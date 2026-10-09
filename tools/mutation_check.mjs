// 변이 검사: Java 포트를 한 군데씩 일부러 틀리게 바꾼 사본을 컴파일해 ParityTest 가 잡는지 (실패하는지) 확인.
// 원본 소스는 바꾸지 않음 — core/build/mutation 의 사본만 바꾸고 지움. 마지막에 바꾸지 않은 사본이 통과하는지도 확인.
// 먼저 ./check-core.sh 로 parity.json 을 만들어 둘 것.   실행: node tools/mutation_check.mjs
import { spawnSync } from "node:child_process";
import { cpSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const core = fileURLToPath(new URL("../core", import.meta.url));
const parity = join(core, "src/test/resources/parity.json");
const workRoot = join(core, "build/mutation");

/** 한 번에 하나씩: file 의 from (정확히 한 곳) → to */
const MUTATIONS = [
  { kind: "상수 읽기", name: "maxHpOf 가 HP_PER_VIT 대신 HP_PER_LEVEL 을 읽음", file: "Hp.java", from: 'vit * CONFIG.num("HP_PER_VIT")', to: 'vit * CONFIG.num("HP_PER_LEVEL")' },
  { kind: "상수 읽기", name: "runePower 가 RUNE.perPoint 대신 RUNE.offStat 을 읽음", file: "Runes.java", from: 'runeStat(s, family).value() * RUNE.num("perPoint"));\n    }\n\n    /** runes.js supportScale', to: 'runeStat(s, family).value() * RUNE.num("offStat"));\n    }\n\n    /** runes.js supportScale' },
  { kind: "엡실론", name: "splitReq 의 - 1e-9 를 뺌", file: "Weapons.java", from: "Math.ceil(unit * e.getValue() - 1e-9)", to: "Math.ceil(unit * e.getValue())" },
  { kind: "엡실론", name: "payHp 의 1 - 1e-6 을 1 로", file: "Hp.java", from: "hp - amount < 1 - 1e-6", to: "hp - amount < 1" },
  { kind: "반올림", name: "Math.round 의 .5 를 아래로 (x - r > 0.5)", file: "Js.java", from: "if (x - r >= 0.5) r += 1.0;", to: "if (x - r > 0.5) r += 1.0;" },
  { kind: "반올림", name: "critMult 의 Math.round 를 Math.floor 로", file: "Weapons.java", from: 'double critMult = Js.round(', to: 'double critMult = Math.floor(' },
  { kind: "분기", name: "needsLevel 이 0렙 무기에도 참 (> 0 → >= 0)", file: "Weapons.java", from: "w != null && w.level() > 0", to: "w != null && w.level() >= 0" },
  { kind: "분기", name: "runeStat 의 다른 스탯 비교를 >= 로 (같으면 바꿈)", file: "Runes.java", from: "if (v > best.value())", to: "if (v >= best.value())" },
  { kind: "분기", name: "isValidWeapon 이 TYPE_ONLY 를 보지 않음", file: "Weapons.java", from: 'Obj only = type != null && !type.isEmpty() ? TYPE_ONLY.objOrNull(type) : null;', to: "Obj only = null;" },
  { kind: "순서", name: "JSON 객체를 JS 속성 순서로 바꾸지 않음", file: "Json.java", from: "if (c == '}') return jsOrder(out);", to: "if (c == '}') return out;" },
  { kind: "글자", name: "숫자 글자의 0.000001 경계 (-6 < n → -7 < n)", file: "Js.java", from: "if (-6 < n && n <= 0)", to: "if (-7 < n && n <= 0)" },
];

function javaFiles(dir) {
  return readdirSync(dir).flatMap((n) => {
    const p = join(dir, n);
    return statSync(p).isDirectory() ? javaFiles(p) : n.endsWith(".java") ? [p] : [];
  });
}

function sh(cmd, args) {
  const r = spawnSync(cmd, args, { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
  return { status: r.status, out: `${r.stdout}${r.stderr}`.replace(/^Picked up JAVA_TOOL_OPTIONS.*\n/gm, "") };
}

/** 사본을 (바꿔서) 컴파일하고 ParityTest 실행 → {status, result, failing} */
function trial(m, i) {
  const work = join(workRoot, String(i));
  rmSync(work, { recursive: true, force: true });
  mkdirSync(work, { recursive: true });
  cpSync(join(core, "src"), join(work, "src"), { recursive: true });
  if (m) {
    const path = join(work, "src/main/java/mmorpg/core", m.file);
    const src = readFileSync(path, "utf8");
    const count = src.split(m.from).length - 1;
    if (count !== 1) throw new Error(`${m.name}: ${m.file} 에서 ${count} 곳이 맞음 (1 곳이어야 함)`);
    writeFileSync(path, src.replace(m.from, m.to));
  }
  const classes = join(work, "classes");
  const tests = join(work, "test-classes");
  let r = sh("javac", ["--release", "21", "-encoding", "UTF-8", "-d", classes, ...javaFiles(join(work, "src/main/java"))]);
  if (r.status !== 0) throw new Error(`javac (main) 실패:\n${r.out}`);
  cpSync(join(work, "src/main/resources"), classes, { recursive: true });
  r = sh("javac", ["--release", "21", "-encoding", "UTF-8", "-cp", classes, "-d", tests, ...javaFiles(join(work, "src/test/java"))]);
  if (r.status !== 0) throw new Error(`javac (test) 실패:\n${r.out}`);
  r = sh("java", ["-Dstdout.encoding=UTF-8", "-cp", `${classes}:${tests}`, "mmorpg.core.ParityTest", parity]);
  rmSync(work, { recursive: true, force: true });
  const result = r.out.match(/^RESULT: .*$/m)?.[0] ?? r.out.trim().split("\n").at(-1);
  const failing = [...r.out.matchAll(/^ {2}(\S+)\s+\d+\s+([1-9]\d*)$/gm)].map((x) => `${x[1]} ${x[2]}`);
  return { status: r.status, result, failing };
}

let ok = true;
MUTATIONS.forEach((m, i) => {
  const t = trial(m, i + 1);
  const caught = t.status === 1;
  ok &&= caught;
  console.log(`${caught ? "잡힘  " : "놓침  "} [${m.kind}] ${m.name}`);
  console.log(`       ${t.result}`);
  console.log(`       실패한 함수 (케이스 수): ${t.failing.slice(0, 6).join(", ")}${t.failing.length > 6 ? ` 외 ${t.failing.length - 6}개` : ""}`);
});
const clean = trial(undefined, 0);
console.log(`${clean.status === 0 ? "통과  " : "실패  "} 바꾸지 않은 사본: ${clean.result}`);
rmSync(workRoot, { recursive: true, force: true });
ok &&= clean.status === 0;
console.log(ok ? "변이 검사: 모든 변이를 잡았고 원본은 통과" : "변이 검사: 놓친 변이가 있거나 원본이 실패");
process.exit(ok ? 0 : 1);
