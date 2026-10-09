import { MolangVariableMap, system, world } from "@minecraft/server";
import { ActionFormData, FormCancelationReason, uiManager } from "@minecraft/server-ui";
import { PVP } from "./config.js";
import { wait } from "./chestui.js";
import { anyDuel, attackerOf, clearDuel, duelOf, duelists, partnerHitAt, setDuel } from "./duel.js";
import { DIALOG_MARKER, ICON, MenuForm } from "./forms.js";
import { play } from "./fx.js";
import { hpState, isRpgMode, setDuelHooks, showRealTitle } from "./hp.js";
import { burst, rune as floorRune, shock, sparks } from "./skills.js";

/**
 * v2.26.0 결투 (PvP). 사용자 요청: "/pvp [플레이어 닉네임] 치면 pvp 수락 또는 거절 버튼이 나오고 수락버튼을 누르면 pvp가 가능하게 하고
 * pvp를 할때 데미지를 감소시켜서 받게 해" — 고른 것: 피해 90% 감소 · 평타 + 스킬 + 룬 · 쓰러지거나 /pvp 끝 (나가도 끝).
 *
 * - /pvp 닉네임: 신청 → 상대 화면 아래 대화창에 수락 · 거절 (PVP.requestTicks 안에 답이 없으면 취소 · 창도 닫음). /pvp 수락 · /pvp 거절 로도 답함
 *   (창을 닫으면 신청은 남음). 서로 신청하면 바로 수락. 받는 사람이 싸우는 중 (방금 다침) 이면 창 없이 글로만.
 *   /pvp 만 치면 결투 창 (접속한 사람 목록 · 받은 신청 · 결투 끝내기 · 신청 받지 않기). /pvp 차단 = 신청 받지 않기 (다시 치면 받기)
 * - 수락하면 3 · 2 · 1 (PVP.countdownTicks) 뒤 시작: 둘만 서로 평타 · 활 · 완드 · 스킬 · 룬 (반사 · 반격 포함) 피해 × PVP.mult
 *   (combat.js · skills.js · runes.js 가 duel.js 를 봄 — 길들인 동물의 피해는 주인의 것). 결투 상대가 아닌 플레이어 사이의 피해는 막음 (PVP.protectOthers).
 *   회복 · 보호막 · 방벽은 결투 중인 사람에게 주지 않음 (결투 상대도 아니고, 남의 결투도 밖에서 돕지 못함)
 * - 끝: 쓰러짐 (PVP.lethal 이 아니면 결투 상대 (펫 포함) 의 피해, 또는 결투 상대가 PVP.blameTicks 안에 때린 뒤의 불 · 용암 · 떨어짐 · 위더로는
 *   죽지 않고 체력 1로 남고 짐 — hp.js 의 결투 훅. 진 뒤 PVP.graceTicks 동안은 남은 불 · 용암 · 떨어짐 · 위더로 쓰러지지 않음) ·
 *   몹 · 보스 (주인 없는 보스 기술 포함) · /kill 에 죽음 (결투가 목숨을 더 주지 않음) · /pvp 끝 · 나감 · 생존 · 모험 모드가 아니게 됨
 * - 세계의 PvP 설정 (게임 규칙 pvp) 이 꺼져 있으면 결투 동안만 켜고, 결투가 모두 끝나면 (또는 다시 불러오면) 되돌림
 * - 연출: 3 · 2 · 1 제목 · 발밑에 그려지는 붉은 마법진 · 머리 위 결투 표식 (교차한 두 검 rpg:rfx_duel) → 시작 충격파,
 *   결투 동안 두 사람 머리 위 표식 (PVP.markEvery 틱마다), 끝나면 승리 (금빛) · 패배 · 끝 제목. 상태 줄에 상대 이름 · 남은 체력 (duel.js duelHud)
 */

const END_WORDS = new Set(["끝", "end", "stop", "그만", "끝내기"]);
const ACCEPT_WORDS = new Set(["수락", "accept", "yes"]);
const DECLINE_WORDS = new Set(["거절", "decline", "no"]);
const BLOCK_WORDS = new Set(["차단", "block"]);
/** 우리가 결투 때문에 세계의 PvP 를 켰는지 (월드에 저장 — 결투 중에 세계를 닫아도 다음에 되돌림) */
const RULE_KEY = "rpg:pvp_rule_by_duel";
/** 결투 신청을 받지 않음 (플레이어에 저장) */
const BLOCK_KEY = "rpg:pvp_block";
const UP = { x: 0, y: 1, z: 0 };
const ZERO = { x: 0, y: 0, z: 0 };
/** 창이 이만큼 (틱 — 0.5초) 기다려도 답이 없으면 우리 창이 열려 있는 것 (다른 창 때문에 바쁘면 곧 돌아옴) — 만료 · 취소 때 이때만 닫음 (남의 창을 닫지 않게) */
const OPEN_AFTER = 10;
/** 결투 상대 탓이 될 수 있는 주인 없는 피해 (결투 상대가 PVP.blameTicks 안에 때린 뒤 — 불 · 넉백 뒤 용암 · 떨어짐 · 위더). 보스의 주인 없는 기술 (떨어지는 블록 · 마법 · 투사체) · /kill 은 아님 */
const BLAME_CAUSES = new Set(["fire", "fireTick", "lava", "fall", "wither"]);

