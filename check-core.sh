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

echo "[4/4] ParityTest · MolangTest · StatTreeTest · RuneBreakTest · MonstersMoreTest · HeroSkillsTest · WeaponsMoreTest · ArmorMoreTest · RunesMoreTest · GachaTest · LimitedRunesTest · RuneTipTest · EconomyTest · StorageTest"
# 대조 시험은 애드온 config.js 그대로의 규칙으로 (자바 전용 덧붙임 rules_more.json 없이 — Rules.PURE)
java -Dstdout.encoding=UTF-8 -Dmmorpg.pureRules=true -cp "$out/main:$out/test" mmorpg.core.ParityTest
# 애드온 파티클 Molang 식 (tools/molang_cases.py 가 따로 짠 계산기로 푼 답) 과 자바 Molang
java -Dstdout.encoding=UTF-8 -cp "$out/main:$out/test" mmorpg.core.MolangTest
# 스탯 트리 (7단계 — stat_tree.json 의 모양 · 계산 · 스탯 룬 뽑기권)
java -Dstdout.encoding=UTF-8 -cp "$out/main:$out/test" mmorpg.core.StatTreeTest
# 룬 돌파 (8단계 — rune_break.json 의 모양 · 계수 · 합치기 · 초월 3 · 6돌 강화 · 글자)
java -Dstdout.encoding=UTF-8 -cp "$out/main:$out/test" mmorpg.core.RuneBreakTest
# 9단계 (자바 전용 규칙 덧붙임 — 새 몬스터 8종 · 전리품 · 반복 의뢰, 레벨업 곡선 ×3)
java -Dstdout.encoding=UTF-8 -cp "$out/main:$out/test" mmorpg.core.MonstersMoreTest
# 10단계 (자바 전용 규칙 덧붙임 — 영웅 무기 스킬 6종: 무기마다 하나 · 세기 · 설명 줄)
java -Dstdout.encoding=UTF-8 -cp "$out/main:$out/test" mmorpg.core.HeroSkillsTest
# 12단계 (자바 전용 규칙 덧붙임 — 새 무기 종류 4개: 조합 · 요구 스탯 · 세기 · 스킬 · 설명 줄 · 제작 · 정령왕 강림)
java -Dstdout.encoding=UTF-8 -cp "$out/main:$out/test" mmorpg.core.WeaponsMoreTest
# 0.12.5 밸런스 (자바 전용 규칙 덧붙임 — 두 스탯 무기용 갑옷 5종 · 방패 상시 감소 상한 25% · 가벼운 갑옷 방어 민첩 0.9 · 마력 0.8)
java -Dstdout.encoding=UTF-8 -cp "$out/main:$out/test" mmorpg.core.ArmorMoreTest
# 0.12.6 (자바 전용 규칙 덧붙임 — 사신 계열 룬 (낫 전용 · 피의 초승달) · 성기사 룬 적용 스탯 체력 + 힘 × 0.5)
java -Dstdout.encoding=UTF-8 -cp "$out/main:$out/test" mmorpg.core.RunesMoreTest
# 0.14.0 룬 뽑기 (자바 전용 — gacha.json: 원신식 천장 · 반천장 / 확천장 · 한정 뽑기 설정 · 통합 확률 모의)
java -Dstdout.encoding=UTF-8 -cp "$out/main:$out/test" mmorpg.core.GachaTest
# 0.16.0 한정 초월 룬 셋 (자바 전용 — 계열 arcana · dipper · skyeye: 네 스탯의 합 × 1.0 · 배율 34 · 42 · 55 · 한정 뽑기의 초월 픽업으로만 · 돌파)
java -Dstdout.encoding=UTF-8 -cp "$out/main:$out/test" mmorpg.core.LimitedRunesTest
# 0.16.1: 자바 판 룬 툴팁 (총 피해 숫자 · 뺀 줄 · 계수 · 돌파 줄)
java -Dstdout.encoding=UTF-8 -cp "$out/main:$out/test" mmorpg.core.RuneTipTest
# 0.21.0 화폐 (골드 · 실버 지갑) · 유저 장터 (수수료 · 개수 · 기간 · 정렬) · 골드 보상
java -Dstdout.encoding=UTF-8 -cp "$out/main:$out/test" mmorpg.core.EconomyTest
# 0.22.0 엔더 창고 (상자 5개 · 기본 1개 · 54칸 · 2번 실버 · 3번부터 골드 · 차례로)
java -Dstdout.encoding=UTF-8 -cp "$out/main:$out/test" mmorpg.core.StorageTest
