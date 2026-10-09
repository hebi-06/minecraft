import { system, world } from "@minecraft/server";
import { HUNT_ZONE, MONSTERS, NAV } from "./config.js";
import { altars, arenaCenter, arenaSize, inArena } from "./arena.js";
import { allEncounters } from "./bosses.js";
import { DUMMY } from "./dummy.js";
import { setHudExtra } from "./hp.js";
import { zoneList } from "./huntzone.js";
import { mainNpcCall } from "./mainquest.js";
import { NPC_TYPE, noteNpcPos, npcKey, npcNameByKey, npcPos } from "./npcdata.js";
import { navGoal, navOf, onNavChange, setNavOff } from "./quest.js";
import { add, flat, particleVars, rightOf, sub, unit } from "./skills.js";

/**
 * 길 안내 (v2.6.1): 퀘스트를 받으면 다음에 갈 곳을 그 플레이어에게만 보여 줌
 * - 갈 곳: quest.js navGoal (고른 퀘스트 → 보고할 것 → 메인 이야기 → 진행 중인 다른 퀘스트 …).
 *   NPC = 마지막으로 적어 둔 자리 (npcdata.js npcPos — 불러오지 않은 곳도, 불러와져 있으면 지금 자리.
 *   적어 둔 자리 가까이 왔는데 그 NPC가 없으면 "NPC가 없음"),
 *   몬스터 처치·레벨 = 그 몬스터의 가장 가까운 사냥터 (/mobzone), 보스 처치 = 그 보스의 가장 가까운 제단,
 *   무기 시험 = 48칸 안의 훈련용 허수아비 (/dummy — 브란 곁에 두기를 권함), 없으면 허수아비 사냥터
 * - 화면 위 가운데 화살표 칸 (RPG_RP/ui/hud_screen.json — 체력 바처럼 제목 채널을 숨겨서 읽음, hp.js setHudExtra 가 번갈아 보냄):
 *   NAV.hudKey + 테두리 1글자 (0 숨김 · 1 메인 금빛 · 2 일반 하늘빛) + 그림 3글자 (m00~m15·c00~c15 = 내가 보는 쪽에서 시계 방향으로
 *   22.5°씩 돈 화살표, mhr·chr = 도착, non = 그림 없음) + 글자 (이름 · 거리 · 높이). 큰 글자 (일반 제목) 가 떠 있는 동안은 UI가 칸을 숨김
 *   제목은 /title 등 다른 제목을 덜 끊도록 꼭 필요할 때만: 화살표는 경계를 4° 넘어야 바뀌고, 거리는 20칸부터 5칸 · 100칸부터 10칸 단위,
 *   목적지·도착·보이기가 바뀐 것이 아니면 NAV.hudGap 틱에 한 번
 * - 빛 (player.spawnParticle — 그 플레이어에게만): 바닥 빛 화살표 (목적지 쪽으로 차례로 켜짐, 2초마다), 목적지 빛기둥 (96칸 안, 1초마다),
 *   목적지 발밑 고리 (32칸 안), 도착 반짝임 (사냥터·제단은 소리도 — NPC는 npc.js 가 다가갈 때 소리를 냄)
 * - 싸우는 보스 필드 근처 (보스 막대가 보이는 거리) 에서는 화면 칸을 숨김. /nav · 퀘스트 목록에서 끄고 켬
 */

const HIDDEN = `${NAV.hudKey}0non`;
const WHITE = [1, 1, 1];
const UP = { x: 0, y: 1, z: 0 };
const ZERO = { x: 0, y: 0, z: 0 };
const BEAM_HEIGHT = 14;
const DUMMY_RANGE = 48; // 무기 시험: 이 안의 훈련용 허수아비
const MISSING_NEAR = 16; // 적어 둔 NPC 자리에서 이 칸 안인데 NPC가 없으면 "NPC가 없음"
const SECTOR = 22.5;
const DIM_NAMES = { "minecraft:overworld": "오버월드", "minecraft:nether": "네더", "minecraft:the_end": "디 엔드" };

