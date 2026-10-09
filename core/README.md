# MMO RPG 규칙 코어 (Java 21)

마인크래프트 베드락 애드온 (MMO RPG 시스템 — 소스의 `RPG_BP/scripts`) 을 Java Edition (Fabric) 모드로 옮기기 위한 첫 단계로,
애드온의 밸런스 데이터와 순수 계산 함수를 마인크래프트와 무관한 Java 21 패키지 `mmorpg.core` 로 옮긴 것입니다.
외부 라이브러리는 쓰지 않으며 `javac --release 21` 만으로 빌드됩니다.

## 1. 무엇이고 무엇이 아닌가

- **이 코어가 하는 일**: config.js 의 상수를 `rules.json` 으로 내보내 읽고, 그 위에서 애드온과 같은 계산을 합니다.
  무기 · 갑옷 · 룬 아이템 ID 해석, 요구 스탯 · 요구 레벨, 공격력 · 치명타 · 방패 수치, 강화 보너스와 비용, 스탯 배율,
  룬 위력, 최대 체력과 체력 변화, 결투 피해, 제작 재료, 아이템 설명 글자 (§ 색 코드 포함) 등입니다.
  같은 입력이면 애드온의 JavaScript 와 같은 double 값 · 같은 글자를 냅니다 (5절의 parity 시험으로 확인).
- **하지 않는 일**: 마인크래프트와 닿는 부분은 전혀 없습니다 (6절). Fabric 모드가 아니며, 애드온의 JS 를 실행하지도
  않습니다. Node 는 빌드 · 검증 때만 씁니다.

## 2. 폴더 구조

```
java_mod/
├─ check-core.sh                 빌드 · 검증 한 번에 (rules → parity → javac → ParityTest)
├─ settings.gradle               (선택) Gradle 로 빌드할 때. Fabric 프로젝트를 여기에 더할 예정
├─ .gitignore                    core/build, parity.json (매번 생성) 제외
├─ core/
│  ├─ build.gradle               (선택) java-library, :core:parity 작업
│  └─ src/
│     ├─ main/java/mmorpg/core/  규칙 코어 (17개 파일)
│     ├─ main/resources/mmorpg/rules.json   config.js 상수 71개 (생성물, 저장소에 둠)
│     ├─ test/java/mmorpg/core/ParityTest.java
│     └─ test/resources/parity.json         비교 케이스 (생성물, 약 4 MB)
└─ tools/
   ├─ export_rules.mjs           config.js → rules.json
   ├─ parity_dump.mjs            진짜 JS 함수 실행 → parity.json
   ├─ parity_hooks.mjs           export 되지 않은 runes.js 함수 둘을 덤프에서 부르게 하는 Node 모듈 훅 (메모리에서만)
   ├─ mutation_check.mjs         변이 검사 (Java 사본을 일부러 틀리게 바꿔 ParityTest 가 잡는지)
   └─ jsenv/                     Node 실행 환경
      ├─ package.json, node_modules/@minecraft/{server,server-ui}   애드온 tests/sim 의 모의 엔진 사본
      └─ scripts/                애드온 RPG_BP/scripts 사본 (./check-core.sh --sync 로 다시 복사)
```

`core/src/main/java/mmorpg/core` 의 파일:

| 파일 | 내용 |
|---|---|
| `Json` | JSON 파서 · 출력. 객체는 `LinkedHashMap` (JS 속성 순서: 정수 키 먼저, 나머지는 적힌 순서), 배열은 `List`, 숫자는 `Double`. 문법에 어긋나면 줄 · 칸을 적은 `Json.ParseException` |
| `Js` | Java 와 다른 JS 동작: 숫자 → 글자 (`String(x)`, 가장 짧은 자릿수), `Math.round`, `toFixed`, 참 · 거짓 판단, UTF-8 길이 |
| `Obj` | JSON 객체 읽기: `num(k)` (꼭 있어야 함, 없으면 경로를 적어 예외), `numOr(k, x)` = `k ?? x`, `numOrNull(k)` = `?.k` 등 |
| `Rules` | 클래스패스의 `/mmorpg/rules.json` 을 처음 쓸 때 한 번 읽고 읽기 전용으로 얼림. `obj` · `num` · `numbers` · `strings` · `grades` · `grade` · `root` |
| `Grade`, `Weapon` | 등급 · 무기 레코드 |
| `Stats`, `Hp` | 스탯 (`STAT_KEYS`, `damagePerPoint`, 장비 · 버프를 더한 스탯), 체력 계산 |
| `Weapons`, `Combat`, `Enhance`, `Skills` | 무기 규칙 · ID · 설명, 전투 배율, 강화, 초월 무기 스킬 |
| `Armor`, `Runes`, `Crafting`, `Mobs`, `Pvp` | 갑옷, 룬, 보스 무기 제작, 몬스터 · 보스 ID, 결투 피해 |

