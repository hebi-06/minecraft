#!/usr/bin/env bash
# 규칙 코어 빌드 · 검증 (JDK 21 + Node 만, 네트워크 · Gradle 없음)
#   1. config.js → rules.json          (tools/export_rules.mjs)
#   2. 진짜 JS 함수로 parity.json 생성  (tools/parity_dump.mjs)
#   3. javac --release 21 → core/build/javac/{main,test}, core/build/mmorpg-core.jar
#   4. ParityTest (하나라도 다르면 실패)
#
# 사용: ./check-core.sh            tools/jsenv/scripts 의 애드온 스크립트 사본으로
#       ./check-core.sh --sync     먼저 애드온 스크립트를 다시 복사 (RPG_ADDON = 애드온 소스 폴더, 기본 ../rpg_addon — RPG_BP/scripts 가 있는 곳)
set -euo pipefail
cd "$(dirname "$0")"

if [[ "${1:-}" == "--sync" ]]; then
  addon="${RPG_ADDON:-../rpg_addon}"
  if [[ ! -d "$addon/RPG_BP/scripts" ]]; then
    echo "애드온 스크립트가 없음: $addon/RPG_BP/scripts" >&2
    exit 1
  fi
  rm -rf tools/jsenv/scripts
  cp -r "$addon/RPG_BP/scripts" tools/jsenv/scripts
  echo "[0/4] 애드온 스크립트 복사: $addon/RPG_BP/scripts → tools/jsenv/scripts"
fi

for tool in node javac java jar; do
  command -v "$tool" >/dev/null || { echo "$tool 이 PATH 에 없음" >&2; exit 1; }
done

echo "[1/4] rules.json"
(cd tools/jsenv && node ../export_rules.mjs)

echo "[2/4] parity.json"
(cd tools/jsenv && node ../parity_dump.mjs)

echo "[3/4] javac"
out=core/build/javac
rm -rf "$out" core/build/mmorpg-core.jar
mkdir -p "$out/main" "$out/test"
find core/src/main/java -name '*.java' > "$out/main-sources.txt"
find core/src/test/java -name '*.java' > "$out/test-sources.txt"
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -d "$out/main" @"$out/main-sources.txt"
cp -r core/src/main/resources/. "$out/main/"
jar --create --file core/build/mmorpg-core.jar -C "$out/main" .
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -cp "$out/main" -d "$out/test" @"$out/test-sources.txt"
cp -r core/src/test/resources/. "$out/test/"

echo "[4/4] ParityTest · MolangTest · StatTreeTest"
java -Dstdout.encoding=UTF-8 -cp "$out/main:$out/test" mmorpg.core.ParityTest
# 애드온 파티클 Molang 식 (tools/molang_cases.py 가 따로 짠 계산기로 푼 답) 과 자바 Molang
java -Dstdout.encoding=UTF-8 -cp "$out/main:$out/test" mmorpg.core.MolangTest
# 스탯 트리 (7단계 — stat_tree.json 의 모양 · 계산 · 스탯 룬 뽑기권)
java -Dstdout.encoding=UTF-8 -cp "$out/main:$out/test" mmorpg.core.StatTreeTest