/**
 * 받은 신청: 받는 사람 id → 신청. 한 사람은 한 번에 하나만 받음
 * @typedef {{id: number, from: string, fromName: string, to: string, toName: string, at: number, until: number, shown: boolean, shownAt: number}} DuelRequest
 * @type {Map<string, DuelRequest>}
 */
const requests = new Map();
/** 보낸 신청: 보낸 사람 id → 받는 사람 id @type {Map<string, string>} */
const outgoing = new Map();
/** 거절 · 만료 · 취소된 틱: "보낸 사람|받는 사람" → 틱 (PVP.retryTicks 동안 그 사람에게 다시 신청하지 못함) @type {Map<string, number>} */
const refused = new Map();
/** 마지막으로 신청을 보낸 틱 (PVP.askGapTicks — 누구에게든) @type {Map<string, number>} */
const lastAsk = new Map();
/** 결투에서 진 사람 → 이 틱까지는 남은 불 · 용암 · 떨어짐 · 위더로 쓰러지지 않음 (PVP.graceTicks — 몹 · 보스에게는 그대로) @type {Map<string, number>} */
const graceUntil = new Map();
/** 결투 상대에게 (또는 그 탓으로) 쓰러진 사람 → 틱 (다음 틱 duelLost 가 결투를 끝냄 — 그 사이 때린 것이 사라져도) @type {Map<string, number>} */
const lostAt = new Map();
/** 진 사람에게 남은 해로운 효과 (끝난 결투의 불 · 독이 체력 1을 깎아 죽이지 않게) */
const HARMFUL = ["poison", "fatal_poison", "wither"];
let nextRequest = 1;

// ------------------------------------------------------------ 찾기 · 확인
function playerById(id) {
  try {
    const e = world.getEntity(id);
    return e?.isValid && e.typeId === "minecraft:player" ? /** @type {import("@minecraft/server").Player} */ (e) : undefined;
  } catch {
    return undefined;
  }
}

function onlinePlayers() {
  try {
    return world.getAllPlayers().filter((p) => p.isValid);
  } catch {
    return [];
  }
}

/**
 * 이름으로 접속한 플레이어 찾기: 똑같은 이름 (대소문자 무시) → 그 이름으로 시작하는 사람이 하나뿐이면 그 사람
 * @returns {{player?: any, error?: string}}
 */
export function findPlayer(name, self) {
  const want = String(name ?? "").trim();
  if (want.startsWith("@")) return { error: "§c선택자 (@p · @a 등) 는 쓸 수 없습니다. 이름을 쓰거나 /pvp 만 쳐서 목록에서 고르세요." };
  const low = want.toLowerCase();
  const list = onlinePlayers();
  const exact = list.filter((p) => p.name.toLowerCase() === low);
  if (exact.length === 1) return { player: exact[0] };
  const pre = list.filter((p) => p.name.toLowerCase().startsWith(low));
  if (low && pre.length === 1) return { player: pre[0] };
  const others = list.filter((p) => p.id !== self?.id).map((p) => p.name);
  if (low && pre.length > 1) return { error: `§c'${want}' 로 시작하는 사람이 여럿입니다: §f${pre.map((p) => p.name).join(", ")}` };
  return { error: `§c'${want}' 님을 찾을 수 없습니다.${others.length ? ` §7접속한 사람: §f${others.slice(0, 12).join(", ")}` : " §7다른 접속자가 없습니다."}` };
}

function live(req) {
  return !!req && requests.get(req.to) === req && system.currentTick < req.until;
}

function say(player, text) {
  try {
    if (player?.isValid) player.sendMessage(text);
  } catch {
    // 무시
  }
}

function sound(player, id, pitch = 1, volume = 1) {
  try {
    if (player?.isValid) player.playSound(id, { pitch, volume });
  } catch {
    // 무시
  }
}

/** 결투 신청을 받지 않는지 (/pvp 차단) */
export function blocksDuels(player) {
  try {
    return player.getDynamicProperty(BLOCK_KEY) === true;
  } catch {
    return false;
  }
}

function setBlock(player, on) {
  try {
    player.setDynamicProperty(BLOCK_KEY, on ? true : undefined);
  } catch {
    // 무시
  }
  say(player, on ? "§7이제 결투 신청을 받지 않습니다. §f(다시 받기: /pvp 차단)" : "§7이제 결투 신청을 받습니다.");
}

