// Node 모듈 훅 (parity_dump.mjs 가 register 로 씀): 애드온의 export 되지 않은 순수 함수를 parity 덤프가 직접 부를 수 있게
// 그 모듈 소스 끝에 export 줄을 덧붙임 (메모리에서만 — 파일은 바꾸지 않음). 내보내는 이름 앞에 "__" 를 붙임.
const EXPOSE = {
  "runes.js": ["supportScale", "utf8Length"],
};

export async function load(url, context, nextLoad) {
  const result = await nextLoad(url, context);
  const file = url.slice(url.lastIndexOf("/") + 1);
  const names = url.includes("/jsenv/scripts/") ? EXPOSE[file] : undefined;
  if (!names) return result;
  const source = typeof result.source === "string" ? result.source : new TextDecoder().decode(result.source);
  return { ...result, source: `${source}\nexport { ${names.map((n) => `${n} as __${n}`).join(", ")} };\n` };
}