## 3. config.js → rules.json → Java

`tools/export_rules.mjs` 가 config.js 를 불러와 export 상수 가운데 JSON 으로 옮길 수 있는 것 (지금은 71개 전부) 을
`rules.json` 에 씁니다. 함수 22개는 Java 로 옮겼으므로 넣지 않습니다.

- 최상위 키 순서는 config.js 에 선언된 순서, 안쪽 키 순서는 JS 객체 순서 (`JSON.stringify` 와 같음) 입니다.
  `Json` 이 같은 순서를 지키므로 `Object.entries` 를 도는 계산 (계수 더하기, 설명 줄 순서) 이 Java 에서도 같은 순서로 돕니다.
- 값은 모듈을 불러온 뒤의 상태입니다. config.js 맨 아래에서 `BOSS_MATERIALS[*].lore` 에 "대장장이 보스 무기 제작" 줄을
  덧붙이므로 rules.json 의 lore 에는 그 줄이 들어 있습니다.
- JSON 으로 옮길 수 없는 값 (함수 · `undefined` · `NaN` · `Infinity`) 이 든 상수가 생기면 그 상수는 빼고 알립니다.
- Java 의 각 클래스는 필요한 표를 `Rules.get().obj("TYPES")` 처럼 읽고, JS 의 `t?.stat ?? "str"` 는
  `t == null ? "str" : t.strOr("stat", "str")` 처럼 옮겼습니다.

함수 대응 (JS 이름을 되도록 그대로 씀. "순수 부분" 은 플레이어 대신 그 함수가 읽는 값을 인자로 받습니다):

| JS | Java |
|---|---|
| config.js `damagePerPoint` | `Stats.damagePerPoint` (`Stats.STAT_KEYS`) |
| config.js `isValidWeapon` · `nearestGradeLevel` · `gradeLevelsText` · `reqUnit` · `splitReq` · `requiredStat` · `requiredStats` · `damageScaling` | `Weapons` 의 같은 이름 |
| config.js `enhanceBonus` · `enhanceCost` | `Enhance.enhanceBonus` · `Enhance.enhanceCost` |
| config.js `skillTotalMult` · `tyrfingHpFactor` | `Skills` 의 같은 이름 (`Skills.weaponSkills` = `SKILLS[type]`) |
| config.js `runeId` · `armorId` | `Runes.runeId` · `Armor.armorId` |
| config.js `craftMaterials` · `craftUseLines` | `Crafting` 의 같은 이름 |
| config.js `monsterId` · `bossId` · `bossUnitKeys` · `unitHomeRadius` · `altarId` | `Mobs` 의 같은 이름 |
| weapons.js `parseWeapon` · `weaponFrom` · `currentWeapon` · `shopPrice` · `reqsText` · `needsLevel` · `weaponLack` · `levelNote` · `weaponInfo` · `skillLines` · `enhancedName` · `round1` · `pct` | `Weapons` 의 같은 이름 (`Weapons.weaponId` = ID 만들기) |
| weapons.js `enhanceOf(item)` | `Enhance.enhanceOf(저장된 값)` (순수 부분) |
| smith.js `nextLevelOf` | `Weapons.nextLevelOf` |
| combat.js `isMeleeWeapon` · `statMultiplier` · `powerMultiplier` · `critChanceOf` · `reqText` | `Combat` 의 같은 이름 (순수 부분: 스탯 맵 · 강화 단계 · 훅이 더한 치명타 · 요구 레벨을 인자로) |
| stats.js `getStatsCached` · `reqLevelOf` · `checkLevelUp` | `Stats.effectiveStats` · `Stats.reqLevelOf` · `Stats.checkLevelUp` (순수 부분) |
| hp.js `maxHpOf` · `levelHpOf` · `hpNumber` · `refreshMaxHp` · `payHp` · `healBy` · `onPlayerHealBefore` · `revive` · `saveHp` | `Hp.maxHpOf` · `levelHpOf` · `hpNumber` · `refreshMaxHp` · `payHp` · `healBy` · `vanillaHeal` · `reviveHp` · `saveRatio` (순수 부분) |
| duel.js `duelDamage` | `Pvp.duelDamage` (받는 사람의 최대 체력을 인자로) |
| armor.js `parseArmor` · `armorFrom` · `bonusText` · `armorReqText` · `armorInfo` · `armorLack` · `armorLevelFor` · `armorPrice` · `setPrice` | `Armor` 의 같은 이름 (`armorLack` 은 순수 부분) |
| runes.js `parseRune` · `runeLevelBase` · `mixBonus` · `runeStat` · `runeStatLabel` · `runePower` · `supportScale` · `runeTotalMult` · `gradeWeights` · `rollRune` · `runeLore` | `Runes` 의 같은 이름 (스탯은 맵으로, `rollRune` 의 난수는 `DoubleSupplier` 로) |
| runesmith.js `grindValue` | `Runes.grindValue` |