/**
 * @typedef {{kind: "npc" | "zone" | "altar" | "dummy", id: string, name: string, dim?: string, at?: {x: number, y: number, z: number}, radius: number, altar?: any, missing?: boolean}} Target
 * @typedef {{goal?: import("./quest.js").NavGoal, target?: Target, key: string, goalAt: number, waveAt: number, beamAt: number, pingAt: number,
 *   arrived: boolean, hide: boolean, dirty: boolean, sector: number, text: string, textAt: number, urgent: boolean}} NavState
 */
/** @type {Map<string, NavState>} */
const navStates = new Map();

/** @returns {NavState} */
function stateOf(player, now = system.currentTick) {
  let st = navStates.get(player.id);
  if (!st) {
    // 목적지 다시 정하기를 플레이어마다 다른 틱에 (여럿이 한 틱에 몰리지 않게)
    let h = 0;
    for (const ch of String(player.id)) h = (h * 31 + ch.charCodeAt(0)) | 0;
    const phase = 2 * (Math.abs(h) % Math.max(1, Math.floor(NAV.goalEvery / 2)));
    st = { key: "", goalAt: now - NAV.goalEvery + phase, waveAt: 0, beamAt: 0, pingAt: 0, arrived: false, hide: false, dirty: false, sector: -1, text: "", textAt: -100000, urgent: true };
    navStates.set(player.id, st);
  }
  return st;
}

export function forgetNav(playerId) {
  navStates.delete(playerId);
}

/** 다음 틱에 갈 곳을 다시 정함 (/nav 등) */
export function refreshNav(player) {
  const st = stateOf(player);
  st.dirty = true;
  st.urgent = true;
}

// ------------------------------------------------------------ 갈 곳 → 자리
/**
 * NPC 자리: 불러와진 NPC면 지금 자리 (옮겼으면 기록도 고침), 아니면 마지막으로 적어 둔 자리.
 * 적어 둔 자리 가까이 (MISSING_NEAR) 왔고 그곳이 불러와졌는데 그 엔티티가 없으면 missing
 * (같은 번호의 NPC가 근처에 다시 생겼으면 npc.js 의 NPC 연출 (24칸, 0.5초마다) 이 기록을 고침)
 */
function npcSpot(key, player) {
  const rec = npcPos(key);
  if (!rec) return undefined;
  let e;
  try {
    e = world.getEntity(rec.id);
  } catch {
    e = undefined;
  }
  try {
    if (e?.isValid && e.typeId === NPC_TYPE && npcKey(e) === key) {
      noteNpcPos(e, key);
      return { dim: e.dimension.id, at: { ...e.location } };
    }
  } catch {
    // 무시
  }
  const at = { x: rec.x, y: rec.y, z: rec.z };
  try {
    const l = player.location;
    if (rec.dim === player.dimension.id && Math.hypot(at.x - l.x, at.z - l.z) <= MISSING_NEAR && player.dimension.isChunkLoaded(at)) {
      return { dim: rec.dim, at, missing: true };
    }
  } catch {
    // 무시
  }
  return { dim: rec.dim, at };
}

const zoneCenter = (z) => ({ x: z.x + 0.5, y: z.y, z: z.z + 0.5 });

/** 같은 차원에서 가장 가까운 것 (없으면 다른 차원의 것 하나) */
function nearest(list, player, posOf) {
  const l = player.location;
  const dim = player.dimension.id;
  let best;
  let bd = Infinity;
  let other;
  for (const x of list) {
    if (x.dim !== dim) {
      other ??= x;
      continue;
    }
    const p = posOf(x);
    const d = Math.hypot(p.x - l.x, p.y - l.y, p.z - l.z);
    if (d < bd) {
      bd = d;
      best = x;
    }
  }
  return best ?? other;
}

/**
 * @param {any} player
 * @param {import("./quest.js").NavGoal | undefined} g
 * @returns {Target | undefined}
 */