// ------------------------------------------------------------ 연출
/** 결투 표식 (교차한 두 검): 빛 (붉은색) + 심지 (흰 금빛) 두 장. vel = 몸을 따라오는 속도 (칸/초) */
function duelMark(dim, at, o) {
  const vel = o.vel ?? ZERO;
  const sp = Math.hypot(vel.x, vel.y, vel.z);
  for (const [cell, col, alpha] of [
    [0, o.color ?? PVP.color, o.alpha ?? 1],
    [1, PVP.core, o.alpha ?? 1],
  ]) {
    try {
      const m = new MolangVariableMap();
      m.setVector3("variable.dir", UP);
      m.setVector3("variable.move", sp > 1e-6 ? { x: vel.x / sp, y: vel.y / sp, z: vel.z / sp } : UP);
      m.setVector3("variable.vel", vel);
      m.setFloat("variable.speed", sp);
      m.setFloat("variable.size", o.size);
      m.setFloat("variable.life", o.life);
      m.setFloat("variable.spin", o.spin ?? 0);
      m.setFloat("variable.spin_rate", o.spinRate ?? 0);
      m.setFloat("variable.grow", o.grow ?? 0);
      m.setFloat("variable.cell", cell);
      m.setFloat("variable.frames", 1);
      m.setFloat("variable.fps", 0);
      m.setFloat("variable.loop", 0);
      m.setFloat("variable.phase", 0);
      m.setFloat("variable.fade", o.fade ?? 0);
      m.setColorRGBA("variable.color", { red: col[0], green: col[1], blue: col[2], alpha });
      dim.spawnParticle("rpg:rfx_duel", at, m);
    } catch {
      // 로드되지 않은 곳 등
    }
  }
}

/** 머리 위 (결투 표식 자리) */
function overHead(p, h = 0.75) {
  const e = p.getHeadLocation();
  return { x: e.x, y: e.y + h, z: e.z };
}

function bodyVel(p) {
  try {
    const v = p.getVelocity();
    return { x: v.x * 20, y: 0, z: v.z * 20 };
  } catch {
    return ZERO;
  }
}

/** 3 · 2 · 1 한 번 (left = 남은 초): 제목 · 종소리 · 발밑에 그려지는 붉은 마법진 · 머리 위 표식 */
function countdownCue(p, left, other) {
  try {
    showRealTitle(p, `§c§l${left}`, `§f${other} §7님과 결투`, { fadeIn: 0, stay: 16, fadeOut: 4 });
  } catch {
    // 무시
  }
  sound(p, "block.bell.hit", 0.8 + (3 - left) * 0.15, 0.8);
  const dim = p.dimension;
  const total = Math.max(1, Math.round(PVP.countdownTicks / 20));
  floorRune(dim, p.location, { size: 1.5, life: 1.05, color: PVP.color, alpha: 0.85, progress: Math.min(1, (total - left + 1) / total), rate: 30 });
  duelMark(dim, overHead(p, 0.9), { size: 0.42, life: 1.0, fade: 0.25, grow: 0.15, vel: bodyVel(p) });
}

/** 결투 시작: 제목 (짧게 — 체력 바가 오래 멈추지 않게) · 충격파 · 큰 표식 */
function startCue(p, other) {
  try {
    showRealTitle(p, "§c§l결투 시작!", `§f상대: ${other} §7· 받는 피해 ${Math.round((1 - PVP.mult) * 100)}% 감소`, { fadeIn: 0, stay: 12, fadeOut: 4 });
  } catch {
    // 무시
  }
  const dim = p.dimension;
  const c = p.location;
  shock(dim, c, PVP.color, 1.6, 0.5);
  burst(dim, { x: c.x, y: c.y + 1.1, z: c.z }, PVP.color, 1.1, 0.3);
  sparks(dim, { x: c.x, y: c.y + 1.1, z: c.z }, PVP.core, 8, 4);
  duelMark(dim, overHead(p, 0.9), { size: 0.55, life: 0.7, grow: 0.6, fade: 0.6, vel: bodyVel(p) });
  play(dim, "mob.evocation_illager.prepare_attack", c, 1.0, 0.9);
  play(dim, "block.bell.hit", c, 1.5, 1.0);
}

/** 끝 연출: kind = win (금빛) · lose (잿빛) · end (조용히) */
function endCue(p, kind, title, subtitle) {
  if (!p?.isValid) return;
  try {
    showRealTitle(p, title, subtitle, { fadeIn: 4, stay: 30, fadeOut: 10 });
  } catch {
    // 무시
  }
  const dim = p.dimension;
  const c = p.location;
  const chest = { x: c.x, y: c.y + 1.1, z: c.z };
  if (kind === "win") {
    burst(dim, chest, PVP.win, 1.3, 0.35);
    sparks(dim, chest, PVP.win, 12, 5);
    shock(dim, c, PVP.win, 1.8, 0.55);
    duelMark(dim, overHead(p, 0.9), { size: 0.5, life: 0.8, grow: 0.5, fade: 0.6, color: PVP.win, vel: bodyVel(p) });
    play(dim, "random.levelup", c, 1.0, 1.0);
    play(dim, "firework.twinkle", c, 1.0, 0.8);
  } else if (kind === "lose") {
    burst(dim, chest, [0.45, 0.45, 0.5], 1.0, 0.4);
    sparks(dim, chest, [0.6, 0.6, 0.65], 6, 2);
    play(dim, "random.anvil_land", c, 0.6, 0.7);
  } else {
    sound(p, "beacon.deactivate", 1.2, 0.7);
  }
}

