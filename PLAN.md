# 자바 에디션 (Fabric) 이식 계획

작성: 2026-10-09 · 갱신: 2026-10-11 (5단계) · 기준 애드온: v2.27.0 (스크립트 약 44,700줄)
저장소: https://github.com/hebi-06/minecraft (main 에 올리면 GitHub Actions 가 빌드 · 시험 · 릴리스)

## 1. 현재 상태

| 단계 | 상태 | 내용 |
|---|---|---|
| 0. 규칙 코어 | 완료 | 밸런스 데이터 (`rules.json`) 와 순수 계산 함수 81개를 Java 로. 애드온 JS 와 같은 입력 28,530개에서 같은 값 (매 빌드 확인) |
| 빌드 환경 | 완료 | GitHub Actions: 버전 확인 → 코어 대조 → 컴파일 → 게임 시험 (서버) → 화면 시험 (클라이언트, 확인 항목 + 사진 — 사진첩 묶음마다 동시에, `ci/gallery_groups.py`) → 모두 통과하면 릴리스 `latest` |
| 1. 기본 | 완료 (26.3) | 스탯 · 레벨 포인트 · 스탯 창 · RPG 체력 · 체력 바 HUD · 무기 151종 (요구 조건 · 평타 공식 · 치명타 · 활 · 완드 · 방패) · 피해 숫자 |
| 2. 무기 연출 · 3D 모델 | 완료 (26.3) | 무기 3D 모델 31종 · 활 / 완드 이펙트 그림 · 근접 참격 22종 그림 · 무기 빛 알갱이 13종 · 화면 흔들림 (아래 3절) |
| 3. 초월 무기 스킬 · 룬 | 완료 (26.3) | 초월 무기 스킬 14종 · 룬 80종 · 룬 뽑기 · 초월 룬 하늘빛 / 번쩍임 (아래 3절) |
| 4. 갑옷 · 상점 · NPC · 퀘스트 | 완료 (26.3) | 갑옷 120 (애드온 3D 모델) · 재료 · 서버 창 4종 · 상점 · 대장장이 (강화 · 레벨 올리기 · 보스 무기 제작) · 룬술사 · NPC · 퀘스트 (메인 31단계 · 반복 · 왕실 · 전설 · 직접 만든 것) · 길 안내 · 훈련용 허수아비 · NPC / 퀘스트 설정 창 (아래 3절) |
| 5. 몬스터 · 보스 · 제단 · 세계수 | 완료 (26.3) | 애드온 엔티티 26종 (모델 · 애니메이션 · 렌더 컨트롤러 · Molang) · 몬스터 6종 · 사냥터 · 보스 엔진 + 보스 9종 · 제단 9종 · 신전 9종 (설계 · 짓기 · 보호) · 세계수 (아래 3절) |
| 6 | 다음 | 아래 4절 |

대상: 마인크래프트 자바 **26.3** · Fabric Loader 0.19.5 · Fabric API 0.162.0+26.3 · Loom 1.18 · Java 25 (gradle.properties).
1.21.1 판 (1단계까지): 브랜치 `mc-1.21.1` → 릴리스 `release-mc-1.21.1` (더 고치지 않음).

### 26.3 으로 옮기며 바뀐 것 (다음 작업 때 참고)

