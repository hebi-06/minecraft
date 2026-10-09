import { CommandPermissionLevel, CustomCommandParamType, CustomCommandStatus, system } from "@minecraft/server";
import { ALL_BOSS_ORDER, ARMOR_GRADE_LEVELS, ARMOR_PIECE_ORDER, ARMOR_TYPE_ORDER, ARMOR_TYPES, BOSSES, ENHANCE, GRADES, LEVELS, MONSTER_DROPS, MONSTER_ORDER, MONSTERS, RAID_MATERIAL_ORDER, RAID_MATERIALS, RUNE, RUNE_FAMILIES, RUNE_FAMILY_ORDER, RUNE_SMITH, SMITH, TYPE_ORDER, gradeLevelsText, isValidWeapon, runeId } from "./config.js";
import { armorFrom, createArmorItem, openStore } from "./armor.js";
import { clearDummies, spawnDummy } from "./dummy.js";
import { clearMonsters, spawnMonsters } from "./monsters.js";
import { addZone, removeNearestZone, zoneListText, zoneMonstersNear } from "./huntzone.js";
import { altarItem, altars, askBuild, nearestAltar, saveAltars } from "./arena.js";
import { encounterAt, endEncounter, summon } from "./bosses.js";
import { materialItem } from "./enhance.js";
import { createRuneItem, createTicketItem, parseRune, quickCastOn, setQuickMode } from "./runes.js";
import { createShardItem, openRunesmith } from "./runesmith.js";
import { dropItem } from "./loot.js";
import { openNpcManager } from "./editor.js";
import { navCommand } from "./nav.js";
import { mainNpcCommand } from "./npc.js";
import { healFull } from "./hp.js";
import { openQuestLog, resetQuests } from "./quest.js";
import { giveItem } from "./shop.js";
import { openStatWindow } from "./statui.js";
import { addPoints, reqLevelOf } from "./stats.js";
import { createOreItem, openSmith } from "./smith.js";
import { createWeaponItem, levelNote, parseWeapon, weaponFrom, writeEnhance } from "./weapons.js";
import { plantTree, setTreeCommandName, undoTree } from "./worldtree.js";
import { pvpCommand } from "./pvp.js";