// ------------------------------------------------------------ 세계의 PvP 설정
/** 결투 동안 세계의 PvP 가 꺼져 있으면 켬 (결투가 아닌 사람 사이의 피해는 combat.js 가 막으므로 꺼진 것과 같음) */
function ensurePvpRule() {
  try {
    if (world.gameRules.pvp) return;
    world.setDynamicProperty(RULE_KEY, true);
    world.gameRules.pvp = true;
  } catch (e) {
    console.warn(`[rpg] pvp rule: ${e}`);
  }
}

/** 결투가 모두 끝났고 우리가 켠 것이면 되돌림 (다시 불러온 뒤에도 — 결투는 저장하지 않음) */
export function restorePvpRule() {
  try {
    if (anyDuel() || world.getDynamicProperty(RULE_KEY) !== true) return;
    world.setDynamicProperty(RULE_KEY, undefined);
    world.gameRules.pvp = false;
  } catch (e) {
    console.warn(`[rpg] pvp rule restore: ${e}`);
  }
}

// ------------------------------------------------------------ 신청 · 답
function dropRequest(req) {
  if (requests.get(req.to) === req) requests.delete(req.to);
  if (outgoing.get(req.from) === req.to) outgoing.delete(req.from);
}

/** 이 사람이 보냈거나 받은 신청을 모두 취소 (결투를 시작하거나 나갈 때). why = 상대에게 보일 까닭 */
function cancelRequestsOf(id, why) {
  for (const req of [...requests.values()]) {
    if (req.from !== id && req.to !== id) continue;
    dropRequest(req);
    closeIfShown(req);
    const other = req.from === id ? playerById(req.to) : playerById(req.from);
    if (other && why) say(other, why(req));
  }
}

/** 우리 신청 창이 열려 있으면 닫음 (창을 띄운 지 OPEN_AFTER 틱이 지나도 답이 없을 때만 — 다른 창 때문에 기다리는 중이면 그 창을 닫지 않게) */
function closeIfShown(req) {
  if (!req.shown) return;
  const open = system.currentTick - req.shownAt >= OPEN_AFTER;
  req.shown = false;
  if (!open) return;
  const t = playerById(req.to);
  try {
    if (t) uiManager.closeAllForms(t);
  } catch {
    // 무시
  }
}

/**
 * 결투 신청 창 (받는 사람 화면 아래 대화창). 수락 = true, 거절 = false, 창을 닫음 = "closed" (신청은 남음), 신청이 끝났으면 undefined
 * @returns {Promise<boolean | "closed" | undefined>}
 */
async function ask(target, req) {
  const pct = Math.round((1 - PVP.mult) * 100);
  const body =
    `§f결투 (PvP) 를 신청합니다!\n` +
    `§7· 서로에게만 평타 · 스킬 · 룬 피해 §f(받는 피해 ${pct}% 감소)\n` +
    `§7· ${PVP.lethal ? "쓰러지면 (죽음)" : "쓰러지면 (체력 1로 남음)"} · /pvp 끝 · 나가면 끝\n` +
    `§7· ${Math.round(PVP.requestTicks / 20)}초 안에 답하세요`;
  for (let tries = 0; ; tries++) {
    if (!live(req) || !target.isValid) return undefined;
    const form = new ActionFormData().title(`${DIALOG_MARKER}§c⚔ §f${req.fromName}`).body(body).button("§2⚔ 결투 수락", ICON.OK).button("§4거절", ICON.BYE);
    req.shown = true;
    req.shownAt = system.currentTick;
    let res;
    try {
      res = await form.show(target);
    } catch {
      req.shown = false;
      return undefined;
    }
    req.shown = false;
    if (res.canceled && res.cancelationReason === FormCancelationReason.UserBusy) {
      if (tries === 2) {
        try {
          target.onScreenDisplay.setActionBar(`§e⚔ ${req.fromName} 님의 결투 신청 §7— 채팅창을 닫으면 창이 열립니다 (/pvp 수락 · /pvp 거절)`);
        } catch {
          // 무시
        }
      }
      await wait(5);
      continue;
    }
    if (!live(req)) {
      if (!res.canceled) say(target, "§7이미 끝난 결투 신청입니다.");
      return undefined;
    }
    if (res.canceled) return "closed";
    return res.selection === 0;
  }
}

/** 신청에 답함 (창 · /pvp 수락 · /pvp 거절 · 결투 창) */
function answer(req, yes) {
  if (!live(req)) return;
  dropRequest(req);
  closeIfShown(req);
  const from = playerById(req.from);
  const to = playerById(req.to);
  if (!to) return;
  if (!from) {
    say(to, `§7${req.fromName} 님이 나가서 결투 신청이 취소되었습니다.`);
    return;
  }
  if (!yes) {
    refused.set(`${req.from}|${req.to}`, system.currentTick);
    say(from, `§c⚔ ${req.toName} 님이 결투를 거절했습니다.`);
    say(to, `§7${req.fromName} 님의 결투 신청을 거절했습니다.`);
    sound(from, "note.bass", 0.8, 0.8);
    return;
  }
  const busy = [from, to].find((p) => duelOf(p.id));
  if (busy) {
    const msg = `§c${busy.name} 님이 이미 다른 사람과 결투 중이라 시작하지 못했습니다.`;
    say(from, msg);
    say(to, msg);
    return;
  }
  const notRpg = [from, to].find((p) => !isRpgMode(p));
  if (notRpg) {
    const msg = `§c${notRpg.name} 님이 생존 · 모험 모드가 아니라 결투를 시작하지 못했습니다.`;
    say(from, msg);
    say(to, msg);
    return;
  }
  startDuel(from, to);
}