- 난독화 없는 공식 이름 (매핑 없음): `Identifier` · `EntityTypes` · `hurtServer` · `snapTo` · `entityTags()` 등. 시그니처 목록은 `api-dump` 브랜치 (`ci/api-dump/classes.txt` 에 클래스를 넣고 올리면 다시 만듦)
- 화면: `GuiGraphicsExtractor` · `RenderPipelines` · `extractRenderState`, 바닐라 체력 줄은 Fabric HUD API 로 교체 (믹스인 없음)
- 입력: SDL — 키 · 마우스 번호는 `InputConstants` 상수 (`KEY_K`, `MOUSE_BUTTON_LEFT` = 왼쪽, GLFW 값 0 이 아님)
- 피해: 연속 피해 막기는 `LivingEntity.damageCooldownTime`, 접속 직후 플레이어는 클라이언트가 "다 불러옴" 을 알릴 때까지 (또는 시간 초과) 피해 없음
- 게임 시험의 가짜 플레이어: `gameMode()` 가 크리에이티브를 돌려줌 → RPG 모드 판정은 `sp.gameMode.getGameModeForPlayer()`
- CI 화면 시험: xvfb 에서 OpenGL 창이 안 만들어져 (GLX visual) Vulkan (lavapipe, `mesa-vulkan-drivers`) 으로 그림
- 아이템 모델: 상자 (`elements`) 에 `light_emission` (빛나는 칸), 상자 범위 -16 ~ 32 · display 이동 최대 5블록 · 배율 최대 4. 손 / 인벤토리 구분은 아이템 정의의 `minecraft:select` + `minecraft:display_context`
- 1인칭 손은 `FirstPersonHandsAndItemsRenderer` (손 자리 0.56, -0.52, -0.72 그대로), 3인칭은 `ItemInHandLayer.submitArmWithItem` (팔 → X -90° · Y 180° · (1, 2, -10)/16)
- 파티클: `SingleQuadParticle.extract(QuadParticleRenderState, Camera, float)` 로 사각형을 직접 넣음 (회전 · UV · 색 · 밝기), 공급자는 Fabric `ParticleProviderRegistry` + `FabricSpriteSet`
- 휘두르기: 공격 패킷 (`ServerboundAttackPacket` → `Player.attack`) 과 별도로 휘두르기 패킷 (`ServerboundPunchPacket` → `handlePunch`) — 허공 참격은 handlePunch 믹스인
- 카메라: `Camera.update` → `alignWithEntity(partialTick)` 가 보는 방향을 정함 (화면 흔들림은 그 뒤에 더함)
- 갑옷 그리기: Fabric `ArmorRenderer` + `submitTransformCopyingModel` (사람 모델의 head · body · right_arm … 움직임을 같은 이름의 부분에 복사). 갑옷 아이템은 `equippable` (자산 없이) — Fabric 이 렌더 상태에 넣어 줌
- 마네킹 (`Mannequin`): 스킨 · 설명 줄 · 밀리지 않음의 설정 함수가 private → 접근 믹스인 (`MannequinAccessor`), 무적은 `setPermanentlyInvulnerable`
- 창 위젯: `EditBox` · `CycleButton.builder` · `AbstractSliderButton` · `Button.builder` (그리기는 `extractWidgetRenderState`), 배경은 `Screen.extractBackground`
- 파티클 화면 밖 자르기: `QuadParticleGroup.extractRenderState` 가 가운데 점 (`Frustum.pointInFrustum`) 으로만 봄 → 큰 애드온 파티클은 크기로 (`QuadParticleGroupMixin`)
- 아이템 구성 요소: `DataComponentType.builder().persistent(codec).networkSynchronized(streamCodec)` (무기 강화 단계 `mmorpg:enhance`)
- 세계 · 엔티티 저장: Fabric Data Attachment (엔티티 · 플레이어 (`copyOnDeath`) · 오버월드 `ServerLevel`) — 베드락 동적 속성 대신 `Props`
- 엔티티 데이터: `EntityDataSerializers` 에 NBT 형식이 없음 → 베드락 속성 · 튼 동작은 JSON 글자 둘 (`BedrockMob`). 렌더러는 `LivingEntityRenderer<엔티티, 상태, 모델>` + `extractRenderState` (상태 객체에 뼈 회전 · 위치 · 크기를 담아 `setupAnim` 에서 씀)
- 빛나는 칸: `RenderTypes.entityTranslucentEmissive` + 밝기 0xF000F0, 덧칠 (렌더 컨트롤러 overlay_color) 은 흰 텍스처 + ARGB 색으로 한 번 더
- 청크 붙잡기: `TicketType.PORTAL` 표 (반경 2 = 그 청크까지 엔티티 틱), 100틱마다 다시 붙임 (`boss.Ticking`)
- 게임 시험 묶음: 시험 환경 (`data/<ns>/test_environment/*.json` — `minecraft:all_of`) 이 같은 시험끼리만 한 묶음으로 동시에 돎 (`@GameTest(environment = …)`)
- 제목: `Hud.extractTitle` (제목 4배 · 부제목 2배) — Fabric `VanillaHudElements.TITLE_AND_SUBTITLE` 을 감싸 줄임 (`TitleFit`)

