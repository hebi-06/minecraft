import { system, world } from "@minecraft/server";
import { ULT_FX } from "./config.js";

/**
 * v2.27.0 초월 룬 2차 강화: 하늘빛 (/fog) · 화면 번쩍임 (카메라 페이드). 그림 (영역 문양 · 빛기둥 · 금 · 고리 · 섬광) 은 runes.js.
 * 사용자 요청 "초월 룬 ... 이펙트가 부족한 것을 채우고" · 고른 것 "초월 룬 2차 강화" (하늘 · 땅 전체 연출, 단계 추가).
 *
 * 하늘빛: 사람마다 하나 (새 하늘빛이 앞 것을 바꿈 — 이름표 ULT_FX.fogTag 로 모두 걷고 씌움), 정해진 틱이 지나면 걷음.
 *   보스 안개 (rpg_boss_fog) 와 이름표가 달라 서로 걷지 않음 (/fog 는 쌓이고, 위의 것이 보임).
 *   /fog 는 사람에게 저장되므로 들어올 때 · 스크립트를 다시 불러올 때 남은 하늘빛을 걷음 (나갔다 들어와도 하늘이 물든 채로 남지 않게).
 * 이 모듈은 다른 게임 모듈을 가져오지 않는다 (runes.js 가 가져옴).
 */

/** 플레이어 id → 지금 씌운 하늘빛 {id, until, token} @type {Map<string, {id: string, until: number, token: number}>} */
const skies = new Map();
let nextToken = 1;
/** 하늘빛을 씌우지 않을 사람 (bosses.js 가 넣음: 보스 안개가 씌워진 사람 — 보스 안개는 위험을 알리는 장치라 가리지 않게) */
let skyBlocked = (_p) => false;

/** bosses.js: 보스 안개가 씌워진 사람인지 (setSky 가 그 사람은 건너뜀) */
export function setSkyBlocker(fn) {
  skyBlocked = fn;
}

/** at 둘레 radius 칸의 플레이어 */
function audience(dim, at, radius) {
  try {
    return dim.getPlayers({ location: at, maxDistance: radius });
  } catch {
    return [];
  }
}

/** 이 사람의 초월 하늘빛을 모두 걷음 (명령 실패 = 이미 없음 · 나간 사람) */
function removeFog(p) {
  try {
    p.runCommand(`fog @s remove ${ULT_FX.fogTag}`);
  } catch {
    // 무시
  }
}

/**
 * 한 사람에게 하늘빛 fogId 를 ticks 틱 동안 (앞 하늘빛은 걷고 바꿈). 씌웠으면 true
 * @param {import("@minecraft/server").Player} p
 * @param {string} fogId
 * @param {number} ticks
 */
export function setSky(p, fogId, ticks) {
  if (!ULT_FX.fog || !fogId || !(ticks > 0)) return false;
  try {
    if (skyBlocked(p)) return false; // 보스 안개 위에 쌓으면 보스 안개가 가려짐 (보스는 이미 씌웠다고 기억해 다시 씌우지 않음)
  } catch {
    return false;
  }
  if (skies.has(p.id)) removeFog(p);
  try {
    p.runCommand(`fog @s push ${fogId} ${ULT_FX.fogTag}`);
  } catch {
    skies.delete(p.id);
    return false;
  }
  const token = nextToken++;
  skies.set(p.id, { id: fogId, until: system.currentTick + ticks, token });
  system.runTimeout(() => {
    const s = skies.get(p.id);
    if (!s || s.token !== token) return; // 그 사이 다른 하늘빛으로 바뀜 (그쪽이 걷음)
    skies.delete(p.id);
    if (p.isValid) removeFog(p);
  }, ticks);
  return true;
}

/**
 * 하늘빛: at 둘레 ULT_FX.audience 칸의 사람 모두에게 ticks 틱 동안. 씌운 사람 수
 * @param {any} dim
 * @param {{x: number, y: number, z: number}} at
 * @param {string | undefined} fogId
 * @param {number} ticks
 */
export function ultSky(dim, at, fogId, ticks) {
  if (!ULT_FX.fog || !fogId) return 0;
  let n = 0;
  for (const p of audience(dim, at, ULT_FX.audience)) if (setSky(p, fogId, ticks)) n++;
  return n;
}

/**
 * 하늘빛을 일찍 걷음 (룬을 쓴 사람이 죽음 · 버프가 끝남): 이 사람들 중 하늘빛이 fogId 인 사람만 (다른 룬의 하늘빛은 그대로)
 * @param {any[]} players
 * @param {string} fogId
 */
export function endSky(players, fogId) {
  for (const p of players) {
    const s = skies.get(p?.id);
    if (!s || s.id !== fogId) continue;
    skies.delete(p.id);
    if (p.isValid) removeFog(p);
  }
}

/** 이 사람에게 지금 씌운 초월 하늘빛 (없으면 undefined) — 시험 · 상태 확인용 */
export function skyOf(player) {
  const s = skies.get(player?.id);
  return s && s.until > system.currentTick ? s.id : undefined;
}

/**
 * 화면 번쩍임: at 둘레 o.radius (기본 ULT_FX.flashRadius) 칸의 사람 + o.caster (룬을 쓴 사람 — 멀리 겨눠도) 의 화면이 color 로 짧게 번쩍임 (카메라 페이드). 번쩍인 사람 수
 * @param {any} dim
 * @param {{x: number, y: number, z: number}} at
 * @param {number[] | undefined} color [빨강, 초록, 파랑] 0~1
 * @param {{radius?: number, caster?: any, time?: {fadeInTime?: number, holdTime?: number, fadeOutTime?: number}}} [o]
 */
export function ultFlash(dim, at, color, o = {}) {
  if (!ULT_FX.flash || !color) return 0;
  const fadeTime = { ...ULT_FX.flashTime, ...(o.time ?? {}) };
  const list = audience(dim, at, o.radius ?? ULT_FX.flashRadius);
  try {
    if (o.caster?.isValid && !list.some((p) => p.id === o.caster.id)) list.push(o.caster);
  } catch {
    // 나가는 중
  }
  let n = 0;
  for (const p of list) {
    try {
      p.camera.fade({ fadeColor: { red: color[0], green: color[1], blue: color[2] }, fadeTime });
      n++;
    } catch {
      // 카메라를 쓸 수 없는 상태 (나가는 중 등)
    }
  }
  return n;
}

/** main.js: 들어올 때 남은 하늘빛 걷기 · 나가면 잊기 · 스크립트를 다시 불러오면 모두 걷기 */
export function registerUltFx() {
  world.afterEvents.playerSpawn.subscribe(({ player, initialSpawn }) => {
    if (!initialSpawn) return;
    skies.delete(player.id);
    removeFog(player);
  });
  world.afterEvents.playerLeave.subscribe(({ playerId }) => {
    skies.delete(playerId);
  });
  system.run(() => {
    let list = [];
    try {
      list = world.getAllPlayers();
    } catch {
      list = [];
    }
    for (const p of list) removeFog(p);
  });
}
