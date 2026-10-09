// 애드온의 진짜 JS 함수를 넓은 입력 격자에 돌려 core/src/test/resources/parity.json 을 씀 (Java ParityTest 가 같은 입력으로 비교).
//
// - 케이스 = {fn, args, expected} 또는 JS 가 예외를 던지면 {fn, args, error}. fn = "<모듈>.<함수>" (js.* · JSON.parse 는 JS 엔진 자체 동작).
// - 숫자 NaN · ±Infinity · -0 은 {"$num": "NaN"} 처럼 적음 (JSON 에 없으므로).
// - 플레이어를 받는 함수 (스탯 · 체력 · 룬 위력 · 전투 배율 등) 는 모의 엔진의 FakePlayer 에 스탯 · 체력 상태를 넣고 진짜 함수를 부르고,
//   케이스에는 그 함수가 실제로 읽은 순수 입력 (getStatsCached 결과 · reqLevelOf · 체력 상태 등) 을 적음 — Java 는 그 입력으로 계산.
// - 무작위 표본은 시드가 고정된 난수 (같은 config.js 면 늘 같은 파일).
//
// 실행: node tools/parity_dump.mjs   (어느 폴더에서든)
import { mkdirSync, writeFileSync } from "node:fs";
import { register } from "node:module";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

register("./parity_hooks.mjs", import.meta.url); // runes.js 의 supportScale · utf8Length 를 __ 이름으로 내보냄

const here = (p) => new URL(p, import.meta.url).href;
const load = (name) => import(here(`./jsenv/scripts/${name}.js`));
const outPath = fileURLToPath(new URL("../core/src/test/resources/parity.json", import.meta.url));

// 스크립트가 쓰는 "@minecraft/server" 와 같은 파일 (같은 모듈 인스턴스)
const { FakePlayer, ItemStack, world } = await import(here("./jsenv/node_modules/@minecraft/server/index.js"));
const C = await load("config");
const weapons = await load("weapons");
const stats = await load("stats");
const hp = await load("hp");
const duel = await load("duel");
const combat = await load("combat");
const armor = await load("armor");
const smith = await load("smith");
const runes = await load("runes");
const runesmith = await load("runesmith");

// ------------------------------------------------------------------ 기록
const cases = [];

/** NaN · ±Infinity · -0 · undefined 를 JSON 으로 */
function enc(v) {
  if (typeof v === "number") {
    if (Number.isNaN(v)) return { $num: "NaN" };
    if (v === Infinity) return { $num: "Infinity" };
    if (v === -Infinity) return { $num: "-Infinity" };
    if (Object.is(v, -0)) return { $num: "-0" };
    return v;
  }
  if (v === undefined) return null;
  if (Array.isArray(v)) return v.map(enc);
  if (v && typeof v === "object") return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, enc(x)]));
  return v;
}

/** 케이스 하나: compute() 가 진짜 JS 결과, view 가 비교할 모양으로 바꿈 */
function rec(fn, args, compute, view = (x) => x) {
  let out;
  try {
    out = view(compute());
  } catch (e) {
    cases.push({ fn, args: enc(args), error: String(e?.message ?? e) });
    return;
  }
  cases.push({ fn, args: enc(args), expected: enc(out) });
}