## 4. 다시 빌드하고 검사하는 법

필요한 것: JDK 21 (`javac`, `java`, `jar`), Node (22.22 에서 확인), bash 와 기본 유틸리티. 네트워크는 쓰지 않습니다.

```sh
./check-core.sh            # tools/jsenv/scripts 사본으로: rules.json → parity.json → javac → ParityTest
./check-core.sh --sync     # 먼저 애드온 스크립트를 다시 복사 (RPG_ADDON=애드온 소스 폴더, 기본 ../rpg_addon)
node tools/mutation_check.mjs   # 변이 검사 (check-core.sh 뒤에, 약 1분)
```

- 하나라도 다르면 `ParityTest` 가 함수별 개수와 처음 몇 개의 차이를 보이고 상태 1 로 끝나며, `check-core.sh` 도 실패합니다.
- 결과물: `core/build/mmorpg-core.jar` (rules.json 포함), 클래스는 `core/build/javac/`.
- 애드온의 config.js 나 공식이 바뀌면 `./check-core.sh --sync` 를 돌립니다. 실패한 함수가 Java 쪽에서 고칠 곳입니다.
- 선택: Gradle 8.14 에서 `gradle --offline :core:parity` 도 됩니다 (의존성 없음). rules.json · parity.json 생성은 Node 가 필요하므로 `check-core.sh` 가 맡습니다.

## 5. 검증 방식과 최근 결과

`tools/parity_dump.mjs` 가 애드온의 진짜 함수를 입력 격자에 돌려 `{fn, args, expected}` (JS 가 예외를 던지면 `{fn, args, error}`) 를 씁니다.

- 입력: 레벨 −5 … 50 (소수 · NaN · Infinity 포함), 모든 등급과 없는 등급, 마검을 포함한 모든 무기 종류와 없는 종류,
  스탯 0 … 150 (모두 0 · 한 스탯 · 같은 값 · 마력 2 : 힘 1 · 요구치 ±1 등), 강화 −3 … 13, 룬 16계열 × 5등급,
  갑옷 5종류 × 레벨 9개 × 등급 3개 × 부위 4개, 잘못된 ID (`rpg:sword_l040_common`, 끝에 줄바꿈 등), 무작위 비트의 double.
  무작위 표본은 시드가 고정되어 있어 같은 config.js 면 같은 파일이 나옵니다.
- 플레이어를 받는 함수는 모의 엔진의 `FakePlayer` 에 스코어보드 스탯 · 갑옷 보너스 · 룬 버프 · 체력 상태를 넣고 부르며,
  케이스에는 그 함수가 실제로 읽은 값 (`getStatsCached` 결과 등) 을 적습니다.
