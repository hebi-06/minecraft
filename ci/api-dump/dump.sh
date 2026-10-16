#!/usr/bin/env bash
# 마인크래프트 26.3 · Fabric API 클래스의 시그니처 (javap -p) 와 클래스 이름 목록을 /tmp/api 에 (이름 · 형식만, 코드는 없음)
set -u
OUT=/tmp/api
rm -rf "$OUT" && mkdir -p "$OUT/javap"
CP=$(cat ci/api-dump/build/classpath.txt | tr '\n' ':')
MC=$(tr '\n' ' ' < ci/api-dump/build/classpath.txt | tr ' ' '\n' | grep -i 'minecraft-' | tr '\n' ':')
echo "$MC" | tr ':' '\n' > "$OUT/jars.txt"
for j in $(echo "$MC" | tr ':' ' '); do unzip -Z1 "$j" 2>/dev/null | grep '\.class$'; done | sed 's/\.class$//; s#/#.#g' | grep -v '\$[0-9]' | sort -u > "$OUT/classes.txt"
wc -l "$OUT/classes.txt"
while read -r c; do
  [ -z "$c" ] && continue
  javap -p -constants -cp "$CP" "$c" > "$OUT/javap/$c.txt" 2>&1 || echo "javap failed: $c"
done < ci/api-dump/classes.txt
ls "$OUT/javap" | wc -l
# 메서드 몸 (javap -c — 호출 줄 포함) 은 올리지 않음: 공개 저장소에 마인크래프트 바이트코드를 싣지 않게 (시그니처 · 상수만)
