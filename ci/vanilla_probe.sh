#!/usr/bin/env bash
# 2026-11-07 1번 (모드 없이 접속) 시험: 배포 jar 로 진짜 Fabric 서버를 켜고 (모드 jar + Fabric API — Polymer 는 모드 jar 안),
# 모드 없는 클라이언트 (바닐라 + 사진만 찍는 작은 모드 src/probe — Fabric API 없음) 로 접속해 서버 장면 (VanillaProbe) 마다 사진.
# 통과: 서버 로그 "[vanilla-probe] done" · 클라이언트 로그 "[vanilla-client] done" · 끊김 없음.
# 결과: ci-out/vanilla-server.log · vanilla-client.txt · vanilla-client-latest.log · shots/vanilla_*.png
set -u
MC=$(grep '^minecraft_version=' gradle.properties | cut -d= -f2)
LOADER=$(grep '^loader_version=' gradle.properties | cut -d= -f2)
FAPI=$(grep '^fabric_version=' gradle.properties | cut -d= -f2)
JAR=$(ls build/libs/*.jar | grep -v -- '-sources' | head -1)
WS=$(pwd)
mkdir -p ci-out/shots
echo "mod jar: $JAR  minecraft $MC  loader $LOADER  fabric-api $FAPI"
unzip -l "$JAR" | grep -E "META-INF/jars/" || echo "(jar 안 jar 없음)"

INSTALLER=$(curl -sf https://meta.fabricmc.net/v2/versions/installer | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["version"])')
dir=/tmp/vanilla-server
rm -rf "$dir" && mkdir -p "$dir/mods"
cp "$WS/$JAR" "$dir/mods/"
curl -sfL -o "$dir/mods/fabric-api-$FAPI.jar" "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$FAPI/fabric-api-$FAPI.jar" || { echo "[vanilla] fabric-api download failed"; exit 2; }
curl -sfL -o "$dir/server.jar" "https://meta.fabricmc.net/v2/versions/loader/$MC/$LOADER/$INSTALLER/server/jar" || { echo "[vanilla] server launcher download failed"; exit 2; }
echo "eula=true" > "$dir/eula.txt"
printf 'online-mode=false\nenforce-secure-profile=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nmax-tick-time=-1\nsync-chunk-writes=false\nview-distance=6\nsimulation-distance=6\nserver-port=25565\nwhite-list=false\nenforce-whitelist=false\nmotd=vanilla probe\n' > "$dir/server.properties"

cd "$dir" || exit 2
rm -f in exit.code
mkfifo in
# 2026-11-11 VFILM=true (묶음 키 vfilm): 장면 없이 3D 효과 엔진 시범 룬 영상만 (VanillaProbe.film — 클라이언트가 틱마다 사진 → films.py)
FILMS_FLAG=""
[ "${VFILM:-}" = "true" ] && FILMS_FLAG="-Dmmorpg.probe.films=true"
(java -Xmx3G -Dmmorpg.probe=true $FILMS_FLAG -jar server.jar nogui < in > out.log 2>&1; echo $? > exit.code) &
exec 3>in
t=0; up=0
while [ $t -lt 480 ]; do
  if grep -q 'Done (' out.log 2>/dev/null; then up=1; break; fi
  [ -f exit.code ] && break
  sleep 1; t=$((t + 1))
done
cd "$WS" || exit 2
echo "[vanilla] server up=$up after ${t}s"
grep -n -E "\[vanilla\]|polymer|Polymer|AutoHost|auto-host" "$dir/out.log" | head -n 80

code=1
if [ $up -eq 1 ]; then
  # 26.3 전용 서버는 허용 목록이 켜진 채 시작함 (CI 42375154420 — "You are not white-listed") — 설정 파일에 더해 콘솔로도 끔
  echo "whitelist off" >&3
  # 리소스 팩을 다 만들 때까지 (AutoHost 가 접속을 기다리게 하지만 넉넉히)
  sleep 20
  mkdir -p run-probe
  printf 'onboardAccessibility:false\nskipMultiplayerWarning:true\njoinedFirstServer:true\ntutorialStep:none\npauseOnLostFocus:false\nrenderDistance:6\nsimulationDistance:6\nguiScale:2\nnarrator:0\nsoundCategory_master:0.0\n' > run-probe/options.txt
  set +e
  timeout 1200 xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew probeClient --stacktrace --console=plain > ci-out/vanilla-client.txt 2>&1
  pcode=$?
  echo "[vanilla] probe client exit $pcode"
  grep -h "\[vanilla-client\]" ci-out/vanilla-client.txt | head -n 60
  grep -h "\[vanilla-probe\] check\|\[vanilla-probe\] skills" "$dir/out.log" | sed 's/^.*\[vanilla-probe\] /  /' | head -n 80
  if grep -q "\[vanilla-probe\] done" "$dir/out.log" && grep -q "\[vanilla-client\] done" ci-out/vanilla-client.txt \
      && ! grep -q "\[vanilla-probe\] check fail" "$dir/out.log"; then code=0; fi
fi

echo "stop" >&3
for _ in $(seq 1 90); do [ -f "$dir/exit.code" ] && break; sleep 1; done
exec 3>&-
[ -f "$dir/exit.code" ] || { pkill -f "jar server.jar nogui" || true; sleep 2; }

# 만든 리소스 팩 살피기 (Polymer — polymer/resource_pack.zip): 크기 · 블록 상태 덮어쓰기 · 직업 아이콘 모델
pack="$dir/polymer/resource_pack.zip"
if [ -f "$pack" ]; then
  echo "----- [vanilla] pack $(stat -c %s "$pack") bytes, $(unzip -Z1 "$pack" | wc -l) files"
  unzip -Z1 "$pack" | grep -E "blockstates/|items/vjob/" | head -n 16
  echo "--- note_block.json (앞)"
  unzip -p "$pack" assets/minecraft/blockstates/note_block.json 2>/dev/null | head -c 600; echo
fi
cp "$dir/out.log" ci-out/vanilla-server.log 2>/dev/null || true
cp run-probe/logs/latest.log ci-out/vanilla-client-latest.log 2>/dev/null || true
for f in run-probe/screenshots/probe_*.png; do [ -f "$f" ] && cp "$f" "ci-out/shots/vanilla_$(basename "$f" .png | sed 's/^probe_//').png"; done
# 영상 (VFILM): 틱마다 찍은 사진 → mp4 (ci/films.py — ci-out/films/vanilla_<계열>_<등급>.mp4 · .json)
if [ -d run-probe/films ]; then
  sudo apt-get install -y -qq ffmpeg > /dev/null 2>&1 || true
  python3 ci/films.py run-probe/films ci-out/films "" | tee ci-out/films-vanilla.txt || true
fi
ls -la ci-out/shots | grep vanilla || true
echo "----- [vanilla] server errors"
grep -n -E "Exception|Caused by|/ERROR\]|/FATAL\]|\[vanilla-probe\]" ci-out/vanilla-server.log | head -n 80
echo "----- [vanilla] client errors"
grep -n -E "Exception|Caused by|/ERROR\]|/FATAL\]|disconnect|Disconnect" ci-out/vanilla-client-latest.log 2>/dev/null | head -n 60
echo "[vanilla] result $code"
exit $code