- export 되지 않은 `supportScale` · `utf8Length` 는 `parity_hooks.mjs` 가 모듈을 불러올 때 export 줄을 덧붙여 부릅니다 (파일은 바꾸지 않음).
- `js.String` · `js.Math.round` · `js.toFixed` · `JSON.parse` 는 애드온 함수가 아니라 Java 의 `Js` · `Json` 이 흉내 내는 JS 엔진 동작을 직접 비교합니다.
- 비교: 글자 · 참거짓 · 목록 · 객체 (키 순서까지) 와 정수 값은 그대로, 그 밖의 숫자는 |a − b| ≤ 1e-9 × max(1, |b|).

**최근 결과 (2026-10-09)**: 케이스 28,530개, 함수 81개, 모두 통과 (`RESULT: PASS (28530/28530 ...)`). JS 가 예외를 던지는 케이스 78개는 Java 도 예외를 던집니다.

| 모듈 | 함수 | 케이스 |
|---|---:|---:|
| config.js | 22 | 5,957 |
| runes.js · runesmith.js | 13 | 5,650 |
| weapons.js · smith.js | 15 | 4,988 |
| combat.js | 5 | 3,947 |
| JS 엔진 (`String` · `Math.round` · `toFixed` · `JSON.parse`) | 4 | 2,982 |
| hp.js | 9 | 1,985 |
| armor.js | 9 | 1,921 |
| stats.js · duel.js | 4 | 1,100 |

**변이 검사** (`tools/mutation_check.mjs`: `core/build/mutation` 의 사본만 바꿈): 11개 모두 ParityTest 가 실패했고, 바꾸지 않은 사본은 통과했습니다.

| 종류 | 바꾼 것 | 실패한 케이스 |
|---|---|---:|
| 상수 읽기 | `maxHpOf` 가 `HP_PER_VIT` 대신 `HP_PER_LEVEL` | 172 |
| 상수 읽기 | `runePower` 가 `RUNE.perPoint` 대신 `RUNE.offStat` | 721 |
| 엡실론 | `splitReq` 의 `- 1e-9` 를 뺌 | 20 |
| 엡실론 | `payHp` 의 `1 - 1e-6` 을 `1` 로 | 23 |
| 반올림 | `Math.round` 의 .5 를 아래로 | 590 |
| 반올림 | `critMult` 의 `Math.round` 를 `Math.floor` 로 | 204 |
| 분기 | `needsLevel` 의 `> 0` 을 `>= 0` 으로 | 183 |
| 분기 | `runeStat` 의 다른 스탯 비교 `>` 를 `>=` 로 | 340 |
| 분기 | `isValidWeapon` 이 `TYPE_ONLY` 를 보지 않음 | 50 |
| 순서 | JSON 객체를 JS 속성 순서로 바꾸지 않음 | 2 |
| 글자 | 숫자 글자의 `0.000001` 경계 | 18 |

검증의 한계:

- 기준은 Node (V8) 입니다. 게임 안의 엔진 (QuickJS) 도 사칙연산 · `Math.round` · 숫자 글자 규칙은 명세대로라 같다고 보지만, 게임 안에서 직접 비교한 것은 아닙니다.
- `Math.hypot` (`unitHomeRadius`) 은 JS 명세상 구현마다 마지막 자리가 다를 수 있어 Java 의 `Math.hypot` 을 쓰고 허용 오차로 비교합니다.
- `splitReq` 의 `- 1e-9` 는 지금의 요구 배율 (1, 2/3, 1/3) 로는 결과를 바꾸지 않습니다. 그래서 100 × 0.07 처럼 곱이 정수를 조금 넘는 입력을 따로 넣어 검증합니다.
  `armorPrice` · `enhanceCost` 의 `- 1e-9` 는 인자로 바꿀 수 없는 config 값에만 걸리고 지금 수치로는 결과가 같아 parity 로는 검증되지 않습니다 (코드는 그대로 옮김).