function resolve(player, g) {
  if (!g) return undefined;
  if (g.npc) {
    const spot = npcSpot(g.npc, player);
    return { kind: "npc", id: g.npc, name: npcNameByKey(g.npc) || mainNpcCall(g.npc) || "NPC", dim: spot?.dim, at: spot?.at, radius: NAV.arrive, missing: !!spot?.missing };
  }
  if (g.why === "boss" && g.boss) {
    const a = nearest(
      altars().filter((x) => x.key === g.boss),
      player,
      arenaCenter,
    );
    return a ? { kind: "altar", id: `${a.dim}|${a.x},${a.y},${a.z}`, name: "", dim: a.dim, at: arenaCenter(a), radius: arenaSize(a).radius, altar: a } : undefined;
  }
  if (g.why === "try") {
    // 무기 시험: 가까운 훈련용 허수아비 (때려도 죽지 않음), 없으면 아래의 허수아비 사냥터
    try {
      const [d] = player.dimension.getEntities({ type: DUMMY, location: player.location, maxDistance: DUMMY_RANGE, closest: 1 });
      if (d) return { kind: "dummy", id: d.id, name: "", dim: d.dimension.id, at: { ...d.location }, radius: NAV.arrive };
    } catch {
      // 무시
    }
  }
  if (g.mob || g.mobs?.length) {
    // v2.9.0 전설의 시련: 몬스터 여럿 중 어느 것이든 → 그 중 가장 가까운 사냥터
    const kinds = g.mobs?.length ? g.mobs : [g.mob];
    const z = nearest(
      zoneList().filter((x) => kinds.includes(x.kind)),
      player,
      zoneCenter,
    );
    return z ? { kind: "zone", id: z.id, name: "", dim: z.dim, at: zoneCenter(z), radius: HUNT_ZONE.radius + 2 } : undefined;
  }
  return undefined;
}

/** 싸우는 보스 필드 근처인지 (보스 막대가 화면 위 가운데에 보이는 거리) */
function nearBossFight(player) {
  const l = player.location;
  const dim = player.dimension.id;
  for (const enc of allEncounters()) {
    try {
      if (enc.a.dim !== dim) continue;
      const c = enc.center;
      if (Math.hypot(l.x - c.x, l.z - c.z) <= arenaSize(enc.a).radius + NAV.bossHide) return true;
    } catch {
      // 무시
    }
  }
  return false;
}

// ------------------------------------------------------------ 글자
/** @param {import("./quest.js").NavGoal} g @param {Target | undefined} t */
function labelOf(g, t) {
  const prog = g.count ? ` §7${Math.min(g.n ?? 0, g.count)}/${g.count}` : "";
  const name = t?.name || "NPC";
  switch (g.why) {
    case "turnin":
      return `§e${name}§f에게 보고`;
    case "talk":
      return `§e${name}§f에게`;
    case "offer":
      return `§e${name}§f에게 새 이야기`;
    case "hunt":
      return `§c${(g.mob && MONSTERS[g.mob]?.name) || g.what} §f처치${prog}`;
    case "boss":
      return `§c${g.what} §f제단`;
    case "try":
      return `§f무기 시험${prog}`;
    case "collect":
      return `§f${g.what} 모으기${prog}`;
    case "level": {
      const m = g.mob ? MONSTERS[g.mob] : undefined;
      return `§7레벨 ${g.lv} 필요${m ? ` §7· §f${m.name} 사냥 §7(Lv.${m.level})` : ""}`;
    }
    default:
      return `§f${g.q.title}`;
  }
}

/** 거리: 20칸까지 1칸, 100칸까지 5칸, 그 밖은 10칸 단위 (걸을 때마다 글자가 바뀌어 제목을 너무 자주 보내지 않게) */
export function distText(d) {
  const step = d < 20 ? 1 : d < 100 ? 5 : 10;
  return `${Math.round(d / step) * step}칸`;
}

const ARRIVED = { npc: " §a· 여기!", zone: " §a· 사냥터", altar: " §a· 제단을 터치", dummy: " §a· 훈련용 허수아비" };