## 2. 시험

| 시험 | 방법 | 내용 |
|---|---|---|
| 규칙 코어 대조 | `./check-core.sh` (Node + JDK) | 애드온 JS 함수와 Java 포트의 결과 28,530개 비교 |
| 게임 시험 153개 | `./gradlew runGameTest` (`build` 가 함께 돌림 — 서버 + 가짜 플레이어 · 좀비 · 허스크 과녁) | 1단계 16개 (시작 포인트 · 레벨업 · 최대 체력 · 회복 환산 · 방패 · 독 · 토템 · 체력 비율 저장 · 평타 공식 · 치명타 · 요구 조건 · 활 · 완드 · 피해 숫자 · 무기 등록 · 명령어) + 스킬 7개 (재사용 대기 · 그람 피해 · 스킬 숫자 · 피해 감소 / 반사 · 속박 / 침묵 · 활 길게 누르기 입력 · 소리 대응표) + 룬 6개 (아이템 80 · 효과 80 등록 · 칸 고르면 바로 쓰기 · 뽑기권 · 위력 / 재사용 대기 / 핫바 표시 · 평타 강화) + 4단계: 갑옷 · 상점 6개 (등록 · 방어 / 보너스 · 벗겨짐 · 강화 배율 · 무기 사기 · 전리품 팔기), 대장장이 6개, 룬술사 · 허수아비 4개, 퀘스트 11개 (만든 퀘스트 4종 · 대화 장 넘김 · 목록 · 메인 이야기 처음 · 반복 의뢰 · 저장 다시 읽기 · 보스 · 전설 · /mainnpc), 길 안내 7개, 설정 창 6개 — 창 흐름은 클라이언트 없이 (`Forms.HEADLESS`) + 5단계 84개: 엔티티 3 · 제단 / 신전 7 · 보스 엔진 + 허수아비 킹 9 · 몬스터 10 · 세계수 7 · 전갈 7 · 크라켄 5 · 거대 웜 6 · 요르문간드 5 · 니드호그 5 · 스콜과 하티 5 · 이미르 6 · 수르트 9 (보스 · 몬스터 · 세계수 시험은 시험 환경 `mmorpg:<이름>` 으로 따로 도는 묶음 — 처치 훅 · 플레이어가 섞이지 않게) |
| 화면 시험 | `./gradlew runClientGameTest` (Fabric 클라이언트 게임 시험, CI 는 xvfb) | 평평한 세계에서 실제 입력으로: 확인 16항목 (스탯 동기화 · HUD · 평타 / 치명타 숫자 · 무기 빛 알갱이 수 · 1인칭 / 3인칭 자리 · 도끼 화면 흔들림 · 낮은 체력 · K 키 스탯 창 · 툴팁 · +1 클릭이 서버 스탯을 올림 · 활 · 완드 명중) + 장면 사진 → `ci-logs` 브랜치 `client-checks.txt` · `shots/` |
| 무기 사진첩 | 같은 화면 시험 (`RpgWeaponGallery`) | 디자인 31종 1인칭 · 3인칭 · 마네킹 옆 / 앞 · 밤 빛나는 칸, 활 · 완드 이펙트 틱별, 참격 7종 (평타 · 치명타 · 허공), 빛 알갱이 13종 (밤 실제 크기 · 마네킹 4배) — 사진으로 확인 |
| 갑옷 · 창 · 길 안내 사진첩 | 같은 화면 시험 (`RpgGearGallery` · `RpgFormGallery` · `RpgNavGallery`) | 갑옷 세트 5종 (3인칭 앞 · 뒤 · 마네킹 · 걸을 때 망토) · 가방 설명 · 허수아비, 상점 · NPC 대화 · 버튼 목록 · 입력창 · 설정 창을 실제 마우스 · 키로 (확인 항목 + 사진), 길 안내 화살표 방향 · 바닥 빛 · 도착 |
| 몬스터 · 신전 · 보스 · 세계수 사진첩 | 같은 화면 시험 (`RpgMobGallery` · `RpgMonsterGallery` · `RpgArenaGallery` · `RpgBossGallery` · 보스마다 `Rpg<보스>Gallery` · `RpgTreeGallery`) | 엔티티 26종 모습 · 동작, 몬스터 6종 공격 · 이름표 · 달려들기 · 반격, 신전 9종 (설계와 칸마다 같은지), 보스 9종은 진짜 신전을 짓고 제단 → 도전 창 → 소환 → 패턴마다 예고 · 판정 · 피하는 법 → 처치 · 보상까지 실제 입력으로 (확인 항목 + 사진), 세계수 자라기 · 밤 · 되돌리기 |
| 스킬 · 룬 사진첩 | 같은 화면 시험 (`RpgSkillGallery` · `RpgRuneGallery`) | 쓰러지지 않는 과녁 허스크 여섯 앞에서 스킬 14종 · 룬 80종을 실제 입력으로 (스킬 2 · 초월 룬은 3인칭으로 한 번 더): 써졌는지 · 들어간 피해를 로그로 확인 (안 써지거나 피해 룬의 피해가 0 이면 실패) + 순간 사진 |
| 배포 환경 | `ci/prod_smoke.sh` · `./gradlew prodClient` | 릴리스 jar 그대로 실제 Fabric 서버 · 클라이언트를 켜서 시작 · HUD · 스탯 창 확인 (개발 환경에서만 되는 문제를 막음) |