- JS 객체의 프로토타입 이름 (`constructor` 등) 을 무기 종류 · 등급으로 넣는 경우는 다루지 않습니다 (아이템 ID 정규식이 막는 입력).
- 모의 엔진은 애드온 `tests/sim` 의 사본이며, 이 위치에서는 `RPG_BP` 의 엔티티 · 블록 정의를 찾지 못합니다. 여기서 부르는 순수 함수는 그 정의를 쓰지 않습니다.

## 6. 일부러 넣지 않은 것

- **마인크래프트가 필요한 것 전부**: 아이템 · 블록 · 엔티티 등록과 아이템 스택 (lore · 이름표 · 동적 속성 쓰기), 파티클 · 소리 · 화면 연출
  (fx, slash, ultfx, 피해 숫자), 창 (상점 · 대장장이 · 룬술사 · 스탯 · 엔더 상자), 이벤트 (피해 · 회복 · 사망 · 입력), 체력 훅
  (바닐라 체력 거울, HUD 제목 글자, 사망 처리), 스킬 · 룬 · 몬스터 · 보스의 실제 동작과 판정, 퀘스트 · NPC · 명령어, 저장 (스코어보드 · 동적 속성).
- **순수 부분만 옮긴 함수** (3절 표의 "순수 부분"): 플레이어 · 아이템 · 이벤트에서 값을 읽는 부분은 Fabric 계층이 맡습니다.
  예: `powerMultiplier` 의 손에 든 무기 강화 단계 확인 (`heldEnhance`), `critChanceOf` 의 훅 목록, `payHp` 의 생존 · 모험 모드 확인,
  `duelDamage` 의 체력 상태 조회.
- **옮기지 않은 순수 함수**: 베드락 전용인 HUD 글자 (`hp.js hudText`) 와 lore 50바이트 자르기 (`weapons.js fitLoreLine` — Java Edition 에는 그 제한이 없음),
  이벤트 짝짓기와 얽힌 받는 피해 계산 (`onPlayerHurtAfter`), 몬스터 · 보스 · 전리품의 표시 · 경험치 함수 (monsters.js, bosskit.js, loot.js, names.js),
  스킬 · 룬 · 보스 패턴 안의 수치 계산 (연출 · 판정 코드와 함께 있음).

## 7. 다음 단계: Fabric 계층

Fabric 모드는 Minecraft 와 Fabric (Loom, 매핑, Fabric API) 을 내려받아야 빌드됩니다. 이 컨테이너는 Maven Central · Fabric · Mojang 서버가
막혀 있으므로 다음 환경이 필요합니다: JDK 21 이 있는 PC, 또는 GitHub Actions (`actions/setup-java` 21 + `actions/setup-node`).

1. `settings.gradle` 에 Fabric 프로젝트 (예: `fabric/`) 를 더하고 Loom 을 적용, `implementation project(':core')` 와 `include` 로 코어를 모드 jar 에 넣습니다.
2. 아이템 등록: rules.json 의 `TYPE_ORDER` × `GRADE_LEVELS` (+ `TYPE_ONLY`), 갑옷 표, 룬 80종을 `Weapons.weaponId` · `Armor.armorId` · `Runes.runeId` 와 같은 ID 로 등록합니다.
3. 툴팁: `weaponInfo` · `armorInfo` · `runeLore` 의 글자를 그대로 쓰고 § 코드는 Java Edition 의 텍스트 서식으로 바꿉니다.
   베드락 전용 색 코드 (`§u` · `§v` 등, 고귀 · 영웅 등급) 는 Java Edition 에 없으므로 대응 색을 정해야 합니다.
4. 플레이어 데이터 (찍은 스탯 · 최고 레벨) 를 저장하고 `Stats.effectiveStats` → `Hp.maxHpOf`, 피해 이벤트에서 `Combat.powerMultiplier` · `critChanceOf` · `weaponLack` 을 씁니다.
5. 프로젝트 지시대로 스킬 피해 숫자 표시와 연출은 클라이언트 쪽에서 새로 만듭니다 (연출 수치는 rules.json 의 `SLASH` · `WAND` · `ULT_FX` 등).
6. CI 에서 `./check-core.sh` 로 parity 를 먼저 통과시킨 뒤 Gradle 빌드를 돌립니다.
