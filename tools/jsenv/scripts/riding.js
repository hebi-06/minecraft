import { system } from "@minecraft/server";
import { MOUNT } from "./config.js";
import { notify } from "./fx.js";

/**
 * 탈것에 탄 동안 막기 (v2.8.0). 다른 파일이 부르는 작은 판정만 — 소환 · 사라짐은 mount.js
 * 사용자 요청: "타면서 몬스터를 잡을 수 없고 스킬도 못 사용하게"
 *   공격 (평타 · 활 · 완드 · 스킬 · 룬의 피해 — combat.js 피해 훅), 무기 스킬 (skills.js castSkill), 룬 (runes.js castRune),
 *   활 · 완드 평타 (combat.js castRanged), 근접 참격 연출 (slash.js), 지속 피해로 잡기 (loot.js killCredit)
 * 우리 탈것 (MOUNT.id) 만 — 배 · 말 · 다른 팩의 탈것은 그대로 (바다 보스에서 배를 타고 싸울 수 있게)
 */

/** 이 플레이어가 우리 탈것을 타고 있는지 */
export function isMounted(player) {
  try {
    return player?.getComponent("minecraft:riding")?.entityRidingOn?.typeId === MOUNT.id;
  } catch {
    return false;
  }
}

/** 마지막으로 막힘을 알린 틱 (플레이어마다) @type {Map<string, number>} */
const warnedAt = new Map();

const WHAT = { attack: "공격할 수 없습니다", skill: "스킬 · 룬을 쓸 수 없습니다" };

/**
 * 탈것에 탔으면 막음: 화면 하단 안내 + 낮은 소리 (MOUNT.warnEvery 틱에 한 번), true. 타지 않았으면 false
 * before 이벤트 안 (읽기만) 에서는 부르지 말고 system.run 으로 (combat.js 피해 훅은 isMounted 로 판정만)
 * @param {any} player
 * @param {"attack" | "skill"} [what]
 */
export function blockedByMount(player, what = "skill") {
  if (!isMounted(player)) return false;
  const now = system.currentTick;
  if (now - (warnedAt.get(player.id) ?? -1e9) >= MOUNT.warnEvery) {
    warnedAt.set(player.id, now);
    notify(player, `§c탈것에 탄 동안에는 ${WHAT[what] ?? WHAT.skill} §7(웅크리면 내림)`, 30);
    try {
      player.playSound("note.bass", { pitch: 0.6, volume: 0.6 });
    } catch {
      // 무시
    }
  }
  return true;
}

export function forgetRiding(playerId) {
  warnedAt.delete(playerId);
}