## 3. 베드락 기능 → Fabric 대응 (1 · 2단계)

| 애드온 (베드락) | 자바 (Fabric) |
|---|---|
| 스코어보드 스탯 · 레벨 · `rpg:hp_ratio` | 플레이어 데이터 (Fabric Data Attachment `mmorpg:player`, 죽어도 유지) |
| 가상 체력 (`hp.js` — 바닐라 체력 20 을 거울로) | 최대 체력 속성 수정자 (`mmorpg:rpg_max_hp`) — 피해는 바닐라 그대로 (방어구 · 흡수 뒤) × 방패 감소, 회복은 × 최대 체력 / 20 (믹스인) |
| 체력 바 (리소스 팩 HUD, 제목 채널) | 클라이언트 HUD (바닐라 하트 자리, 애드온 그림 그대로) |
| 무기 아이템 · 설명 | Item 151개 (`rpg:<종류>_l<레벨>_<등급>`), 설명은 코어 `weaponInfo` |
| 무기 3D 모델 31종 (attachable · geo.json · 1인칭 자세 · 빛나는 칸) | 아이템 모델 (`tools/weapon_models.py`): weapon 뼈의 상자 그대로, 손 자세는 Blockbench 베드락 미리보기 규칙으로 애드온 화면과 같은 자리가 되게 계산, 빛나는 칸 = `light_emission` 15. 인벤토리는 아이콘. 1인칭은 칼 · 단검 · 도끼 · 창 · 마검 · 완드를 손잡이 기준으로 세우고 (날 방향 거의 수직 — 애드온은 약 35° 기울어 있었음), 날이 있는 무기는 날 축 둘레로 돌려 베는 날이 앞을 향함 (2026-10-10 요청) |
| 무기 주변 효과 (attachable 의 fx 파티클 — 영웅 · 초월 13종) | 클라이언트 `WeaponAura`: 무기를 든 플레이어 · 마네킹마다 모델의 fx 자리 (1인칭 카메라 공간 · 3인칭 팔 공간, 생성기가 계산) 에서 초당 10개, 바닐라 generic_0 · flame 그림 |
| 평타 보정 (`entityHurt` before) | `Player#attack` 믹스인 (공격 충전은 자바 방식 유지: 0.2 + 0.8 × 충전²) |
| 피해 숫자 (`rpg:dmg_text` 이름표) | `text_display` 엔티티 (늘 카메라를 봄 · 밝게 · 그림자, 18틱 동안 떠오름) |
| 스탯 창 (상자 창) | 클라이언트 Screen (키 K · 스탯 책 · `/stats`), 모드 없는 클라이언트는 채팅 스탯 창 |
| 이펙트 그림 파티클 (`rpg:arrow_shot` · `rpg:magic_circle` · 참격 22종 · 스킬 그림) | 파티클 `mmorpg:fx` (`FxOptions`: 종류 · 그림 · 방향 · 속도 · 크기 · 수명 · 색 …) — 서버가 보내고 클라이언트 `FxParticle` 이 애드온 그림 (PNG 그대로, `tools/fx_textures.py`) 으로 같은 모양 · 움직임 · 사라짐 |
| 참격 (`slash.js` — 휘두르기 · 맞힘) | `Slash` (서버): 휘두르기 패킷 · 공격마다 참격, 맞힌 피해 뒤 맞힘 연출 (`DamageNumbers` 가 부름) |
| `camerashake add @s 세기 초 rotational` | `RpgNet.ShakePayload` → 클라이언트 `CameraShake` → `Camera` 믹스인 (보는 방향만 떨림, 조준은 그대로) |
| 명령어 (`/weapon` · `/points` 등) | Brigadier (`/rpg …` + 애드온과 같은 이름) |

