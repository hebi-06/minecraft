#!/usr/bin/env bash
# 배포 jar 로 진짜 Fabric 서버를 띄워 봄 (개발 환경의 게임 시험과 달리 실제 설치와 같게: mods 폴더에 모드 jar + Fabric API jar).
# 서버가 "Done" 까지 켜지는지 (모드 시작 (main entrypoint) · 믹스인 · 첫 틱들), 켜지는 동안 우리 모드 쪽 예외가 없는지 확인.
# Fabric API 는 둘: gradle.properties 의 버전 (실패하면 이 단계 실패 → 배포 안 함) 과 그 마인크래프트 버전용 가장 오래된 것 (참고 — 결과만 남김).
set -u
MC=$(grep '^minecraft_version=' gradle.properties | cut -d= -f2)
LOADER=$(grep '^loader_version=' gradle.properties | cut -d= -f2)
FAPI=$(grep '^fabric_version=' gradle.properties | cut -d= -f2)
JAR=$(ls build/libs/*.jar | grep -v -- '-sources' | head -1)
WS=$(pwd)
echo "mod jar: $JAR  minecraft $MC  loader $LOADER  fabric-api $FAPI"
unzip -l "$JAR" | grep -E "mmorpg/rules.json|mmorpg/core/Rules.class|fabric.mod.json|mixins.json" || true

INSTALLER=$(curl -sf https://meta.fabricmc.net/v2/versions/installer | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["version"])')
OLDEST=$(curl -sf https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml | python3 -c '
import re, sys
mc = sys.argv[1]
vs = [v for v in re.findall(r"<version>([^<]+)</version>", sys.stdin.read()) if v.endswith("+" + mc)]
key = lambda v: tuple(int(x) if x.isdigit() else -1 for x in re.split(r"[.\-]", v.split("+")[0]))
print(sorted(vs, key=key)[0] if vs else "")
' "$MC")
echo "fabric installer $INSTALLER, oldest fabric-api for $MC: ${OLDEST:-none}"

# run_one <fabric-api 버전> → 0 = Done 까지 켜지고 우리 모드 예외 없음
run_one() {
  local v=$1
  local dir=/tmp/prod-$v
  rm -rf "$dir" && mkdir -p "$dir/mods"
  cp "$WS/$JAR" "$dir/mods/"
  if ! curl -sfL -o "$dir/mods/fabric-api-$v.jar" "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$v/fabric-api-$v.jar"; then
    echo "[prod] $v: fabric-api download failed"; return 2
  fi
  curl -sfL -o "$dir/server.jar" "https://meta.fabricmc.net/v2/versions/loader/$MC/$LOADER/$INSTALLER/server/jar" || { echo "[prod] server launcher download failed"; return 2; }
  echo "eula=true" > "$dir/eula.txt"
  printf 'online-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nmax-tick-time=-1\nsync-chunk-writes=false\n' > "$dir/server.properties"
  (
    cd "$dir" || exit 1
    rm -f in exit.code
    mkfifo in
    (java -Xmx2G -jar server.jar nogui < in > out.log 2>&1; echo $? > exit.code) &
    exec 3>in
    local t=0 up=0
    while [ $t -lt 420 ]; do
      if grep -q 'Done (' out.log 2>/dev/null; then up=1; break; fi
      [ -f exit.code ] && break
      sleep 1; t=$((t + 1))
    done
    if [ $up -eq 1 ]; then sleep 8; echo "stop" >&3; fi
    for _ in $(seq 1 90); do [ -f exit.code ] && break; sleep 1; done
    exec 3>&-
    [ -f exit.code ] || { pkill -f "jar server.jar nogui" || true; sleep 2; }
  )
  local log=$dir/out.log
  echo "----- [prod] fabric-api $v: log tail"
  tail -n 25 "$log"
  echo "----- [prod] fabric-api $v: errors"
  grep -n -E "Exception|Caused by|Could not execute|/ERROR\]|/FATAL\]" "$log" | head -n 80
  cp "$log" "$WS/ci-out/prod-server-$v.log" 2>/dev/null || true
  if grep -q 'Done (' "$log" && ! grep -q -E "Could not execute entrypoint|Exception in server tick loop|mmorpg.*(Exception|Error)" "$log"; then
    echo "[prod] fabric-api $v: OK"
    return 0
  fi
  echo "[prod] fabric-api $v: FAILED"
  return 1
}

run_one "$FAPI"
main=$?
if [ -n "$OLDEST" ] && [ "$OLDEST" != "$FAPI" ]; then
  run_one "$OLDEST"
  echo "[prod] oldest fabric-api $OLDEST exit $? (참고)"
fi
exit $main