/**
 * 짧은 명령어. 채팅에서는 앞의 rpg: 를 빼고 쳐도 된다 (예: /npc).
 * 명령 블록·NPC 버튼에서는 rpg:npc 처럼 전체 이름을 쓴다.
 *
 *   /npc                        NPC 관리 창 (운영자)
 *   /mainnpc [번호]             메인 퀘스트 NPC 세우기 (운영자, v2.6.0, 1~10 — v2.7.0 11 왕녀 시그리드 · 12 룬술사 에이빈드 — v2.9.0 13~16 지역 NPC. 번호 없이 치면 목록, 이미 있으면 이 자리로 옮김)
 *   /quest                      내 퀘스트 목록
 *   /nav [on|off]               길 안내 켜기·끄기 (모두, v2.6.1 — 없으면 바꿈). /길안내 = 같은 명령의 한글 이름 (게임이 받으면)
 *   /stats                      스탯 창
 *   /shop 10                    10렙 무기 상점 (운영자, 윗줄 오른쪽 칸 → 갑옷 상점)
 *   /weapon sword 10 rare       무기 받기 (운영자, 영웅은 30·35·40렙, 초월은 40렙)
 *   /ore 10                     별철 광석(레이드 재료) 받기 (운영자, 레이드가 생기기 전 시험용)
 *   /smith                      대장장이 창 (운영자, 윗줄 왼쪽 칸 → 무기 강화)
 *   /enhance                    무기 강화 창 (운영자)
 *   /enhanceset 7               손에 든 무기의 강화 단계를 바로 정함 (운영자, 시험용)
 *   /armor str 40 [transcend]   갑옷 한 벌 받기 (운영자, 종류 str·dex·vit·strvit·int, 일반 0·10·20·30·40렙, 초월 40렙)
 *   /raidmat stone 10           레이드 재료 받기 (운영자, ore 별철 광석·stone 강화석·bright 빛나는 강화석·scroll 수호 주문서)
 *   /dummy [clear]              훈련용 허수아비(샌드백) 세우기 / 주변 훈련용 허수아비 치우기 (운영자)
 *   /mob gulon 3 · /mob clear   몬스터 소환 (눈앞 4칸, 1~10마리) / 주변 48칸 몬스터 치우기 (운영자, v1.7.0)
 *   /mobzone [종류 [수] | clear] 사냥터: 내 발밑에 몬스터를 늘 채워 두는 자리 / 목록 / 가까운 사냥터 지우기 (운영자, v2.6.0)
 *   /boss altar scarecrow_king  보스 제단 블록 받기 (운영자, v1.8.0). /boss list · build · summon · reset · cooldown (가까운 제단)
 *   /rune axe hero              룬 받기 (운영자, 계열 str·dex·vit·int·axe·bow·holy·storm·assassin·blade, 등급 common~transcend)
 *   /runeticket 10 [advanced]   룬 뽑기권 받기 (운영자, 시험용 — advanced = 고급 룬 뽑기권, v2.7.0)
 *   /runeshard 40               룬 조각 받기 (운영자, 시험용, v2.7.0)
 *   /runesmith                  룬술사 창 (운영자, v2.7.0: 룬 갈기 · 뽑기권 사기)
 *   /loot troll 10              몬스터 전리품 받기 (운영자, 시험용, v2.7.0)
 *   /runequick auto|on|off      룬 칸을 고르면 바로 사용 (모두, 기본 auto = 터치일 때만)
 *   /points 5                   스탯 포인트 추가 (운영자)
 *   /heal                       체력 회복 (운영자)
 *   /questreset                 내 퀘스트 기록 초기화 (운영자, 시험용)
 *   /worldtree [plant|undo] [높이]  바라보는 곳에 세계수 심기 / 마지막 세계수 되돌리기 (운영자, v2.5.1, 높이 40~96 기본 64).
 *   /세계수 ... · /segyesu ...      같은 명령의 한글 이름 (게임이 한글 명령 이름을 받으면) · 소리 나는 대로 쓴 영어 이름
 *   /pvp [닉네임|끝|수락|거절]   결투 (모두, v2.26.0): 닉네임 = 신청 (상대에게 수락 · 거절 창) · 끝 = 결투 끝내기 (보낸 신청 취소) · 수락 · 거절 = 받은 신청에 답
 *                               · 없이 치면 결투 창 (접속한 사람 목록). 이름에 빈칸이 있으면 따옴표로 ("Steve Kim"). /결투 = 같은 명령의 한글 이름 (게임이 받으면)
 */

function playerOf(origin) {
  for (const e of [origin.sourceEntity, origin.initiator]) {
    if (e && e.typeId === "minecraft:player") return /** @type {import("@minecraft/server").Player} */ (e);
  }
  return undefined;
}

/** @type {string[]} */
export const REGISTERED = [];