### 3단계 (초월 무기 스킬 · 룬)

| 애드온 (베드락) | 자바 (Fabric) |
|---|---|
| 초월 무기 스킬 (`skills.js`, 손에 든 무기 누르기 · 웅크리기) | `skill/Transcend` (입력 · 재사용 대기 · 화면 하단 줄) + 무기마다 한 파일 (`Gram` · `Solas` · `Gungnir` · `Aegis` · `Artemis` · `Odin` · `Tyrfing`), 공통 도구 `Kit` · `SkillFx` (애드온 함수 이름 그대로). 활 · 완드 스킬 2 는 길게 누르기 (`Item.onUseTick` — 12틱) |
| 스킬 · 룬 그림 (파티클 119종, Molang 식) | `mmorpg:addon` 파티클: 애드온 파티클 정의를 식 그대로 옮겨 클라이언트가 계산 (`tools/addon_particles.py`) |
| 피해 막기 (`entityHurt` before · 무적 시간) | `Guards` (받는 피해 감소 · 반사), 무적 시간은 마지막으로 맞은 틱을 기억해 피함 (`LivingEntity` 믹스인) |
| 룬 아이템 (`minecraft:cooldown` 쓸기) | Item 80개 (`rpg:rune_<계열>_<등급>`) + 아이템 재사용 대기 (`ItemCooldowns` — 대기 중에는 우클릭도 막힘) |
| 룬 칸 고르기 (`playerHotbarSelectedSlotChange`, 터치) | 서버가 틱마다 고른 칸을 봄 (`RuneCast.quickTick`), `/runequick on` 일 때만 (자바에는 터치 없음) |
| 동적 속성 (`rpg:rune_quick` · `rpg:rune_pending`) | Fabric Data Attachment `mmorpg:runes` |
| 하늘빛 (`/fog push rpg:ult_*`) | `RpgNet.SkyPayload` → 클라이언트 `UltFx` → `FogRenderer.setupFog` 믹스인 (안개 거리 · 색 · 하늘 끝을 0.5초에 걸쳐 바꿈) |
| 화면 번쩍임 (`camera fade`) | `RpgNet.FlashPayload` → HUD 맨 위 색 덮개 |
| 평타에 더하는 룬 (마검 부여 · 평타 강화) | `RpgCombat` 훅 (평타 보너스 · 맞힘 · 치명타 + · 활 재사용 배율) |
| 신체 강화 (모든 스탯 +%) | `RpgStats` (찍은 스탯 × (1 + 룬 버프) — 최대 체력 · 무기 피해 · 룬 위력이 모두 이것) |
| 보호막 · 불사 (`hp.js`) | `RpgHealth` (보호막이 먼저 막음 · 버티기 훅) |

