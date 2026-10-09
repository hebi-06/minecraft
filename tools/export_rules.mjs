// config.js → core/src/main/resources/mmorpg/rules.json
//
// - config.js 의 export 상수 중 JSON 으로 그대로 옮길 수 있는 것을 모두 씀 (함수는 Java 로 옮겼으므로 뺌).
// - 최상위 키 순서 = config.js 에 선언된 순서, 안쪽 키 순서 = JS 객체 순서 (JSON.stringify 와 같음:
//   정수 모양 키는 오름차순, 나머지는 넣은 순서). Java 쪽 LinkedHashMap 이 이 순서를 그대로 지키므로
//   Object.entries 를 도는 계산 (예: 계수 더하기) 이 같은 순서로 돈다.
// - 값은 모듈을 불러온 뒤의 상태 (config.js 맨 아래에서 BOSS_MATERIALS 의 lore 에 제작 줄을 덧붙인 결과 포함).
// - JSON 으로 옮길 수 없는 값 (함수 · undefined · NaN · Infinity) 이 들어 있는 상수는 쓰지 않고 알림.
//
// 실행: node tools/export_rules.mjs   (어느 폴더에서든)
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const configUrl = new URL("./jsenv/scripts/config.js", import.meta.url);
const outPath = fileURLToPath(new URL("../core/src/main/resources/mmorpg/rules.json", import.meta.url));

const config = await import(configUrl.href);
const source = readFileSync(configUrl, "utf8");

/** 선언 순서: `export const NAME` (함수 export 는 `export function` 이라 걸리지 않음) */
const declared = [...source.matchAll(/^export const (\w+)\b/gm)].map((m) => m[1]);
const exported = Object.keys(config).filter((k) => typeof config[k] !== "function");
const missing = exported.filter((k) => !declared.includes(k));
if (missing.length) throw new Error(`export 상수를 선언 순서에서 찾지 못함: ${missing.join(", ")}`);

/** JSON 으로 그대로 옮길 수 없는 첫 자리 (없으면 undefined) */
function unsupported(value, path) {
  if (value === null || typeof value === "string" || typeof value === "boolean") return undefined;
  if (typeof value === "number") return Number.isFinite(value) ? undefined : `${path} = ${value}`;
  if (Array.isArray(value)) {
    for (let i = 0; i < value.length; i++) {
      const bad = unsupported(value[i], `${path}[${i}]`);
      if (bad) return bad;
    }
    return undefined;
  }
  if (typeof value === "object" && Object.getPrototypeOf(value) === Object.prototype) {
    for (const [k, v] of Object.entries(value)) {
      const bad = unsupported(v, `${path}.${k}`);
      if (bad) return bad;
    }
    return undefined;
  }
  return `${path} (${typeof value})`;
}

const rules = {};
const skipped = [];
for (const name of declared) {
  if (!(name in config)) continue;
  const bad = unsupported(config[name], name);
  if (bad) skipped.push(bad);
  else rules[name] = config[name];
}

mkdirSync(dirname(outPath), { recursive: true });
writeFileSync(outPath, `${JSON.stringify(rules, null, 2)}\n`, "utf8");
console.log(`rules.json: 상수 ${Object.keys(rules).length}개 (함수 ${Object.keys(config).length - exported.length}개 제외) → ${outPath}`);
for (const s of skipped) console.log(`  건너뜀 (JSON 으로 옮길 수 없음): ${s}`);