/** /pvp 닉네임 */
function requestDuel(player, name) {
  const { player: target, error } = findPlayer(name, player);
  if (!target) {
    say(player, error ?? "§c찾을 수 없습니다.");
    return;
  }
  if (target.id === player.id) {
    say(player, "§c자기 자신에게는 결투를 신청할 수 없습니다.");
    return;
  }
  const mine = duelOf(player.id);
  if (mine) {
    say(player, `§c이미 ${mine.partnerName} 님과 결투 중입니다. §7(끝내기: /pvp 끝)`);
    return;
  }
  if (duelOf(target.id)) {
    say(player, `§c${target.name} 님은 다른 사람과 결투 중입니다.`);
    return;
  }
  if (!isRpgMode(player)) {
    say(player, "§c생존 · 모험 모드에서만 결투할 수 있습니다.");
    return;
  }
  if (!isRpgMode(target)) {
    say(player, `§c${target.name} 님은 생존 · 모험 모드가 아니라 결투할 수 없습니다.`);
    return;
  }
  // 상대가 먼저 나에게 신청했으면 바로 수락
  const theirs = requests.get(player.id);
  if (live(theirs) && theirs.from === target.id) {
    answer(theirs, true);
    return;
  }
  if (blocksDuels(target)) {
    say(player, `§7${target.name} 님은 결투 신청을 받지 않습니다.`);
    return;
  }
  const now = system.currentTick;
  const pending = requests.get(target.id);
  if (live(pending)) {
    if (pending.from === player.id) say(player, `§7이미 ${target.name} 님에게 신청했습니다. §f(${Math.ceil((pending.until - now) / 20)}초 남음)`);
    else say(player, `§c${target.name} 님이 다른 결투 신청에 답하는 중입니다. 잠시 뒤에 다시 신청하세요.`);
    return;
  }
  const back = refused.get(`${player.id}|${target.id}`);
  if (back !== undefined && now - back < PVP.retryTicks) {
    say(player, `§7${target.name} 님에게는 ${Math.ceil((PVP.retryTicks - (now - back)) / 20)}초 뒤에 다시 신청할 수 있습니다.`);
    return;
  }
  const last = lastAsk.get(player.id);
  if (last !== undefined && now - last < PVP.askGapTicks) {
    say(player, `§7${Math.ceil((PVP.askGapTicks - (now - last)) / 20)}초 뒤에 다시 신청할 수 있습니다.`);
    return;
  }
  // 다른 사람에게 보낸 신청은 취소 (한 번에 하나 — 그 사람에게는 retryTicks 동안 다시 보내지 못함)
  const prevTo = outgoing.get(player.id);
  const prev = prevTo ? requests.get(prevTo) : undefined;
  if (prev && prev.from === player.id) {
    dropRequest(prev);
    closeIfShown(prev);
    refused.set(`${prev.from}|${prev.to}`, now);
    say(playerById(prev.to), `§7${player.name} 님이 결투 신청을 취소했습니다.`);
  }
  /** @type {DuelRequest} */
  const req = { id: nextRequest++, from: player.id, fromName: player.name, to: target.id, toName: target.name, at: now, until: now + PVP.requestTicks, shown: false, shownAt: -1000 };
  requests.set(target.id, req);
  outgoing.set(player.id, target.id);
  lastAsk.set(player.id, now);
  const secs = Math.round(PVP.requestTicks / 20);
  say(player, `§e⚔ ${target.name} 님에게 결투를 신청했습니다. §7(${secs}초 안에 답이 없으면 취소)`);
  sound(target, "block.bell.hit", 0.8, 1.0);
  // 받는 사람이 싸우는 중 (방금 다침) 이면 창을 띄우지 않음 (모바일에서 창이 열리면 움직이지 못함) — 글로만
  let fighting = false;
  try {
    fighting = now - hpState(target).lastHurt < PVP.quietIfHurtTicks;
  } catch {
    fighting = false;
  }
  if (fighting) {
    say(target, `§e⚔ ${player.name} 님이 결투를 신청했습니다. §7싸우는 중이라 창을 띄우지 않았습니다 — §f/pvp 수락 · /pvp 거절 §7(또는 /pvp 로 창, ${secs}초 안)`);
    try {
      target.onScreenDisplay.setActionBar(`§e⚔ ${player.name} 님의 결투 신청 §7— /pvp 수락 · /pvp 거절`);
    } catch {
      // 무시
    }
    return;
  }
  say(target, `§e⚔ ${player.name} 님이 결투를 신청했습니다. §7(창에서 수락 · 거절, 또는 /pvp 수락 · /pvp 거절)`);
  ask(target, req).then(
    (res) => {
      if (res === true || res === false) answer(req, res);
      else if (res === "closed" && live(req)) say(target, `§7창을 닫았습니다. §f/pvp 수락 · /pvp 거절 §7로 답할 수 있습니다 (${Math.ceil((req.until - system.currentTick) / 20)}초 안).`);
    },
    () => {},
  );
}