달라지는 점: 자바는 죽었다 다시 태어나면 플레이어 객체가 바뀜 (베드락은 같음) — 오래 남는 룬 상태는 UUID 로 적고 그때의 플레이어를 찾음.

### 4단계 (갑옷 · 창 · 상점 · NPC · 퀘스트)

| 애드온 (베드락) | 자바 (Fabric) |
|---|---|
| 갑옷 아이템 · attachable 3D 모델 (geo.json · 황제 망토 애니메이션) | Item 120 (`equippable` · 방어 속성) + 클라이언트 `ArmorModels`: 애드온 모델을 자바 좌표로 바꿔 (`tools/armor_items.py`) 입은 사람의 움직임을 그대로 따라 그림, 망토는 걷는 빠르기만큼 |
| 갑옷 스탯 보너스 (`setStatBonus`) · 요구 조건 (`armorTick`) | `ArmorSystem` (0.5초마다) → `RpgStats` 장비 보너스 (최대 체력은 남은 비율 그대로) |
| 서버 폼 (ActionForm · ModalForm) + 리소스 팩 UI (상자 창 · 대화창) | 창 패킷 (`ui.FormNet`) + 클라이언트 화면 (`client/form/` — 애드온 그림 그대로). 애드온의 `await` 고리는 받을 일 (콜백) 되부름 (`ui.Forms`: 응답이 오면 다음 창으로 바로 바뀜, 흐름이 끝나면 닫힘) |
| 아이템 동적 속성 (`rpg:enhance`) | 아이템 구성 요소 `mmorpg:enhance` (`gear.WeaponEnhance` — 이름 +N · 설명 · 피해) |
| 엔티티 · 플레이어 · 세계 동적 속성 (NPC 번호 · 퀘스트 정의 · 진행 · 길 안내) | `Props` (Fabric Data Attachment — JSON 글자 그대로라 애드온과 같은 모양) |
| `minecraft:npc` (NPC 엔티티 · 스킨 고르기 · 고개 돌리기) | 마네킹 (`Mannequin`): 밀리지 않음 · 무적 · 가까운 사람을 바라봄 (`Npcs`), 스킨은 설정 창 (플레이어 이름) |
| 길 안내 (제목 채널에 숨긴 글자 → hud_screen.json 화살표 칸) | `nav.NavNet` 패킷 + 클라이언트 `NavHud` (방향 · 거리는 클라이언트가 매 프레임 — 서버 왕복 없음), 바닥 빛 · 빛기둥은 그 사람에게만 보내는 파티클 |
| `rpg:training_dummy` (엔티티 · geo · wobble 애니메이션 · 이름표 여러 줄) | 자바 엔티티 `TrainingDummy` + `DummyRenderer` (`tools/dummy_model.py`), 머리 위 기록은 글자 표시 엔티티 (체력 상한 1024 없이 원래 피해 그대로 기록) |

몬스터 (`rpg:*`) 처치 · 보스 단계 퀘스트와 사냥터 · 제단 길 안내는 5단계에서 이어짐 (훅: `quest.Loot` · `QuestSystem.onBossKilled` · `nav.Nav.zones / altars / bossFights`).

### 5단계 (몬스터 · 보스 · 제단 · 세계수)

