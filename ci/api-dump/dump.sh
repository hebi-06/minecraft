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
  javap -p -cp "$CP" "$c" > "$OUT/javap/$c.txt" 2>&1 || echo "javap failed: $c"
done < ci/api-dump/classes.txt
ls "$OUT/javap" | wc -l
# 믹스인 대상 메서드 안에서 부르는 메서드 (호출 줄만 — 어느 메서드를 가로챌지 정하려고)
mkdir -p "$OUT/calls"
probe() {  # probe <class> <method-name-regex>
  javap -c -p -cp "$CP" "$1" 2>/dev/null | awk -v m="$2" '
    /^  [a-z].*\(.*\);$/ { inm = ($0 ~ ("[ .](" m ")\\(")); if (inm) print "== " $0; next }
    inm && /invoke(virtual|static|special|interface)/ { sub(/^ +[0-9]+: /, ""); print "   " $0 }
  ' > "$OUT/calls/$1.txt"
}
probe net.minecraft.world.entity.player.Player 'attack|actuallyHurt|causeExtraKnockback|doSweepAttack|getAttackStrengthScale'
probe net.minecraft.world.entity.LivingEntity 'heal|actuallyHurt|hurtServer|checkTotemDeathProtection'
probe net.minecraft.world.food.FoodData 'tick'
ls -la "$OUT/calls"