export function registerCommands() {
  system.beforeEvents.startup.subscribe((ev) => {
    const reg = ev.customCommandRegistry;
    // 목록마다 따로 (하나가 실패해도 나머지 명령은 등록되게)
    /** @type {[string, string[]][]} */
    const enums = [
      ["rpg:weapon_type", [...TYPE_ORDER]],
      ["rpg:weapon_grade", GRADES.map((g) => g.id)],
      ["rpg:dummy_action", ["spawn", "clear"]],
      ["rpg:rune_family", [...RUNE_FAMILY_ORDER]],
      ["rpg:armor_type", [...ARMOR_TYPE_ORDER]],
      ["rpg:armor_grade", Object.keys(ARMOR_GRADE_LEVELS)],
      ["rpg:raid_material", [...RAID_MATERIAL_ORDER]],
      ["rpg:quick_mode", ["auto", "on", "off"]],
      ["rpg:mob_kind", [...MONSTER_ORDER, "clear"]],
      ["rpg:boss_action", ["altar", "list", "build", "summon", "reset", "cooldown"]],
      ["rpg:boss_kind", [...ALL_BOSS_ORDER]], // v2.12.0 초월 보스 (수르트) 포함
      ["rpg:tree_action", ["plant", "undo"]],
      ["rpg:nav_mode", ["on", "off"]],
      ["rpg:loot_kind", [...MONSTER_ORDER]],
      ["rpg:ticket_kind", ["normal", "advanced"]],
    ];
    for (const [name, values] of enums) {
      try {
        reg.registerEnum(name, values);
      } catch (e) {
        console.warn(`[rpg] command enum ${name}: ${e}`);
      }
    }

    /**
     * @param {string} name
     * @param {string} description
     * @param {CommandPermissionLevel} permissionLevel
     * @param {(player: import("@minecraft/server").Player, ...args: any[]) => void} run
     * @param {import("@minecraft/server").CustomCommandParameter[]} [mandatoryParameters]
     * @param {import("@minecraft/server").CustomCommandParameter[]} [optionalParameters]
     * @returns {boolean} 등록했으면 true
     */
    const add = (name, description, permissionLevel, run, mandatoryParameters = [], optionalParameters = []) => {
      try {
        reg.registerCommand(
          { name: `rpg:${name}`, description, permissionLevel, cheatsRequired: false, mandatoryParameters, optionalParameters },
          (origin, ...args) => {
            const player = playerOf(origin);
            if (!player) return { status: CustomCommandStatus.Failure, message: "플레이어만 쓸 수 있는 명령어입니다." };
            // 명령어 안에서는 세계를 바꿀 수 없어서 다음 틱에 실행
            system.run(() => {
              if (!player.isValid) return;
              try {
                run(player, ...args);
              } catch (e) {
                player.sendMessage(`§c오류: ${e}`);
              }
            });
            return { status: CustomCommandStatus.Success };
          },
        );
        REGISTERED.push(name);
        return true;
      } catch (e) {
        console.warn(`[rpg] command ${name}: ${e}`);
        return false;
      }
    };

    const ADMIN = CommandPermissionLevel.GameDirectors;
    const ANY = CommandPermissionLevel.Any;
    const T = CustomCommandParamType;

    add("npc", "NPC 관리 창 (NPC 만들기, 이름·대사·퀘스트 설정)", ADMIN, (p) => openNpcManager(p));
    // v2.6.0 메인 퀘스트: 미리 짜 둔 NPC (1 할아버지 … 10 여관 주인). 번호 없이 치면 목록
    add("mainnpc", "메인 퀘스트 NPC 세우기 (1~8 마을 · 13~16 지역 · 9 대장장이 · 10 여관 · 11 왕녀 · 12 룬술사, 번호 없이 치면 목록 · 이미 있으면 이 자리로 옮김)", ADMIN, (p, n) => p.sendMessage(mainNpcCommand(p, n)), [], [{ name: "number", type: T.Integer }]);
    add("quest", "내 퀘스트 목록", ANY, (p) => openQuestLog(p));
    // v2.6.1 길 안내: 받은 퀘스트의 다음 NPC·사냥터·보스 제단을 화면 화살표·바닥 빛·빛기둥으로 (그 플레이어에게만)
    const navDesc = "길 안내 켜기·끄기 (퀘스트를 받으면 다음에 갈 NPC·사냥터·보스 제단을 화살표와 빛으로 안내)";
    const nav = (p, mode) => p.sendMessage(navCommand(p, mode));
    add("nav", navDesc, ANY, nav, [], [{ name: "rpg:nav_mode", type: T.Enum }]);
    add("길안내", navDesc, ANY, nav, [], [{ name: "rpg:nav_mode", type: T.Enum }]);
    add("stats", "스탯 창 열기", ANY, (p) => openStatWindow(p));
    // v2.26.0 결투 (PvP): 영어 이름 (/pvp) 먼저, 그다음 한글 이름 (/결투 — 게임이 받으면)
    const pvpDesc = "결투 (PvP): /pvp 닉네임 = 신청 (상대가 수락하면 둘만 서로 피해, 받는 피해 90% 감소) · /pvp 끝 · /pvp 수락 · /pvp 거절 · /pvp = 결투 창";
    const pvp = (p, name) => pvpCommand(p, name);
    add("pvp", pvpDesc, ANY, pvp, [], [{ name: "player_name", type: T.String }]);
    add("결투", pvpDesc, ANY, pvp, [], [{ name: "player_name", type: T.String }]);
    add("shop", "무기·갑옷 상점 열기 (레벨 0, 5, 10, 15, 20, 30, 40)", ADMIN, (p, lv) => openStore(p, lv ?? LEVELS[0]), [], [
      { name: "level", type: T.Integer },
    ]);
    add("enhance", "무기 강화 창 열기 (+1 ~ +10)", ADMIN, (p) => openSmith(p, "enhance"));
    add(
      "enhanceset",
      "손에 든 무기의 강화 단계를 정함 (시험용, 0~10)",
      ADMIN,
      (p, n) => {
        const inv = p.getComponent("minecraft:inventory")?.container;
        const item = inv?.getItem(p.selectedSlotIndex);
        const w = parseWeapon(item?.typeId);
        if (!inv || !item || !w) {
          p.sendMessage("§cRPG 무기를 손에 들고 쓰세요.");
          return;
        }
        const v = Math.max(0, Math.min(ENHANCE.max, Math.floor(n)));
        inv.setItem(p.selectedSlotIndex, writeEnhance(item, w, v));
        p.sendMessage(`§6[RPG] §f${w.title} 강화 +${v}`);
      },
      [{ name: "level", type: T.Integer }],
    );
    add(
      "armor",
      "갑옷 한 벌 받기 (예: /armor str 40, 초월은 /armor str 40 transcend)",
      ADMIN,
      (p, type, lv, grade) => {
        const g = grade ?? "common";
        const pieces = ARMOR_PIECE_ORDER.map((piece) => armorFrom(type, lv, g, piece));
        if (pieces.some((a) => !a)) {
          p.sendMessage(`§c없는 갑옷입니다. §7(일반 ${ARMOR_GRADE_LEVELS.common.join("·")}렙, 초월 ${ARMOR_GRADE_LEVELS.transcend.join("·")}렙)`);
          return;
        }
        for (const a of pieces) giveItem(p, createArmorItem(a));
        p.sendMessage(`§6[RPG] §f갑옷 지급: ${pieces[0].name.replace(pieces[0].pieceName, "세트")}`);
      },
      [
        { name: "rpg:armor_type", type: T.Enum },
        { name: "level", type: T.Integer },
      ],
      [{ name: "rpg:armor_grade", type: T.Enum }],
    );
    add(
      "raidmat",
      "레이드 재료 받기 (ore 별철 광석 · stone 강화석 · bright 빛나는 강화석 · scroll 수호 주문서)",
      ADMIN,
      (p, kind, n) => {
        const m = RAID_MATERIALS[kind];
        if (!m) return;
        const amount = Math.max(1, Math.min(640, Math.floor(n ?? 1)));
        for (let left = amount; left > 0; left -= 64) giveItem(p, materialItem(kind, Math.min(64, left)));
        p.sendMessage(`§6[RPG] §f${m.name} ${amount}개 지급`);
      },
      [{ name: "rpg:raid_material", type: T.Enum }],
      [{ name: "amount", type: T.Integer }],
    );
    add(
      "weapon",
      "무기 받기 (예: /weapon sword 10 rare)",
      ADMIN,
      (p, type, lv, grade) => {
        const w = isValidWeapon(lv, grade) ? weaponFrom(type, lv, grade) : undefined;
        if (!w) {
          p.sendMessage(`§c${GRADES.find((g) => g.id === grade)?.name ?? grade} 등급은 ${gradeLevelsText(grade)} 무기만 있습니다. §7(일반·희귀·고귀 ${LEVELS.join("·")}렙, 영웅 30·35·40렙, 초월 40렙)`);
          return;
        }
        giveItem(p, createWeaponItem(w));
        p.sendMessage(`§6[RPG] §f${w.name}§r§f 지급${levelNote(w, reqLevelOf(p))}`);
      },
      [
        { name: "rpg:weapon_type", type: T.Enum },
        { name: "level", type: T.Integer },
        { name: "rpg:weapon_grade", type: T.Enum },
      ],
    );
    add("smith", "대장장이 창 열기 (영웅 무기 레벨 제한 올리기)", ADMIN, (p) => openSmith(p));
    add(
      "rune",
      "룬 받기 (예: /rune axe hero)",
      ADMIN,
      (p, family, grade) => {
        const r = parseRune(runeId(family, grade));
        if (!r) {
          p.sendMessage(`§c없는 룬입니다. §7(계열 ${RUNE_FAMILY_ORDER.join("·")}, 등급 common·rare·noble·hero·transcend)`);
          return;
        }
        giveItem(p, createRuneItem(family, grade));
        p.sendMessage(`§6[RPG] §f룬 지급: ${r.gradeColor}${r.def.name}§r §7(${RUNE_FAMILIES[family].name} 룬 · ${r.gradeName})`);
      },
      [
        { name: "rpg:rune_family", type: T.Enum },
        { name: "rpg:weapon_grade", type: T.Enum },
      ],
    );
    add(
      "runeticket",
      `${RUNE.ticketName} 받기 (시험용, advanced = ${RUNE.advTicketName})`,
      ADMIN,
      (p, n, kind) => {
        const adv = kind === "advanced";
        const amount = Math.max(1, Math.min(640, Math.floor(n ?? 1)));
        for (let left = amount; left > 0; left -= 64) giveItem(p, createTicketItem(Math.min(64, left), adv));
        p.sendMessage(`§6[RPG] §f${adv ? RUNE.advTicketName : RUNE.ticketName} ${amount}장 지급`);
      },
      [],
      [
        { name: "amount", type: T.Integer },
        { name: "rpg:ticket_kind", type: T.Enum },
      ],
    );
    // v2.7.0 룬술사: 룬 조각 (시험용) · 창
    add(
      "runeshard",
      `${RUNE_SMITH.shardName} 받기 (시험용)`,
      ADMIN,
      (p, n) => {
        const amount = Math.max(1, Math.min(640, Math.floor(n ?? 1)));
        for (let left = amount; left > 0; left -= 64) giveItem(p, createShardItem(Math.min(64, left)));
        p.sendMessage(`§6[RPG] §f${RUNE_SMITH.shardName} ${amount}개 지급`);
      },
      [],
      [{ name: "amount", type: T.Integer }],
    );
    add("runesmith", "룬술사 창 (룬 갈기 → 룬 조각 → 룬 뽑기권 · 고급 룬 뽑기권)", ADMIN, (p) => openRunesmith(p));
    // v2.7.0 몬스터 전리품 (시험용): 상점에서 팔기 · 메인 심부름 · 반복 의뢰
    add(
      "loot",
      "몬스터 전리품 받기 (시험용, 예: /loot troll 10)",
      ADMIN,
      (p, kind, n) => {
        const d = MONSTER_DROPS[kind];
        if (!d) {
          p.sendMessage(`§c종류: ${MONSTER_ORDER.join(" · ")}`);
          return;
        }
        const amount = Math.max(1, Math.min(640, Math.floor(n ?? 1)));
        for (let left = amount; left > 0; left -= 64) giveItem(p, dropItem(kind, Math.min(64, left)));
        p.sendMessage(`§6[RPG] §f${d.name} ${amount}개 지급`);
      },
      [{ name: "rpg:loot_kind", type: T.Enum }],
      [{ name: "amount", type: T.Integer }],
    );
    add(
      "runequick",
      "룬 칸을 고르면 바로 사용 (auto = 터치일 때만, on = 항상, off = 누를 때만)",
      ANY,
      (p, mode) => {
        const m = mode === "on" || mode === "off" ? mode : "auto";
        setQuickMode(p, m);
        const now = quickCastOn(p) ? "§a켜짐" : "§7꺼짐";
        const how = { auto: "자동 (터치면 켜짐)", on: "항상 켬", off: "끔 (누를 때만)" }[m];
        p.sendMessage(`§6[RPG] §f룬 바로 쓰기: ${how} §8| §f지금 ${now}`);
      },
      [{ name: "rpg:quick_mode", type: T.Enum }],
    );
    add(
      "dummy",
      "훈련용 허수아비(샌드백) 소환 · /dummy clear 로 주변 훈련용 허수아비 치우기",
      ADMIN,
      (p, action) => {
        if (action === "clear") {
          const n = clearDummies(p);
          p.sendMessage(`§6[RPG] §f훈련용 허수아비 ${n}개를 치웠습니다.`);
          return;
        }
        spawnDummy(p);
        p.sendMessage("§6[RPG] §f훈련용 허수아비를 세웠습니다. §7(때리면 머리 위에 피해 합·초당 피해, 5초 동안 안 맞으면 초기화)");
      },
      [],
      [{ name: "rpg:dummy_action", type: T.Enum }],
    );
    add(
      "mob",
      "몬스터 소환 (눈앞 4칸, 1~10마리) · /mob clear 로 주변 몬스터 치우기",
      ADMIN,
      (p, kind, n) => {
        if (kind === "clear") {
          const c = clearMonsters(p);
          const kept = zoneMonstersNear(p);
          p.sendMessage(`§6[RPG] §f몬스터 ${c}마리를 치웠습니다.${kept ? ` §7(사냥터 몬스터 ${kept}마리는 그대로 — /mobzone clear)` : ""}`);
          return;
        }
        const def = MONSTERS[kind];
        if (!def) {
          p.sendMessage(`§c종류: ${MONSTER_ORDER.join(" · ")} · clear`);
          return;
        }
        const list = spawnMonsters(p, kind, n ?? 1);
        p.sendMessage(`§6[RPG] §fLv.${def.level} ${def.name} ${list.length}마리 §7(먼저 때리기 전에는 공격하지 않음)`);
      },
      [{ name: "rpg:mob_kind", type: T.Enum }],
      [{ name: "count", type: T.Integer }],
    );
    // v2.6.0 사냥터: 잡은 몬스터가 다시 나오는 자리 (메인 퀘스트의 처치 단계용)
    add(
      "mobzone",
      "사냥터: /mobzone 종류 수 = 내 발밑에 몬스터를 늘 채워 둠 (잡으면 다시 나옴) · /mobzone = 목록 · /mobzone clear = 가까운 사냥터 지우기",
      ADMIN,
      (p, kind, n) => {
        if (kind === undefined) p.sendMessage(zoneListText());
        else if (kind === "clear") p.sendMessage(removeNearestZone(p));
        else p.sendMessage(addZone(p, kind, n));
      },
      [],
      [
        { name: "rpg:mob_kind", type: T.Enum },
        { name: "count", type: T.Integer },
      ],
    );
    add(
      "boss",
      "보스 (v1.8.0): altar 종류 = 제단 받기, list = 제단 목록, build·summon·reset·cooldown = 가장 가까운 제단의 필드 짓기·바로 소환·초기화·재사용 대기 없애기",
      ADMIN,
      (p, action, kind) => {
        if (action === "altar") {
          const k = kind ?? ALL_BOSS_ORDER[0];
          if (!BOSSES[k]) {
            p.sendMessage(`§c종류: ${ALL_BOSS_ORDER.join(" · ")}`);
            return;
          }
          giveItem(p, altarItem(k));
          p.sendMessage(`§6[보스] §f${BOSSES[k].name} 제단 지급 §7(놓으면 보스 필드를 만들지 묻습니다)`);
          return;
        }
        if (action === "list") {
          const list = altars();
          p.sendMessage(list.length ? list.map((a) => `§6${BOSSES[a.key].name} §7${a.dim.replace("minecraft:", "")} ${a.x} ${a.y} ${a.z} §8· ${a.built ? "§a필드" : "§c필드 없음"}${encounterAt(a) ? " §c전투 중" : ""}`).join("\n") : "§7제단이 없습니다");
          return;
        }
        const a = nearestAltar(p, 64);
        if (!a) {
          p.sendMessage("§c64칸 안에 보스 제단이 없습니다");
          return;
        }
        const name = BOSSES[a.key].name;
        if (action === "build") {
          system.run(() => void askBuild(p, a));
        } else if (action === "summon") {
          if (encounterAt(a)) p.sendMessage("§c이미 전투 중입니다");
          else if (!a.built) p.sendMessage("§c보스 필드가 없습니다 §7— /boss build 로 먼저 지으세요");
          else {
            a.cooldownUntil = 0;
            saveAltars();
            summon(a, p);
          }
        } else if (action === "reset") {
          const enc = encounterAt(a);
          if (enc) endEncounter(enc, "manual");
          p.sendMessage(`§6[보스] §f${name} ${enc ? "초기화" : "전투 없음"}`);
        } else if (action === "cooldown") {
          a.cooldownUntil = 0;
          saveAltars();
          p.sendMessage(`§6[보스] §f${name} 재사용 대기 없앰`);
        }
      },
      [{ name: "rpg:boss_action", type: T.Enum }],
      [{ name: "rpg:boss_kind", type: T.Enum }],
    );
    add(
      "ore",
      `${SMITH.oreName}(대장장이 재료) 받기 (레이드가 생기기 전 시험용)`,
      ADMIN,
      (p, n) => {
        const amount = Math.max(1, Math.min(640, Math.floor(n ?? 1)));
        // 한 묶음은 64개까지라서 나눠서 준다
        for (let left = amount; left > 0; left -= 64) giveItem(p, createOreItem(Math.min(64, left)));
        p.sendMessage(`§6[RPG] §f${SMITH.oreName} ${amount}개 지급`);
      },
      [],
      [{ name: "amount", type: T.Integer }],
    );
    add(
      "points",
      "스탯 포인트 추가",
      ADMIN,
      (p, n) => {
        addPoints(p, n);
        p.sendMessage(`§6[RPG] §f스탯 포인트 ${n >= 0 ? "+" : ""}${n}`);
      },
      [{ name: "amount", type: T.Integer }],
    );
    add("heal", "체력 회복", ADMIN, (p) => {
      healFull(p);
      p.sendMessage("§6[RPG] §f체력을 회복했습니다.");
    });
    add("questreset", "내 퀘스트 기록 초기화 (시험용)", ADMIN, (p) => {
      resetQuests(p);
      p.sendMessage("§6[RPG] §f퀘스트 기록을 초기화했습니다.");
    });
    // v2.5.1 세계수: 영어 이름 (/worldtree) 을 먼저, 그다음 한글 이름 (/세계수) 과 소리 나는 대로 쓴 이름 (/segyesu).
    // 명령 이름은 영어 소문자만 받는다고 알려져 있어 게임이 한글 이름을 받지 않으면 /worldtree·/segyesu 만 남음 (안내는 늘 /worldtree)
    const tree = (p, action, height) => {
      const run = action === "undo" ? undoTree(p) : plantTree(p, height);
      run.catch((e) => console.warn(`[rpg] world tree: ${e}`));
    };
    const treeParams = [
      { name: "rpg:tree_action", type: T.Enum },
      { name: "height", type: T.Integer },
    ];
    const treeDesc = "세계수 심기: 바라보는 곳에 거대한 나무 (plant [높이 40~96]) · undo = 마지막 세계수 되돌리기";
    add("worldtree", treeDesc, ADMIN, tree, [], treeParams);
    const korean = add("세계수", treeDesc, ADMIN, tree, [], treeParams);
    add("segyesu", treeDesc, ADMIN, tree, [], treeParams);
    setTreeCommandName("/worldtree", korean ? "/세계수" : undefined);
  });
}