| 애드온 (베드락) | 자바 (Fabric) |
|---|---|
| 엔티티 정의 (행동 팩 entities · 리소스 팩 entity / geo / animations / render_controllers · 텍스처) | `tools/bedrock_entities.py` → `mmorpg/entities.json` (수치 · 속성 · 사건 · 행동) + `assets/mmorpg/bedrock/entities.json` (모델 · 동작 · 렌더 컨트롤러). 서버 `mob.BedrockMob` (속성 · 동작 동기화 = 글자 엔티티 데이터 둘, 사건 → 이동 빠르기, 보스 막대, 피해 · 효과 면역), 클라이언트 `bedrock.BedrockRenderer` (geo → ModelPart, 애니메이션 · 렌더 컨트롤러는 Molang 식 그대로 — 규칙 코어 `Molang` · `bedrock.*`), 빛나는 칸은 `_emissive` 텍스처 |
| `minecraft:behavior.*` (근접 공격 · 대상 고르기 · 돌아다니기 · 집 반경) | `mob.BedrockGoals` (바닐라 목표로), 닉스 물속 길 찾기 (`navigation.generic` can_swim → 물뭍 길 찾기 · 헤엄 이동) |
| `minecraft:health` 큰 값 (보스 90,000) | 최대 체력 속성 상한 1,024 → 1,000,000 (`RangedAttributeAccessor`) |
| 몬스터 스크립트 (monsters.js · huntzone.js) | `mob.Monsters` · `mob.HuntZones` (이름표 = 몬스터에 태운 글자 엔티티), 명령어 `MonsterCommands` |
| 보스 엔진 (bosses.js · bosskit.js) + 보스 9종 (boss_*.js) | `boss.Bosses` (틀 · 평타 · 원거리 견제 · 보상 · 재사용 대기) · `boss.BossKit` (예고 · 피해 · 소리 · 안개) + 보스마다 `BossModule` (패턴 함수 이름 그대로). 플레이어 피해는 `hurtServer` 안에서 바로 (`absorb`), 사람마다 무적 시간 |
| `/fog` (보스 안개) · `camera fade` · 제목 | 하늘빛 패킷 (3단계 초월 룬과 같음) · 번쩍임 패킷 · 바닐라 제목 (긴 제목은 클라이언트 `TitleFit` 이 화면에 맞게 줄임) |
| 제단 블록 9종 · 신전 설계 (arena.js) · 틱킹 영역 | `boss.AltarBlocks` · `boss.ArenaThemes` (칸마다 같은 설계) · `boss.ArenaBuild` (틱마다 1,500 블록, 이웃을 건드리지 않음) · `boss.ArenaGuard` (필드 보호) · `boss.Ticking` (청크 표로 붙잡음) |
| 플레이어 방어 (베드락: 방어 1 = 4%, 최대 80%) | `LivingEntity.getDamageAfterArmorAbsorb` 믹스인 (플레이어만 — 애드온 수치가 이 식에 맞춰져 있음) |
| 세계수 (worldtree.js — system.runJob) | `tree.WorldTree` (설계 계산은 일꾼 스레드, 자라기 · 되돌리기는 틱마다 나눠서) · `TreePlan` (칸마다 같은 설계) · `TreeLog` (원래 블록 기록) |

자바 판 차이: 위 표 + 보스마다 `Boss*.java` 머리말의 "자바에서 달라진 점" (틀 줄이기 · 이름표 · 블록 상태 이름 등 — 보이는 결과는 같음).

## 4. 다음 단계 (각 단계 = 빌드 · 게임 시험 · 화면 시험 통과 후 전달)

6. 결투 (PvP) · 탈것 · 엔더 상자 · 나머지

## 5. 참고

- 자바 에디션은 PC 용. 안드로이드는 비공식 런처로 가능하지만 성능 제약, iOS 는 사실상 어려움 — 모바일이 주 대상이면 베드락 애드온이 계속 본편