// 시드 고정 난수 (mulberry32)
let seed = 0x5eed2026;
function rand() {
  seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const ri = (lo, hi) => lo + Math.floor(rand() * (hi - lo + 1));
const pick = (arr) => arr[Math.floor(rand() * arr.length)];
const range = (lo, hi, step = 1) => Array.from({ length: Math.floor((hi - lo) / step) + 1 }, (_, i) => lo + i * step);

// ------------------------------------------------------------------ 비교 모양 (Java ParityTest 의 view 와 같게)
const reqsV = (reqs) => reqs.map((r) => ({ stat: r.stat, value: r.value }));
const weaponV = (w) =>
  w
    ? {
        id: w.id,
        type: w.type,
        level: w.level,
        gradeIndex: w.gradeIndex,
        grade: w.grade.id,
        stat: w.stat,
        req: w.req,
        reqs: reqsV(w.reqs),
        scaling: w.scaling.map((b) => ({ stat: b.stat, per: b.per })),
        icon: w.icon,
        title: w.title,
        name: w.name,
        darkName: w.darkName,
        typeLabel: w.typeLabel,
        look: w.look ?? null,
        legacy: w.legacy,
        skills: w.skills ? w.skills.map((k) => k.id) : null,
        damage: w.damage,
        critChance: w.critChance,
        critMult: w.critMult,
        passive: w.passive,
        block: w.block,
      }
    : null;
const armorV = (a) =>
  a
    ? {
        id: a.id,
        type: a.type,
        typeName: a.typeName,
        level: a.level,
        grade: a.grade.id,
        gradeIndex: a.gradeIndex,
        piece: a.piece,
        pieceName: a.pieceName,
        slot: a.slot,
        set: a.set,
        setName: a.setName,
        prot: a.prot,
        setProt: a.setProt,
        bonus: a.bonus,
        setBonus: a.setBonus,
        reqLevel: a.reqLevel,
        reqs: reqsV(a.reqs),
        icon: a.icon,
        title: a.title,
        name: a.name,
        darkName: a.darkName,
      }
    : null;
const runeV = (r) => (r ? { id: r.id, family: r.family, stat: r.stat, famName: r.famName, grade: r.grade, fn: r.def.fn, defName: r.def.name, cd: r.cd, gradeName: r.gradeName, gradeColor: r.gradeColor } : null);
const appliedV = (e) => ({ stat: e.stat, raw: e.raw, value: e.value, coef: e.coef, parts: e.parts.map((p) => ({ stat: p.stat, raw: p.raw, coef: p.coef })), off: e.off, mix: e.mix, hasMix: e.hasMix, best: !!e.best });
const lackV = (list) => list.map((x) => (x.level !== undefined ? { level: x.level, value: x.value } : { stat: x.stat, value: x.value }));

// ------------------------------------------------------------------ 입력 격자
const GRADE_IDS = C.GRADES.map((g) => g.id);
const ALL_TYPES = [...C.TYPE_ORDER, ...Object.keys(C.TYPE_ONLY)];
const STAT4 = ["str", "dex", "int", "vit"];

/** 무작위 스탯 (찍은 스탯처럼 0 이상 정수): 여러 모양 — 모두 0 · 한 스탯 · 같은 값 · 마력 2 : 힘 1 · 아무렇게나 */
function randomStats(max = 150) {
  const s = { str: 0, dex: 0, int: 0, vit: 0 };
  switch (ri(0, 5)) {
    case 0:
      break;
    case 1:
      s[pick(STAT4)] = ri(1, max);
      break;
    case 2: {
      const v = ri(0, max);
      for (const k of STAT4) if (rand() < 0.6) s[k] = v;
      break;
    }
    case 3: {
      const w = ri(0, Math.floor(max / 3));
      s.int = 2 * w + ri(-3, 3);
      s[pick(["str", "dex"])] = w;
      s.vit = ri(0, 20);
      for (const k of STAT4) s[k] = Math.max(0, s[k]);
      break;
    }
    default:
      for (const k of STAT4) s[k] = rand() < 0.3 ? 0 : ri(0, max);
  }
  return s;
}

// 모의 플레이어 (스탯은 스코어보드)
stats.initObjectives();
const P = new FakePlayer({ name: "Parity" });
const objective = (k) => world.scoreboard.getObjective(k === "point" ? C.OBJ_POINT : k === "lvmax" ? C.OBJ_LVMAX : C.STAT_INFO[k].objective);
/** base: 찍은 스탯 {str,dex,int,vit,point,lvmax}, bonus: 갑옷 보너스, buff: 룬 버프 비율, level: 지금 경험치 레벨 */
function setPlayer(base, bonus = { str: 0, dex: 0, int: 0, vit: 0 }, buff = 0, level = 0) {
  for (const k of [...STAT4, "point", "lvmax"]) objective(k).setScore(P, base[k] ?? 0);
  P.level = level;
  stats.readStats(P, true);
  stats.setStatBonus(P, bonus);
  stats.setStatBuff(P, buff);
}
const randomBonus = () => (rand() < 0.5 ? { str: 0, dex: 0, int: 0, vit: 0 } : Object.fromEntries(STAT4.map((k) => [k, rand() < 0.5 ? 0 : ri(0, 16)])));
const randomBuff = () => pick([0, 0, 0, 0.15, 0.25, 0.1, -0.2]);
const randomBase = () => ({ ...randomStats(), point: ri(0, 20), lvmax: ri(0, 50) });

// 무기 ID: 종류 × 레벨 × 등급 (지금 규칙 · 이전 버전 조합 · 없는 조합)
const WEAPON_IDS = [];
for (const t of [...ALL_TYPES, "spear"]) for (const l of [...C.ALL_LEVELS, 1, 25, 45, 50]) for (const g of [...GRADE_IDS, "legendary"]) WEAPON_IDS.push(`rpg:${t}_l${l}_${g}`);
WEAPON_IDS.push("rpg:sword_l040_common", "rpg:sword_l0_common\n", "rpg:sword_l0_common_x", "rpg:SWORD_l0_common", "rpg:sword_l-5_common", "rpg:sword_l5.5_rare", "rpg:sword_l99999999999999999999_common", "minecraft:iron_sword", "rpg:", "", "rpg:greatsword_l40_hero", "rpg:greatsword_l40_transcend");
const WEAPONS = [...new Set(WEAPON_IDS)].map((id) => weapons.parseWeapon(id)).filter(Boolean);
const uniqueWeapons = [...new Map(WEAPONS.map((w) => [w.id, w])).values()];

// ================================================================== JS 엔진 (Java Js 클래스가 흉내 내는 것)
{
  const special = [0, -0, 1, -1, 0.1, 0.2, 0.1 + 0.2, 1 / 3, 2 / 3, 1.15, 1.3, 1.75, 12.9, 5.6, 100, 1e20, 1e21, 1.5e21, 123456789012345680000, 1e-6, 1e-7, 1.5e-7, 0.000001234, 5e-324, 1.5e-323, 2.2250738585072014e-308, 1.7976931348623157e308, NaN, Infinity, -Infinity, 9007199254740993, 4.35, 0.1 + 0.7, 2 ** 53, 1.005, 1e15 + 0.3, 299792458, 0.5, 1.5, 2.5, -2.5, 0.85, 0.005, 0.0005, 1.1875, 1.275, 0.6666666666666666, 0.3333333333333333, 2 ** -1022, 2 ** 1023, 1e300, 1e-300, 4.94e-322];
  for (const x of special) rec("js.String", [x], () => String(x));
  const dv = new DataView(new ArrayBuffer(8));
  for (let i = 0; i < 1200; i++) {
    dv.setUint32(0, Math.floor(rand() * 2 ** 32));
    dv.setUint32(4, Math.floor(rand() * 2 ** 32));
    const x = dv.getFloat64(0);
    rec("js.String", [x], () => String(x));
  }
  for (let i = 0; i < 600; i++) {
    const x = Math.round((rand() - 0.3) * 10 ** ri(0, 9)) / 10 ** ri(0, 7);
    rec("js.String", [x], () => String(x));
  }
  for (let i = 0; i < 200; i++) {
    const x = (rand() - 0.5) * 10 ** ri(-12, 25);
    rec("js.String", [x], () => String(x));
  }

  const rounds = [0.5, 1.5, 2.5, -0.5, -1.5, -2.5, 0.49999999999999994, -0.49999999999999994, 4503599627370495.5, 4503599627370497, 2 ** 52 - 0.5, -(2 ** 52) + 0.5, 1e20, -1e20, NaN, Infinity, -Infinity, -0, 0, 0.1, -0.1, -0.4, 8.5, 99.5, 12.45 * 10, 1.005 * 100, 5e-324, -5e-324];
  for (const x of rounds) rec("js.Math.round", [x], () => Math.round(x));
  for (let i = 0; i < 300; i++) {
    const x = (rand() - 0.5) * 10 ** ri(-3, 17);
    rec("js.Math.round", [x], () => Math.round(x));
  }
  for (let i = 0; i < 200; i++) {
    const y = ((ri(-2000, 2000) + 0.5) / pick([1, 10, 100, 1000])) * pick([1, 10, 100]); // .5 근처 (곱하면 조금 어긋남)
    rec("js.Math.round", [y], () => Math.round(y));
  }

  const fixed = [0, -0, 0.05, 0.15, 0.25, 0.35, 0.45, 1.005, 1.45, 2.5, -2.5, -0.04, -0.05, 1e21, 1e20, 123.456, NaN, Infinity, -Infinity, 0.5, 1, 2 / 3, 1 / 3, 0.85, 1.5, 0.3333333333333333, 0.6666666666666666, 999.95, 5e-324, 1e-7];
  for (const x of fixed) for (const d of [0, 1, 2, 3]) rec("js.toFixed", [x, d], () => x.toFixed(d));
  for (let i = 0; i < 200; i++) {
    const x = (rand() - 0.5) * 10 ** ri(-3, 6);
    const d = ri(0, 4);
    rec("js.toFixed", [x, d], () => x.toFixed(d));
  }

  const utf = ["", "a", "§r§7", "힘", "가나다", "😀", "\ud800", "a\u0080", "\u07ff\u0800", "€", "\uffff", "§r§7원소 검사 룬 · 희귀 · 재사용 12초", "§r§7정령사 룬 · 초월 · 재사용 36초"];
  for (const s of utf) rec("runes.utf8Length", [s], () => runes.__utf8Length(s));

  const docs = [
    "{}", "[]", "0", "-0", "1.5e3", "1E-2", "-12.5e+2", '"a"', "true", "false", "null", ' {"a" : [1, 2, {"b": null}] } ', '{"a":1,"a":2,"b":3}', '{"b":1,"a":2,"1":3,"0":4}',
    '"\\u00e9\\ud83d\\ude00"', '"\\ud800"', '"\\/\\b\\f\\n\\r\\t\\"\\\\"', "1e400", "-1e-400", "[1e309]", '"§힘 · 체력"', '{"__proto__": 1}', '"\u2028\u2029"', "\t\n\r [ 1 ]\n", '{"":""}', "[[[[[]]]]]",
    '{"x":{"y":{"z":[true,false,null,0.1]}}}', "123456789012345678901234567890", "0.1e1", '"\u007f"', "-0.0", "[-1]",
    '{"10":1,"9":2,"a":3,"01":4,"-1":5,"4294967295":6,"4294967294":7,"b":8,"0":9,"1.5":10," 1":11,"1":12,"1":13}',
    "", " ", "{", "}", "[", "]", "[1,]", '{"a":1,}', "01", "-01", "1.", ".5", "+1", "-", "1e", "1e+", "1.e5", "tru", "nul", "True", "'a'", '"a', '"\\x"', '"\\u12G4"', '"\\u12"',
    '"\t"', '"\n"', "{a:1}", "[1 2]", '{"a" 1}', '{"a":}', "1 2", "NaN", "Infinity", "-Infinity", "\ufeff{}", "/*c*/1", "0x10", "[,1]", "{,}", "[1]]", '{"a":1}}', '{"a":1 "b":2}', '"\\', "[\"a\",]", "\u00a01", "nulll", "[1,,2]",
  ];
  for (const s of docs) rec("JSON.parse", [s], () => JSON.parse(s));
}

// ================================================================== config.js
for (const s of [...STAT4, "luck", ""]) rec("config.damagePerPoint", [s], () => C.damagePerPoint(s));

{
  const levels = [-1, 0, 1, 4, 5, 6, 10, 15, 20, 25, 29, 30, 31, 35, 39, 40, 41, 45, 2.5];
  for (const l of levels)
    for (const g of [...GRADE_IDS, "legendary", ""]) {
      rec("config.isValidWeapon", [l, g], () => C.isValidWeapon(l, g));
      for (const t of [...ALL_TYPES, "spear", ""]) rec("config.isValidWeapon", [l, g, t], () => C.isValidWeapon(l, g, t));
    }
  for (const l of [...range(-5, 50), 2.5, 17.5, 32.5, 37.5, NaN, Infinity]) for (const g of [...GRADE_IDS, "legendary"]) rec("config.nearestGradeLevel", [l, g], () => C.nearestGradeLevel(l, g));
  for (const g of [...GRADE_IDS, "legendary", ""]) rec("config.gradeLevelsText", [g], () => C.gradeLevelsText(g));
  for (const l of [...range(0, 45), 2.5, -3, 100]) for (const gi of [0, 1, 2, 3, 4, 5, -1]) rec("config.reqUnit", [l, gi], () => C.reqUnit(l, gi));

  const multSets = [[["str", 1]], [["str", 2 / 3], ["vit", 1 / 3]], [["int", 2 / 3], ["str", 1 / 3]], [["dex", 0.5], ["vit", 0.5]], [["str", 0.6], ["vit", 0.6]], [["a", 0.2], ["b", 0.3], ["c", 0.5]], [["str", 0]], [], [["str", 1], ["dex", 1]], [["str", 0.35], ["dex", 0.35], ["vit", 0.3]]];
  // 곱이 정수보다 아주 조금 커지는 배율 (100 × 0.07 = 7.000000000000001): splitReq 의 "- 1e-9" 가 결과를 바꾸는 입력
  for (const k of [0.07, 0.14, 0.28, 0.55, 1.1]) multSets.push([["str", k]], [["str", k], ["vit", k]]);
  for (const m of multSets) {
    for (const u of [0, 1, 2.5, 10, 12.5, 25, 37.5, 50, 100, 102.5, 110, 7.5, 0.1, -5]) rec("config.splitReq", [u, m], () => C.splitReq(u, m), reqsV);
    for (let i = 0; i < 25; i++) {
      const u = Math.round(rand() * 1200) / 10;
      rec("config.splitReq", [u, m], () => C.splitReq(u, m), reqsV);
    }
  }
  for (const t of [...ALL_TYPES, "spear"])
    for (const l of range(0, 45))
      for (const gi of [0, 1, 2, 3, 4]) {
        rec("config.requiredStats", [l, gi, t], () => C.requiredStats(l, gi, t), reqsV);
        if ((l + gi) % 3 === 0) rec("config.requiredStat", [l, gi, t], () => C.requiredStat(l, gi, t));
      }
  for (const t of [...ALL_TYPES, "spear", ""]) rec("config.damageScaling", [t], () => C.damageScaling(t), (list) => list.map((b) => ({ stat: b.stat, per: b.per })));
}

{
  for (const t of ALL_TYPES) for (const k of C.SKILLS[t]) rec("config.skillTotalMult", [k], () => C.skillTotalMult(k));
  const fields = ["mult", "reflect", "hits", "shots", "waves", "boltMult", "spellMult", "afterMult", "backMult", "pulseMult", "pulses", "pillarMult", "foldMult"];
  rec("config.skillTotalMult", [{}], () => C.skillTotalMult({}));
  for (let i = 0; i < 80; i++) {
    const k = {};
    for (const f of fields) if (rand() < 0.35) k[f] = pick([0, 1, 2, 3.5, 0.8, 6, 12.25]);
    rec("config.skillTotalMult", [k], () => C.skillTotalMult(k));
  }
  for (const m of [0, -5, 1, 50, 99.5, 100, 151, 1e6, NaN, Infinity, 0.5, -0]) rec("config.tyrfingHpFactor", [m], () => C.tyrfingHpFactor(m));
}

for (const f of [...C.RUNE_FAMILY_ORDER, "fire"]) for (const g of [...GRADE_IDS, "legendary"]) rec("config.runeId", [f, g], () => C.runeId(f, g));

{
  const recipes = [...C.BOSS_CRAFT, { type: "sword", grade: "hero", mat: "x", count: 3 }, { mat: "y", count: 1, also: [], drops: [["nix", 2]] }, { mat: "z", count: 2, also: [["a", 1], ["b", 2]] }];
  for (const r of recipes) rec("config.craftMaterials", [r], () => C.craftMaterials(r));
  for (const k of [...C.BOSS_MATERIAL_ORDER, "troll", "nope"]) rec("config.craftUseLines", ["boss", k], () => C.craftUseLines("boss", k));
  for (const k of [...C.MONSTER_ORDER, "kraken_ink", "nope"]) rec("config.craftUseLines", ["drop", k], () => C.craftUseLines("drop", k));
  rec("config.craftUseLines", ["x", "nix"], () => C.craftUseLines("x", "nix"));
}

{
  for (const n of [...range(-3, 13, 0.5), NaN, Infinity, -Infinity, 1e9, -0]) rec("config.enhanceBonus", [n], () => C.enhanceBonus(n));
  const costV = (c) => (c ? { rate: c.rate, stone: c.stone, bright: c.bright, down: c.down } : null);
  for (const n of [...range(-2, 11), 0.5, 4.5, NaN]) for (const g of [...GRADE_IDS, "legendary"]) rec("config.enhanceCost", [n, g], () => C.enhanceCost(n, g), costV);
}

for (const t of [...C.ARMOR_TYPE_ORDER, "x"]) for (const l of [0, 10, 40, 5.5]) for (const g of ["common", "transcend"]) for (const p of [...C.ARMOR_PIECE_ORDER, "gloves"]) if (rand() < 0.5) rec("config.armorId", [t, l, g, p], () => C.armorId(t, l, g, p));

{
  const keys = [...C.MONSTER_ORDER, ...C.ALL_BOSS_ORDER, "desert_scorpion", "", "x y"];
  for (const k of keys) {
    rec("config.monsterId", [k], () => C.monsterId(k));
    rec("config.bossId", [k], () => C.bossId(k));
    rec("config.altarId", [k], () => C.altarId(k));
    rec("config.bossUnitKeys", [k], () => C.bossUnitKeys(k));
  }
  for (const k of [...C.ALL_BOSS_ORDER, "nope"]) {
    const units = C.BOSSES[k]?.units ?? [];
    for (const u of [null, ...units, { key: "x", spawn: [3, 4] }, { key: "y", spawn: [0, 0] }, { key: "z" }, { key: "w", spawn: [-8, 6] }, { spawn: [1.5, -2.5] }]) {
      rec("config.unitHomeRadius", [k, u], () => C.unitHomeRadius(k, u ?? undefined));
    }
  }
}

// ================================================================== weapons.js · smith.js
for (const x of [0, 0.05, 0.15, 0.25, 1.05, 1.15, 2.45, -1.05, 12.34, 9.95, 1e-9, NaN, 0.0004, 1 / 3, 0.075, 2.675]) {
  rec("weapons.round1", [x], () => weapons.round1(x));
  rec("weapons.pct", [x], () => weapons.pct(x));
}
for (const id of WEAPON_IDS) rec("weapons.parseWeapon", [id], () => weapons.parseWeapon(id), weaponV);
rec("weapons.parseWeapon", [null], () => weapons.parseWeapon(undefined), weaponV);
for (let i = 0; i < 120; i++) {
  const t = pick([...ALL_TYPES, "spear"]);
  const l = pick([...C.ALL_LEVELS, 2.5, 7, 35, 50]);
  const g = pick([...GRADE_IDS, "legendary"]);
  rec("weapons.weaponFrom", [t, l, g], () => weapons.weaponFrom(t, l, g), weaponV);
}
for (const w of uniqueWeapons) {
  rec("weapons.currentWeapon", [w.id], () => weapons.currentWeapon(weapons.parseWeapon(w.id)), weaponV);
  rec("weapons.shopPrice", [w.id], () => weapons.shopPrice(weapons.parseWeapon(w.id)));
  rec("weapons.needsLevel", [w.id], () => weapons.needsLevel(weapons.parseWeapon(w.id)));
  rec("combat.isMeleeWeapon", [w.id], () => combat.isMeleeWeapon(weapons.parseWeapon(w.id)));
  rec("smith.nextLevelOf", [w.id], () => smith.nextLevelOf(weapons.parseWeapon(w.id)), (n) => (n ? { to: n.to.id, emerald: n.cost.emerald, ore: n.cost.ore } : null));
  for (const lv of new Set([0, w.level - 1, w.level, ri(0, 45)])) rec("weapons.levelNote", [w.id, lv], () => weapons.levelNote(weapons.parseWeapon(w.id), lv));
  rec("weapons.weaponInfo", [w.id], () => weapons.weaponInfo(weapons.parseWeapon(w.id)));
  rec("weapons.weaponInfo", [w.id, true], () => weapons.weaponInfo(weapons.parseWeapon(w.id), true));
  const enh = pick([1, 3, 5, 7, 10, 2.5, 11]);
  const dark = rand() < 0.5;
  rec("weapons.weaponInfo", [w.id, dark, enh], () => weapons.weaponInfo(weapons.parseWeapon(w.id), dark, enh));
  if (w.skills) for (const dk of [false, true]) rec("weapons.skillLines", [w.id, dk], () => weapons.skillLines(weapons.parseWeapon(w.id), dk));
  if (rand() < 0.4) {
    const n = pick([1, 7, 10, 0]);
    const custom = pick([undefined, "내 검", "§a빛"]);
    const args = custom === undefined ? [w.id, n] : [w.id, n, custom];
    rec("weapons.enhancedName", args, () => weapons.enhancedName(weapons.parseWeapon(w.id), n, custom));
  }
  for (let i = 0; i < 6; i++) {
    const s = randomStats();
    if (i >= 3) for (const r of w.reqs) s[r.stat] = Math.max(0, r.value + ri(-1, 1)); // 요구치 근처
    if (i === 5) delete s.vit;
    const lv = i % 2 ? ri(0, 45) : w.level + ri(-1, 1);
    rec("weapons.weaponLack", [w.id, lv, s], () => weapons.weaponLack(weapons.parseWeapon(w.id), lv, s), lackV);
  }
}
rec("weapons.needsLevel", [null], () => weapons.needsLevel(undefined));
rec("combat.isMeleeWeapon", [null], () => combat.isMeleeWeapon(undefined));
rec("smith.nextLevelOf", [null], () => smith.nextLevelOf(undefined), (n) => (n ? { to: n.to.id, emerald: n.cost.emerald, ore: n.cost.ore } : null));
for (let i = 0; i < 60; i++) {
  const t = pick(ALL_TYPES);
  const reqs = C.requiredStats(ri(0, 40), ri(0, 4), t);
  const dark = rand() < 0.5;
  const sep = pick([undefined, "§c · ", " / "]);
  const args = sep === undefined ? (rand() < 0.5 ? [reqsV(reqs)] : [reqsV(reqs), dark]) : [reqsV(reqs), dark, sep];
  rec("weapons.reqsText", args, () => weapons.reqsText(...args));
}
{
  const item = new ItemStack("rpg:sword_l0_common", 1);
  for (const v of [undefined, 0, 1, 3, 3.7, 10, 11, 25, -1, 0.5, NaN, Infinity, "5", true, 9.999]) {
    item.setDynamicProperty("rpg:enhance", v);
    rec("weapons.enhanceOf", [v ?? null], () => weapons.enhanceOf(item));
  }
}

// ================================================================== combat.js
{
  let hookAdd = 0;
  combat.addCritBonusHook(() => hookAdd);
  for (const w of uniqueWeapons) {
    for (const add of [0, pick([0.05, 0.2, 0.5, 0.9]), pick([-0.1, NaN, 1e-12])]) {
      hookAdd = add;
      rec("combat.critChanceOf", [w.id, add], () => combat.critChanceOf(P, w));
    }
    hookAdd = pick([0.1, 0.3]);
    const cap = pick([0.5, 0.95, 0.1, 1]);
    rec("combat.critChanceOf", [w.id, hookAdd, cap], () => combat.critChanceOf(P, w, undefined, cap));
    rec("combat.reqText", [w.id], () => combat.reqText(w));
  }
  hookAdd = 0;
  for (let i = 0; i < 1200; i++) {
    const w = pick(uniqueWeapons);
    setPlayer(randomBase(), randomBonus(), randomBuff());
    const s = stats.getStatsCached(P);
    rec("combat.statMultiplier", [s, w.id], () => combat.statMultiplier(P, w));
    if (i % 2 === 0) {
      const n = pick([0, 1, 4, 7, 10, 3.6, -1, 12]);
      combat.rememberEnhance(P, w.id, n); // 손에 든 무기가 아니면 최근에 든 무기의 강화 단계 (heldEnhance)
      rec("combat.powerMultiplier", [s, w.id, n], () => combat.powerMultiplier(P, w));
    }
  }
  for (let i = 0; i < 700; i++) {
    const w = pick(uniqueWeapons);
    const base = randomBase();
    if (rand() < 0.5) for (const r of w.reqs) base[r.stat] = Math.max(0, r.value + ri(-2, 1));
    base.lvmax = rand() < 0.5 ? Math.max(0, w.level + ri(-2, 1)) : ri(0, 50);
    setPlayer(base, randomBonus(), 0, rand() < 0.3 ? ri(0, 50) : 0);
    rec("combat.reqText", [w.id, stats.reqLevelOf(P), stats.getBaseStats(P)], () => combat.reqText(w, P));
  }
}

// ================================================================== stats.js · hp.js · duel.js
for (let i = 0; i < 400; i++) {
  const base = randomBase();
  const bonus = randomBonus();
  const buff = randomBuff();
  setPlayer(base, bonus, buff);
  rec("stats.getStatsCached", [stats.getBaseStats(P), bonus, buff], () => stats.getStatsCached(P));
}
for (let i = 0; i < 150; i++) {
  const lvmax = ri(0, 60);
  const level = pick([ri(0, 60), 0, lvmax, lvmax + 1, NaN]);
  setPlayer({ ...randomStats(), point: 0, lvmax }, undefined, 0, level);
  rec("stats.reqLevelOf", [lvmax, level], () => stats.reqLevelOf(P));
  const best = ri(0, 50);
  const lv = pick([best - 1, best, best + 1, best + ri(2, 20), 0]);
  objective("lvmax").setScore(P, best);
  P.level = lv;
  rec("stats.checkLevelUp", [lv, best], () => stats.checkLevelUp(P));
}
for (let i = 0; i < 250; i++) {
  setPlayer(randomBase(), randomBonus(), randomBuff());
  const s = stats.getStatsCached(P);
  rec("hp.maxHpOf", [s.lvmax, s.vit], () => hp.maxHpOf(P));
  if (i % 3 === 0) rec("hp.levelHpOf", [s.lvmax], () => hp.levelHpOf(P));
}
{
  const S = hp.hpState(P);
  const fresh = (h, m, dead) => {
    S.hp = h;
    S.max = m;
    S.dead = dead;
    S.awaitRespawn = false;
    S.queue.length = 0;
  };
  for (const h of [0, -1, 0.04, 0.05, 0.06, 0.1, 1, 12.345, 12.35, 99.99, 100, NaN, 1e-9]) for (const dead of [false, true]) rec("hp.hpNumber", [dead, h], () => hp.hpNumber({ dead, hp: h }));
  for (let i = 0; i < 500; i++) {
    const lvmax = ri(0, 50);
    const vit = ri(0, 150);
    setPlayer({ str: 0, dex: 0, int: 0, vit, point: 0, lvmax });
    const newMax = hp.maxHpOf(P);
    const m0 = pick([newMax, newMax - ri(1, 30), newMax + ri(1, 30), 0, Math.max(1, newMax - 1)]);
    const h0 = pick([m0, 1, 0.5, m0 * rand(), ri(1, 200), 0]);
    const dead = rand() < 0.1;
    const keep = rand() < 0.5;
    fresh(h0, m0, dead);
    rec("hp.refreshMaxHp", [h0, m0, newMax, dead, keep], () => {
      hp.refreshMaxHp(P, keep);
      return S.hp;
    });
  }
  setPlayer({ str: 0, dex: 0, int: 0, vit: 0, point: 0, lvmax: 0 });
  for (let i = 0; i < 250; i++) {
    const h0 = pick([ri(1, 150), 1, 2, 16.4, 0.5, rand() * 100]);
    const amount = pick([h0 - 1, h0 - 1 + 1e-7, h0 - 1 + 1e-5, h0, ri(0, 40), 15.4, 0, -3, NaN, 0.1 * ri(1, 100)]);
    const dead = rand() < 0.1;
    fresh(h0, 200, dead);
    rec("hp.payHp", [h0, amount, dead], () => {
      const paid = hp.payHp(P, amount);
      return { hp: S.hp, amount: paid };
    });
    const m0 = ri(20, 200);
    const h1 = pick([m0, m0 * rand(), 1, 0]);
    const heal = pick([0, -1, NaN, ri(1, 50), rand() * 10, 1e6]);
    fresh(h1, m0, dead);
    rec("hp.healBy", [h1, m0, heal, dead], () => {
      const got = hp.healBy(P, heal);
      return { hp: S.hp, amount: got };
    });
    const vanilla = pick([0, -1, 1, 2, 0.5, 4, 20, 100, rand() * 6]);
    fresh(h1, m0, dead);
    rec("hp.onPlayerHealBefore", [h1, m0, vanilla, dead], () => {
      hp.onPlayerHealBefore({ healedEntity: P, healing: vanilla, healSource: { cause: "Regeneration" } });
      return S.hp;
    });
    if (i % 2 === 0) {
      const max = pick([ri(1, 300), 4, 5, 14, 15, 25, 0.5, 9.99]);
      fresh(0, max, true);
      rec("hp.revive", [max], () => {
        hp.onPlayerHealBefore({ healedEntity: P, healing: 0, healSource: { cause: "TotemOfUndying" } });
        return S.hp;
      });
    }
    const m2 = pick([ri(1, 200), 0, -5]);
    const h2 = pick([m2 * rand(), m2, 0, 1e-6, m2 * 0.0004, m2 * 1.2]);
    const dead2 = rand() < 0.15;
    fresh(h2, m2, dead2);
    S.saved = -1;
    rec("hp.saveHp", [dead2, h2, m2], () => {
      hp.saveHp(P);
      return P.getDynamicProperty("rpg:hp_ratio");
    });
  }
  fresh(20, 20, false);

  const V = new FakePlayer({ name: "Victim" });
  const VS = hp.hpState(V);
  for (let i = 0; i < 200; i++) {
    const max = pick([20, 100, 151, ri(1, 400)]);
    const dmg = pick([0, 1, 10, 55.5, ri(0, 2000), rand() * 500]);
    VS.max = max;
    rec("duel.duelDamage", [max, dmg], () => duel.duelDamage(V, dmg));
    const saved = { mult: C.PVP.mult, capRatio: C.PVP.capRatio };
    C.PVP.mult = pick([0.1, 0.25, 1, 0.5]);
    C.PVP.capRatio = pick([0.3, 0.05, 1, 0, -0.2]);
    rec("duel.duelDamage", [C.PVP.mult, C.PVP.capRatio, max, dmg], () => duel.duelDamage(V, dmg));
    Object.assign(C.PVP, saved);
  }
}

// ================================================================== armor.js
{
  const ids = [];
  for (const t of C.ARMOR_TYPE_ORDER) for (const l of [0, 5, 10, 15, 20, 30, 35, 40, 45]) for (const g of ["common", "transcend", "rare"]) for (const p of C.ARMOR_PIECE_ORDER) ids.push(C.armorId(t, l, g, p));
  ids.push("rpg:armor_str_l010_common_chest", "rpg:armor_str_l10_common_gloves", "rpg:armor_foo_l10_common_chest", "rpg:armor_str_l10_common_chest\n", "rpg:sword_l0_common", "", "rpg:armor_", "rpg:armor_int_l40_transcend_boots");
  for (const id of ids) rec("armor.parseArmor", [id], () => armor.parseArmor(id), armorV);
  rec("armor.parseArmor", [null], () => armor.parseArmor(undefined), armorV);
  for (let i = 0; i < 60; i++) {
    const args = [pick(C.ARMOR_TYPE_ORDER), pick([0, 10, 20, 30, 40, 25, 7.5]), pick(["common", "transcend"]), pick(C.ARMOR_PIECE_ORDER)];
    rec("armor.armorFrom", args, () => armor.armorFrom(...args), armorV);
  }
  const all = [...new Set(ids)].map((id) => armor.parseArmor(id)).filter(Boolean);
  const unique = [...new Map(all.map((a) => [a.id, a])).values()];
  for (const a of unique) {
    rec("armor.bonusText", [a.setBonus], () => armor.bonusText(a.setBonus));
    rec("armor.bonusText", [a.bonus, true], () => armor.bonusText(a.bonus, true));
    rec("armor.armorReqText", [a.id], () => armor.armorReqText(armor.parseArmor(a.id)));
    rec("armor.armorInfo", [a.id], () => armor.armorInfo(armor.parseArmor(a.id)));
    rec("armor.armorInfo", [a.id, true], () => armor.armorInfo(armor.parseArmor(a.id), true));
    rec("armor.armorPrice", [a.id], () => armor.armorPrice(armor.parseArmor(a.id)));
    for (let i = 0; i < 4; i++) {
      const base = randomBase();
      if (i >= 2) for (const r of a.reqs) base[r.stat] = Math.max(0, r.value + ri(-1, 1));
      base.lvmax = i % 2 ? ri(0, 50) : Math.max(0, a.reqLevel + ri(-1, 1));
      setPlayer(base, randomBonus(), 0, rand() < 0.2 ? ri(0, 50) : 0);
      rec("armor.armorLack", [a.id, stats.reqLevelOf(P), stats.getBaseStats(P)], () => armor.armorLack(P, a));
    }
  }
  for (const b of [{}, { str: 0, vit: 3 }, { int: -2, dex: 5 }, { vit: 1.5 }]) for (const dark of [false, true]) rec("armor.bonusText", [b, dark], () => armor.bonusText(b, dark));
  for (const l of [...range(-5, 50), 2.5, 39.9, NaN]) rec("armor.armorLevelFor", [l], () => armor.armorLevelFor(l));
  for (const t of C.ARMOR_TYPE_ORDER) for (const l of [0, 5, 10, 15, 20, 25, 30, 40, 45]) rec("armor.setPrice", [t, l], () => armor.setPrice(t, l));
}

// ================================================================== runes.js · runesmith.js
{
  const RUNE_IDS = [];
  for (const f of C.RUNE_FAMILY_ORDER) for (const g of GRADE_IDS) RUNE_IDS.push(C.runeId(f, g));
  for (const id of [...RUNE_IDS, "rpg:rune_fire_common", "rpg:rune_str_legendary", "rpg:rune_ticket", "rpg:rune_str_common\n", "rpg:rune_strike_rare", "rpg:rune_st_common", "rune_str_common", ""]) {
    rec("runes.parseRune", [id], () => runes.parseRune(id), runeV);
    rec("runesmith.grindValue", [id], () => runesmith.grindValue(runes.parseRune(id)));
  }
  rec("runes.parseRune", [null], () => runes.parseRune(undefined), runeV);
  for (const id of RUNE_IDS) {
    rec("runes.runeLore", [id], () => runes.runeLore(runes.parseRune(id)));
    rec("runes.runeLore", [id, true], () => runes.runeLore(runes.parseRune(id), true));
    rec("runes.runeTotalMult", [runes.parseRune(id).def], () => runes.runeTotalMult(runes.parseRune(id).def));
  }
  const defFields = { mult: [0, 1, 4, 2.5], hits: [1, 3, 6], dot: [0, 1.5], dots: [2], crush: [0, 6, 20], echo: [0.5], bonus: [1.33, 2], ticks: [120, 160, 50], pulse: [20, 30] };
  for (const fn of ["flurry", "edgeHone", "cleave", "bloodBlade", "swordGod", "bulwark", "undyingPact", "fireball", undefined]) {
    for (let i = 0; i < 4; i++) {
      const d = fn ? { fn } : {};
      for (const [k, vs] of Object.entries(defFields)) if (rand() < 0.5) d[k] = pick(vs);
      rec("runes.runeTotalMult", [d], () => runes.runeTotalMult(d));
    }
  }
  for (const l of [...range(-5, 50, 0.5), NaN, Infinity, -Infinity, 1e9, 33.3]) rec("runes.runeLevelBase", [l], () => runes.runeLevelBase(l));

  const FAMS = C.RUNE_FAMILY_ORDER;
  for (let i = 0; i < 300; i++) {
    const s = randomStats();
    if (i % 4 === 0) for (const k of STAT4) if (rand() < 0.3) s[k] += 0.5;
    const F = C.RUNE_FAMILIES[pick(FAMS)];
    const args = pick([[s], [s, F.mix], [s, F.mix, F.ratio], [s, pick([0.2, 0, -1, 0.5])], [s, 0.2, pick([2, 0, -1, 3, 0.5])]]);
    rec("runes.mixBonus", args, () => runes.mixBonus(...args));
  }
  for (let i = 0; i < 2400; i++) {
    setPlayer(randomBase(), randomBonus(), randomBuff());
    const s = stats.getStatsCached(P);
    const f = i % 50 === 49 ? "fire" : FAMS[i % FAMS.length];
    rec("runes.runeStat", [s, f], () => runes.runeStat(P, f), appliedV);
    if (i % 3 === 0) rec("runes.runePower", [s, f], () => runes.runePower(P, f));
    if (i % 5 === 0) rec("runes.supportScale", [s, f], () => runes.__supportScale(P, f));
    if (i % 4 === 0) {
      const sep = pick([undefined, " x "]);
      rec("runes.runeStatLabel", sep === undefined ? [s, f] : [s, f, sep], () => runes.runeStatLabel(runes.runeStat(P, f), sep));
    }
  }
  for (const adv of [false, true]) {
    rec("runes.gradeWeights", [adv], () => runes.gradeWeights(adv));
    const r1s = [0, 0.1, 0.4999, 0.5, 0.5001, 0.78, 0.7799, 0.92, 0.9199, 0.985, 0.9849, 0.999, 0.99999, 0.65, 0.35];
    for (const r1 of [...r1s, ...Array.from({ length: 25 }, () => rand())])
      for (const r2 of [0, 0.0624, 0.0625, 0.5, 0.9999, rand()]) {
        const seq = [r1, r2];
        rec("runes.rollRune", [r1, r2, adv], () => runes.rollRune(() => seq.shift(), adv));
      }
    for (const r2 of [-0.01, 1, NaN]) {
      const seq = [0.3, r2];
      rec("runes.rollRune", [0.3, r2, adv], () => runes.rollRune(() => seq.shift(), adv));
    }
  }
}

// ------------------------------------------------------------------ 쓰기
mkdirSync(dirname(outPath), { recursive: true });
writeFileSync(outPath, `[\n${cases.map((c) => JSON.stringify(c)).join(",\n")}\n]\n`, "utf8");
const byFn = new Map();
for (const c of cases) byFn.set(c.fn, (byFn.get(c.fn) ?? 0) + 1);
const errors = cases.filter((c) => "error" in c).length;
console.log(`parity.json: 케이스 ${cases.length}개 (함수 ${byFn.size}개, JS 예외 케이스 ${errors}개) → ${outPath}`);