/** 받은 신청에 글로 답함 (/pvp 수락 · /pvp 거절) */
function answerTyped(player, yes) {
  const req = requests.get(player.id);
  if (!live(req)) {
    say(player, "§7받은 결투 신청이 없습니다.");
    return;
  }
  answer(req, yes);
}

// ------------------------------------------------------------ 결투
function startDuel(a, b) {
  cancelRequestsOf(a.id, (r) => `§7${r.from === a.id ? r.fromName : r.toName} 님이 다른 결투를 시작해 신청이 취소되었습니다.`);
  cancelRequestsOf(b.id, (r) => `§7${r.from === b.id ? r.fromName : r.toName} 님이 다른 결투를 시작해 신청이 취소되었습니다.`);
  ensurePvpRule();
  graceUntil.delete(a.id);
  graceUntil.delete(b.id);
  const start = system.currentTick + PVP.countdownTicks;
  const token = setDuel(a, b, start);
  const pct = Math.round((1 - PVP.mult) * 100);
  for (const [p, o] of [
    [a, b],
    [b, a],
  ]) {
    say(p, `§6⚔ ${o.name} 님과 결투! §7${Math.round(PVP.countdownTicks / 20)}초 뒤 시작 · 받는 피해 ${pct}% 감소 · ${PVP.lethal ? "쓰러지면 짐 (죽음)" : "쓰러지면 짐 (체력 1로 남음)"} · 끝내기 /pvp 끝`);
  }
  const same = () => duelOf(a.id)?.token === token;
  const secs = Math.max(1, Math.round(PVP.countdownTicks / 20));
  for (let i = 0; i < secs; i++) {
    const cue = () => {
      if (!same()) return;
      for (const [p, o] of [
        [a, b],
        [b, a],
      ])
        if (p.isValid) countdownCue(p, secs - i, o.name);
    };
    if (i === 0) cue();
    else system.runTimeout(cue, Math.round((i * PVP.countdownTicks) / secs));
  }
  system.runTimeout(() => {
    if (!same()) return;
    for (const [p, o] of [
      [a, b],
      [b, a],
    ])
      if (p.isValid) startCue(p, o.name);
  }, Math.max(1, PVP.countdownTicks));
}

/**
 * 결투 끝 (둘 다). reason: defeat (id 가 쓰러져 짐 — 상대가 이김) · fell (몹 · 보스 등에 죽음) · command (/pvp 끝) · left (나감) · mode (생존 · 모험이 아니게 됨)
 * @param {string} id 끝나게 한 사람 (진 사람 · 죽은 사람 · /pvp 끝을 친 사람 · 나간 사람)
 */
function endDuel(id, reason, name = undefined) {
  const d = clearDuel(id);
  if (!d) return false;
  const me = playerById(id);
  const other = playerById(d.partner);
  const myName = me?.name ?? name ?? "상대";
  if (reason === "defeat") {
    if (!PVP.lethal && lostAt.has(d.partner)) graceUntil.set(d.partner, system.currentTick + PVP.graceTicks); // 같은 틱에 서로 쓰러뜨림: 이긴 사람도 체력 1 — 잠깐 보호
    if (!PVP.lethal && me) {
      // 쓰러졌지만 죽지 않음: 잠깐 (graceTicks) 은 남은 불 · 용암 · 떨어짐 · 위더로 쓰러지지 않고, 남은 불 · 독 · 위더는 없앰
      graceUntil.set(id, system.currentTick + PVP.graceTicks);
      try {
        me.extinguishFire(false);
        for (const ef of HARMFUL) me.removeEffect(ef);
      } catch {
        // 무시
      }
    }
    endCue(other, "win", "§6§l승리!", `§f${myName} 님이 쓰러졌습니다`);
    endCue(me, "lose", "§c§l패배", `§f${d.partnerName} 님에게 졌습니다${PVP.lethal ? "" : " §7· 체력 1"}`);
    const msg = `§6⚔ 결투 끝: §f${d.partnerName} §6승리 §7(${myName} 님이 쓰러짐)`;
    say(me, msg);
    say(other, msg);
  } else if (reason === "fell") {
    endCue(other, "end", "§7결투 끝", `§f${myName} 님이 쓰러졌습니다`);
    say(other, `§7⚔ 결투 끝: ${myName} 님이 쓰러졌습니다.`);
    say(me, `§7⚔ 결투 끝: 쓰러졌습니다.`);
  } else if (reason === "command") {
    endCue(me, "end", "§7결투 끝", `§f${d.partnerName} 님과의 결투를 끝냈습니다`);
    endCue(other, "end", "§7결투 끝", `§f${myName} 님이 결투를 끝냈습니다`);
    say(me, `§7⚔ ${d.partnerName} 님과의 결투를 끝냈습니다.`);
    say(other, `§7⚔ ${myName} 님이 결투를 끝냈습니다.`);
  } else if (reason === "left") {
    endCue(other, "end", "§7결투 끝", `§f${myName} 님이 나갔습니다`);
    say(other, `§7⚔ ${myName} 님이 나가서 결투가 끝났습니다.`);
  } else if (reason === "mode") {
    const msg = `§7⚔ ${myName} 님이 생존 · 모험 모드가 아니어서 결투가 끝났습니다.`;
    endCue(other, "end", "§7결투 끝", `§f${myName} 님이 생존 · 모험 모드가 아님`);
    say(me, msg);
    say(other, msg);
  }
  if (!anyDuel()) restorePvpRule();
  return true;
}