// ------------------------------------------------------------ 방향
/** 내가 보는 쪽에서 목적지까지 시계 방향 각 (도, -180 ~ 180: 0 = 앞, 90 = 오른쪽) */
export function angleOf(view, from, to) {
  const f = flat(view);
  const d = flat(sub(to, from));
  const r = rightOf(f);
  return (Math.atan2(d.x * r.x + d.z * r.z, d.x * f.x + d.z * f.z) * 180) / Math.PI;
}

/** 각 → 0~15 칸 (0 = 앞, 4 = 오른쪽, 8 = 뒤, 12 = 왼쪽). prev 가 있으면 경계를 NAV.sectorHold 도 넘어야 바뀜 (경계에서 화살표가 떨리지 않게) */
export function sectorFor(ang, prev = -1) {
  if (prev >= 0) {
    let off = ang - prev * SECTOR;
    off = ((((off + 180) % 360) + 360) % 360) - 180;
    if (Math.abs(off) <= SECTOR / 2 + NAV.sectorHold) return prev;
  }
  return (((Math.round(ang / SECTOR) % 16) + 16) % 16);
}

/** 경계 머무름 없이 칸 (시험용) */
export function sectorOf(view, from, to) {
  return sectorFor(angleOf(view, from, to));
}

/** 내가 보는 쪽: 거의 바로 위·아래를 보면 수평 성분이 없어 몸의 방향 (yaw) 으로 */
function facing(player) {
  const v = player.getViewDirection();
  if (Math.hypot(v.x, v.z) >= 0.1) return v;
  const yaw = (player.getRotation().y * Math.PI) / 180;
  return { x: -Math.sin(yaw), y: 0, z: Math.cos(yaw) };
}

function isHere(player, t, d, dy) {
  if (t.kind === "altar") return inArena(t.altar, player.dimension.id, player.location);
  return d <= t.radius && Math.abs(dy) <= (t.kind === "zone" ? 6 : 3);
}

function leftHere(player, t, d) {
  if (t.kind === "altar") return !inArena(t.altar, player.dimension.id, player.location, 3);
  return d > t.radius + 3;
}

// ------------------------------------------------------------ 빛 (그 플레이어에게만)
function pfx(player, id, at, o) {
  try {
    player.spawnParticle(id, at, particleVars(o));
  } catch {
    // 불러오지 않은 곳 등
  }
}

/**
 * 바닥 높이: 발보다 2.5칸 위에서 아래로 8칸 (언덕 오르막도) — 닿은 윗면의 높이 (반 블록·눈 층도), 물 위도.
 * 시작점이 블록 안이거나 윗면이 아니면 (낮은 굴 천장 등) 발 높이
 */
function groundY(dim, at, y0) {
  try {
    const hit = dim.getBlockFromRay({ x: at.x, y: y0 + 2.5, z: at.z }, { x: 0, y: -1, z: 0 }, { maxDistance: 8, includeLiquidBlocks: true });
    if (hit?.block && (hit.face === undefined || String(hit.face) === "Up")) {
      const y = hit.block.location.y + (Number.isFinite(hit.faceLocation?.y) ? hit.faceLocation.y : 1);
      if (y <= y0 + 2.4) return y;
    }
  } catch {
    // 불러오지 않은 곳
  }
  return y0;
}

/** 바닥 빛 화살표: 발밑에서 목적지 쪽으로 > > > 가 차례로 켜졌다 사라짐 */
function wave(player, t, color) {
  const p = player.location;
  const dir = flat(sub(t.at, p));
  const side = rightOf(dir);
  const dist = Math.hypot(t.at.x - p.x, t.at.z - p.z);
  const W = NAV.wave;
  for (let i = 0; i < W.count; i++) {
    const d = W.from + i * W.step;
    if (d > dist - 1.5) break;
    const c = add(p, dir, d);
    const y = groundY(player.dimension, c, p.y) + 0.12;
    const tip = { x: c.x + dir.x * 0.26, y, z: c.z + dir.z * 0.26 };
    const delay = i * W.delay;
    for (const s of [-1, 1]) {
      const end = { x: c.x - dir.x * 0.22 + side.x * 0.32 * s, y, z: c.z - dir.z * 0.22 + side.z * 0.32 * s };
      const mid = { x: (tip.x + end.x) / 2, y, z: (tip.z + end.z) / 2 };
      pfx(player, "rpg:arrow_trail", mid, { dir: unit(sub(end, tip)), size: 0.29, thick: 0.065, life: delay + 0.6, delay, color, alpha: 0.9 });
    }
  }
}