/** /pvp 끝: 결투를 끝내거나, 보낸 신청을 취소 (그 사람에게는 retryTicks 동안 다시 보내지 못함) */
function endByCommand(player) {
  if (endDuel(player.id, "command")) return;
  const to = outgoing.get(player.id);
  const req = to ? requests.get(to) : undefined;
  if (live(req) && req.from === player.id) {
    dropRequest(req);
    closeIfShown(req);
    refused.set(`${req.from}|${req.to}`, system.currentTick);
    say(player, `§7${req.toName} 님에게 보낸 결투 신청을 취소했습니다.`);
    say(playerById(req.to), `§7${player.name} 님이 결투 신청을 취소했습니다.`);
    return;
  }
  say(player, "§7결투 중이 아닙니다.");
}

// ------------------------------------------------------------ /pvp 만 쳤을 때: 결투 창
async function openPvpMenu(player) {
  const d = duelOf(player.id);
  if (d) {
    const p = playerById(d.partner);
    const hp = p ? Math.round((hpState(p).hp / Math.max(1, hpState(p).max)) * 100) : 0;
    const sel = await new MenuForm("§c⚔ 결투", `§f${d.partnerName} §7님과 결투 중 §f(상대 체력 ${hp}%)`).button("§4결투 끝내기", ICON.BYE).button("§7닫기").show(player);
    if (sel === 0 && duelOf(player.id)?.token === d.token) endDuel(player.id, "command");
    return;
  }
  const inc = requests.get(player.id);
  if (live(inc)) {
    const sel = await new MenuForm("§c⚔ 결투 신청", `§f${inc.fromName} §7님이 결투를 신청했습니다.`).button("§2⚔ 결투 수락", ICON.OK).button("§4거절", ICON.BYE).button("§7닫기").show(player);
    if (sel === 0 || sel === 1) answer(inc, sel === 0);
    return;
  }
  const others = onlinePlayers().filter((p) => p.id !== player.id);
  const pct = Math.round((1 - PVP.mult) * 100);
  const blocked = blocksDuels(player);
  const menu = new MenuForm(
    "§c⚔ 결투 신청",
    (others.length
      ? `§f결투할 사람을 고르세요.\n§7받은 사람에게 수락 · 거절 창이 뜹니다. 수락하면 서로에게만 평타 · 스킬 · 룬 피해 (받는 피해 ${pct}% 감소). 끝내기: /pvp 끝`
      : "§7다른 접속자가 없습니다.") + (blocked ? "\n§c지금은 결투 신청을 받지 않습니다." : ""),
  );
  const ids = [];
  for (const p of others.slice(0, 30)) {
    const busy = duelOf(p.id) ? " §7(결투 중)" : !isRpgMode(p) ? " §7(생존 · 모험 아님)" : blocksDuels(p) ? " §7(신청 받지 않음)" : "";
    menu.button(`§f${p.name}${busy}`);
    ids.push(p.id);
  }
  menu.button(blocked ? "§2결투 신청 받기" : "§7결투 신청 받지 않기");
  menu.button("§7닫기");
  const sel = await menu.show(player);
  if (sel < 0 || !player.isValid) return;
  if (sel === ids.length) {
    setBlock(player, !blocked);
    return;
  }
  if (sel > ids.length) return;
  const t = playerById(ids[sel]);
  if (!t) {
    say(player, "§7그 사람이 나갔습니다.");
    return;
  }
  requestDuel(player, t.name);
}

/** /pvp [닉네임 | 끝 | 수락 | 거절 | 차단] */
export function pvpCommand(player, arg) {
  const a = String(arg ?? "").trim();
  if (!a) {
    openPvpMenu(player).catch(() => {});
    return;
  }
  const low = a.toLowerCase();
  if (END_WORDS.has(low)) endByCommand(player);
  else if (ACCEPT_WORDS.has(low)) answerTyped(player, true);
  else if (DECLINE_WORDS.has(low)) answerTyped(player, false);
  else if (BLOCK_WORDS.has(low)) setBlock(player, !blocksDuels(player));
  else requestDuel(player, a);
}

// ------------------------------------------------------------ 틱 · 이벤트
function tick() {
  const now = system.currentTick;
  // 만료된 신청
  for (const req of [...requests.values()]) {
    if (now < req.until) continue;
    dropRequest(req);
    closeIfShown(req);
    refused.set(`${req.from}|${req.to}`, now);
    say(playerById(req.from), `§7${req.toName} 님이 ${Math.round(PVP.requestTicks / 20)}초 동안 답하지 않아 결투 신청이 취소되었습니다.`);
    say(playerById(req.to), `§7${req.fromName} 님의 결투 신청이 시간이 지나 취소되었습니다.`);
  }
  for (const [k, t] of refused) if (now - t >= PVP.retryTicks) refused.delete(k);
  for (const [k, t] of lastAsk) if (now - t >= PVP.askGapTicks) lastAsk.delete(k);
  for (const [k, t] of graceUntil) if (t <= now) graceUntil.delete(k);
  for (const [k, t] of lostAt) if (now - t > 20) lostAt.delete(k);
  for (const id of duelists()) {
    const d = duelOf(id);
    if (!d) continue;
    const me = playerById(id);
    const other = playerById(d.partner);
    if (!me && !other) {
      // 둘 다 사라짐 (나감 이벤트를 놓친 경우)
      clearDuel(id);
      if (!anyDuel()) restorePvpRule();
    } else if (!other) endDuel(d.partner, "left", d.partnerName);
    else if (me && !isRpgMode(me)) endDuel(id, "mode"); // 크리에이티브 등으로 바꿈: 다치지 않으므로 결투 끝
  }
}

/** 결투 중인 두 사람 머리 위 표식 (시작한 뒤) */
function markTick() {
  const now = system.currentTick;
  for (const id of duelists()) {
    const d = duelOf(id);
    if (!d || now < d.start) continue;
    const p = playerById(id);
    if (!p) continue;
    try {
      duelMark(p.dimension, overHead(p), { size: 0.3, life: PVP.markEvery / 20 + 0.05, alpha: 0.95, fade: 0.1, vel: bodyVel(p) });
    } catch {
      // 무시
    }
  }
}

/** 주인 없는 피해 (때린 엔티티 없음) 이고 결투 상대 탓이 될 수 있는 원인 (불 · 용암 · 떨어짐 · 위더) 인지 */
function blameable(source) {
  try {
    return !source?.damagingEntity && BLAME_CAUSES.has(String(source?.cause));
  } catch {
    return false;
  }
}

/**
 * 피해가 결투에서 진 것으로 셀 피해인지: 결투 상대 (길들인 동물 포함) 의 피해, 또는 결투 상대가 PVP.blameTicks 안에 때린 뒤의
 * 불 · 용암 · 떨어짐 · 위더. 몹 · 보스 (주인 없는 보스 기술 포함) · /kill 은 아님
 */
function duelBlow(d, victim, source) {
  const from = attackerOf(source);
  if (from) return from.id === d.partner;
  return blameable(source) && system.currentTick - partnerHitAt(victim.id) <= PVP.blameTicks;
}

/**
 * hp.js 결투 훅 (죽을 피해일 때): true = 죽지 않고 체력 1 (그보다 적었으면 그대로).
 * 결투 중 결투 상대 (탓) 의 피해 → 짐 (같은 틱의 남은 불 등도 결투 중이라 그대로 막음 — 다음 틱 duelLost 가 끝내고 graceUntil),
 * 진 뒤 잠깐은 남은 불 · 용암 · 떨어짐 · 위더만. before 이벤트 안에서도 부름 (세계는 바꾸지 않음 — 이 모듈의 기록만)
 */
function duelFloor(player, source) {
  if (PVP.lethal) return false;
  const now = system.currentTick;
  const d = duelOf(player.id);
  if (d && now >= d.start && duelBlow(d, player, source)) {
    lostAt.set(player.id, now);
    return true;
  }
  return (graceUntil.get(player.id) ?? -1) > now && blameable(source);
}

/** hp.js 결투 훅: 결투 상대 (탓) 에게 쓰러져 체력 1로 남음 = 결투에서 짐 (다음 틱 — 그 사이 결투가 끝났으면 아무 일 없음) */
function duelLost(player, _source) {
  if (!lostAt.has(player.id)) return;
  lostAt.delete(player.id);
  if (duelOf(player.id)) endDuel(player.id, "defeat");
}

export function registerPvp() {
  setDuelHooks(duelFloor, duelLost);
  system.runInterval(tick, 10);
  system.runInterval(markTick, PVP.markEvery);
  system.runTimeout(() => restorePvpRule(), 1); // 결투 중에 세계를 닫았으면 켜 둔 PvP 를 되돌림 (결투는 저장하지 않음)
  world.afterEvents.entityDie.subscribe(
    (ev) => {
      const dead = ev.deadEntity;
      if (dead?.typeId !== "minecraft:player") return;
      const d = duelOf(dead.id);
      if (!d) return;
      const from = attackerOf(ev.damageSource);
      endDuel(dead.id, from && from.id === d.partner ? "defeat" : "fell");
    },
    { entityTypes: ["minecraft:player"] },
  );
  world.afterEvents.playerLeave.subscribe(({ playerId, playerName }) => {
    cancelRequestsOf(playerId, (r) => `§7${r.from === playerId ? r.fromName : r.toName} 님이 나가서 결투 신청이 취소되었습니다.`);
    outgoing.delete(playerId);
    graceUntil.delete(playerId);
    lastAsk.delete(playerId);
    lostAt.delete(playerId);
    endDuel(playerId, "left", playerName);
  });
}