/** 목적지 빛기둥 (NPC는 머리 위부터) */
function beam(player, t, color) {
  const base = t.kind === "npc" || t.kind === "dummy" ? 2.3 : 0.3;
  const mid = { x: t.at.x, y: t.at.y + base + BEAM_HEIGHT / 2, z: t.at.z };
  pfx(player, "rpg:arrow_shot", mid, { dir: UP, speed: 0, size: BEAM_HEIGHT / 2, thick: 0.3, life: 1.2, color, alpha: 0.5 });
  pfx(player, "rpg:arrow_shot", mid, { dir: UP, speed: 0, size: BEAM_HEIGHT / 2, thick: 0.08, life: 1.1, color: WHITE, alpha: 0.8 });
}

/** 목적지 발밑 고리 */
function ping(player, t, color) {
  const size = t.kind === "npc" || t.kind === "dummy" ? 1.4 : Math.min(8, t.radius);
  pfx(player, "rpg:shock_ring", { x: t.at.x, y: t.at.y + 0.08, z: t.at.z }, { dir: ZERO, size, life: 0.9, color, alpha: 0.75 });
}

/** 도착: 고리 + 위로 튀는 빛 (NPC가 아니면 소리도 — 사냥터·제단·허수아비) */
function arrivalFx(player, t, color) {
  const at = t.kind === "npc" || t.kind === "dummy" ? t.at : player.location;
  const base = { x: at.x, y: at.y + 0.08, z: at.z };
  pfx(player, "rpg:shock_ring", base, { dir: ZERO, size: 2.2, life: 0.7, color, alpha: 0.95 });
  for (let i = 0; i < 10; i++) {
    const a = (i / 10) * Math.PI * 2;
    pfx(player, "rpg:arrow_spark", { x: base.x, y: base.y + 0.6, z: base.z }, { dir: unit({ x: Math.cos(a), y: 1.2, z: Math.sin(a) }), speed: 3, size: 0.09, life: 0.55, color, alpha: 0.95 });
  }
  if (t.kind !== "npc") {
    try {
      player.playSound("random.orb", { pitch: 1.7, volume: 0.6 });
    } catch {
      // 무시
    }
  }
}

// ------------------------------------------------------------ 틱
function targetKey(g, t) {
  if (!g) return "";
  return `${g.q.id}|${g.why}|${t ? `${t.kind}:${t.id}` : ""}`;
}

/** 화면 칸 글자를 맡김: 목적지·도착·보이기가 바뀌었으면 바로, 아니면 (화살표·거리만 바뀜) NAV.hudGap 틱에 한 번 */
function show(player, st, now, text, visible) {
  if (text === st.text) return;
  if (!st.urgent && now - st.textAt < NAV.hudGap) return;
  st.text = text;
  st.textAt = now;
  st.urgent = false;
  setHudExtra(player, "nav", text, visible);
}

/** 한 플레이어 (2틱마다) */
function tickPlayer(player, now) {
  const st = stateOf(player, now);
  if (st.dirty || now - st.goalAt >= NAV.goalEvery) {
    st.dirty = false;
    st.goalAt = now;
    st.goal = navGoal(player);
    st.target = resolve(player, st.goal);
    const hide = !!st.goal && nearBossFight(player);
    if (hide !== st.hide) st.urgent = true;
    st.hide = hide;
    const key = targetKey(st.goal, st.target);
    if (key !== st.key) {
      // 갈 곳이 바뀜 (퀘스트를 받음 · 목표를 채움 · 보고함): 바로 빛 화살표와 빛기둥, 화면 칸도 바로
      st.key = key;
      st.arrived = false;
      st.sector = -1;
      st.waveAt = now;
      st.beamAt = now;
      st.pingAt = now;
      st.urgent = true;
    }
  }
  const g = st.goal;
  if (!g || st.hide) {
    show(player, st, now, HIDDEN, false);
    return;
  }
  const t = st.target;
  const c = g.main ? "m" : "c";
  const color = NAV.colors[c];
  let code = "non";
  let tail = "";
  if (!t) {
    // 갈 곳이 없음: 모으기는 글자만, 이 애드온 몬스터·보스인데 사냥터·제단이 없으면 알려 줌 (운영자가 /mobzone · /boss altar 로 둠)
    if (g.why === "boss") tail = " §7· 제단 없음";
    else if (g.mob || g.mobs?.length) tail = " §7· 사냥터 없음";
  } else if (!t.at) tail = " §7· 위치 모름";
  else if (t.dim !== player.dimension.id) tail = ` §7· ${DIM_NAMES[t.dim ?? ""] ?? "다른 차원"}에 있음`;
  else if (t.missing) tail = " §7· NPC가 없음";
  else {
    const l = player.location;
    const d = Math.hypot(t.at.x - l.x, t.at.z - l.z);
    const dy = t.at.y - l.y;
    if (!st.arrived && isHere(player, t, d, dy)) {
      st.arrived = true;
      st.urgent = true;
      arrivalFx(player, t, color);
    } else if (st.arrived && leftHere(player, t, d)) {
      st.arrived = false;
      st.urgent = true;
    }
    if (st.arrived) {
      code = `${c}hr`;
      tail = ARRIVED[t.kind];
    } else {
      st.sector = sectorFor(angleOf(facing(player), l, t.at), st.sector);
      code = `${c}${String(st.sector).padStart(2, "0")}`;
      tail = ` §7· §f${distText(d)}${Math.abs(dy) >= NAV.height ? ` §7(${dy > 0 ? "위" : "아래"} ${Math.round(Math.abs(dy))})` : ""}`;
      if (now >= st.waveAt) {
        st.waveAt = now + NAV.waveEvery;
        wave(player, t, color);
      }
      if (d <= NAV.beamRange && now >= st.beamAt) {
        st.beamAt = now + NAV.beamEvery;
        beam(player, t, color);
      }
      if (d <= NAV.pingRange && now >= st.pingAt) {
        st.pingAt = now + NAV.waveEvery;
        ping(player, t, color);
      }
    }
  }
  show(player, st, now, `${NAV.hudKey}${g.main ? 1 : 2}${code}${labelOf(g, t)}${tail}`, true);
}

export function navTick() {
  const now = system.currentTick;
  for (const player of world.getAllPlayers()) {
    try {
      tickPlayer(player, now);
    } catch (e) {
      console.warn(`[rpg] nav: ${e}`);
    }
  }
}

// ------------------------------------------------------------ /nav
const plain = (s) => String(s ?? "").replace(/§./g, "");

/** /nav [on|off]: 켜고 끔 (없으면 바꿈). 켜면 지금 갈 곳을 알려 줌 */
export function navCommand(player, mode) {
  const off = mode === "off" ? true : mode === "on" ? false : !navOf(player).off;
  setNavOff(player, off);
  refreshNav(player);
  player.playSound("random.click", { pitch: off ? 0.8 : 1.3 });
  if (off) return "§6[RPG] §7길 안내를 껐습니다. §8(/nav 로 다시 켜기)";
  const g = navGoal(player);
  if (!g) return "§6[RPG] §b길 안내를 켰습니다. §7지금은 갈 곳이 없습니다 (퀘스트를 받으면 안내합니다).";
  const t = resolve(player, g);
  const where = t?.at ? ` §7(${DIM_NAMES[t.dim ?? ""] ?? ""} ${Math.floor(t.at.x)} ${Math.floor(t.at.y)} ${Math.floor(t.at.z)})` : t ? " §7(위치 모름)" : "";
  return `§6[RPG] §b길 안내를 켰습니다. §f${g.q.title} → ${plain(labelOf(g, t))}${where}`;
}

export function registerNav() {
  onNavChange(refreshNav); // 받음 · 목표를 채움 · 보고 · 끄고 켬 → 다음 틱에 바로
  system.runInterval(navTick, 2);
}
