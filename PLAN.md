# 자바 에디션 (Fabric) 이식 계획

작성: 2026-10-09 · 갱신: 2026-10-10 · 기준 애드온: v2.27.0 (스크립트 약 44,700줄)
저장소: https://github.com/hebi-06/minecraft (main 에 올리면 GitHub Actions 가 빌드 · 시험 · 릴리스)

## 1. 현재 상태

| 단계 | 상태 | 내용 |
|---|---|---|
| 0. 규칙 코어 | 완료 | 밸런스 데이터 (`rules.json`) 와 순수 계산 함수 81개를 Java 로. 애드온 JS 와 같은 입력 28,530개에서 같은 값 (매 빌드 확인) |
| 빌드 환경 | 완료 | GitHub Actions: 버전 확인 → 코어 대조 → 컴파일 → 게임 시험 (서버) → 화면 시험 (클라이언트, 확인 항목 + 사진) → 모두 통과하면 릴리스 `latest` |
| 1. 기본 | 완료 (26.3) | 스탯 · 레벨 포인트 · 스탯 창 · RPG 체력 · 체력 바 HUD · 무기 151종 (요구 조건 · 평타 공식 · 치명타 · 활 · 완드 · 방패) · 피해 숫자 |
| 2. 무기 연출 · 3D 모델 | 완료 (26.3) | 무기 3D 모델 31종 · 활 / 완드 이펙트 그림 · 근접 참격 22종 그림 · 무기 빛 알갱이 13종 · 화면 흔들림 (아래 3절) |
| 3. 초월 무기 스킬 · 룬 | 다음 | 아래 4절 |
| 4 ~ 6 | 대기 | 아래 4절 |

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

## 2. 시험

| 시험 | 방법 | 내용 |
|---|---|---|
| 규칙 코어 대조 | `./check-core.sh` (Node + JDK) | 애드온 JS 함수와 Java 포트의 결과 28,530개 비교 |
| 게임 시험 16개 | `./gradlew runGameTest` (`build` 가 함께 돌림 — 서버 + 가짜 플레이어 · 좀비) | 시작 포인트 · 레벨업 · 최대 체력 · 회복 환산 · 방패 · 독 · 토템 · 체력 비율 저장 · 평타 공식 · 치명타 · 요구 조건 · 활 · 완드 · 피해 숫자 · 무기 등록 · 명령어 (+ 권한) |
| 화면 시험 | `./gradlew runClientGameTest` (Fabric 클라이언트 게임 시험, CI 는 xvfb) | 평평한 세계에서 실제 입력으로: 확인 16항목 (스탯 동기화 · HUD · 평타 / 치명타 숫자 · 무기 빛 알갱이 수 · 1인칭 / 3인칭 자리 · 도끼 화면 흔들림 · 낮은 체력 · K 키 스탯 창 · 툴팁 · +1 클릭이 서버 스탯을 올림 · 활 · 완드 명중) + 장면 사진 → `ci-logs` 브랜치 `client-checks.txt` · `shots/` |
| 무기 사진첩 | 같은 화면 시험 (`RpgWeaponGallery`) | 디자인 31종 1인칭 · 3인칭 · 마네킹 옆 / 앞 · 밤 빛나는 칸, 활 · 완드 이펙트 틱별, 참격 7종 (평타 · 치명타 · 허공), 빛 알갱이 13종 (밤 실제 크기 · 마네킹 4배) — 사진으로 확인 |

## 3. 베드락 기능 → Fabric 대응 (1 · 2단계)

| 애드온 (베드락) | 자바 (Fabric) |
|---|---|
| 스코어보드 스탯 · 레벨 · `rpg:hp_ratio` | 플레이어 데이터 (Fabric Data Attachment `mmorpg:player`, 죽어도 유지) |
| 가상 체력 (`hp.js` — 바닐라 체력 20 을 거울로) | 최대 체력 속성 수정자 (`mmorpg:rpg_max_hp`) — 피해는 바닐라 그대로 (방어구 · 흡수 뒤) × 방패 감소, 회복은 × 최대 체력 / 20 (믹스인) |
| 체력 바 (리소스 팩 HUD, 제목 채널) | 클라이언트 HUD (바닐라 하트 자리, 애드온 그림 그대로) |
| 무기 아이템 · 설명 | Item 151개 (`rpg:<종류>_l<레벨>_<등급>`), 설명은 코어 `weaponInfo` |
| 무기 3D 모델 31종 (attachable · geo.json · 1인칭 자세 · 빛나는 칸) | 아이템 모델 (`tools/weapon_models.py`): weapon 뼈의 상자 그대로, 손 자세는 Blockbench 베드락 미리보기 규칙으로 애드온 화면과 같은 자리가 되게 계산, 빛나는 칸 = `light_emission` 15. 인벤토리는 아이콘 |
| 무기 주변 효과 (attachable 의 fx 파티클 — 영웅 · 초월 13종) | 클라이언트 `WeaponAura`: 무기를 든 플레이어 · 마네킹마다 모델의 fx 자리 (1인칭 카메라 공간 · 3인칭 팔 공간, 생성기가 계산) 에서 초당 10개, 바닐라 generic_0 · flame 그림 |
| 평타 보정 (`entityHurt` before) | `Player#attack` 믹스인 (공격 충전은 자바 방식 유지: 0.2 + 0.8 × 충전²) |
| 피해 숫자 (`rpg:dmg_text` 이름표) | `text_display` 엔티티 (늘 카메라를 봄 · 밝게 · 그림자, 18틱 동안 떠오름) |
| 스탯 창 (상자 창) | 클라이언트 Screen (키 K · 스탯 책 · `/stats`), 모드 없는 클라이언트는 채팅 스탯 창 |
| 이펙트 그림 파티클 (`rpg:arrow_shot` · `rpg:magic_circle` · 참격 22종 · 스킬 그림) | 파티클 `mmorpg:fx` (`FxOptions`: 종류 · 그림 · 방향 · 속도 · 크기 · 수명 · 색 …) — 서버가 보내고 클라이언트 `FxParticle` 이 애드온 그림 (PNG 그대로, `tools/fx_textures.py`) 으로 같은 모양 · 움직임 · 사라짐 |
| 참격 (`slash.js` — 휘두르기 · 맞힘) | `Slash` (서버): 휘두르기 패킷 · 공격마다 참격, 맞힌 피해 뒤 맞힘 연출 (`DamageNumbers` 가 부름) |
| `camerashake add @s 세기 초 rotational` | `RpgNet.ShakePayload` → 클라이언트 `CameraShake` → `Camera` 믹스인 (보는 방향만 떨림, 조준은 그대로) |
| 명령어 (`/weapon` · `/points` 등) | Brigadier (`/rpg …` + 애드온과 같은 이름) |

## 4. 다음 단계 (각 단계 = 빌드 · 게임 시험 · 화면 시험 통과 후 전달)

3. 초월 무기 스킬 · 룬 80종 (연출 포함 — 가장 큰 단계). 2단계의 `mmorpg:fx` 파티클 · 화면 흔들림을 그대로 씀
4. 갑옷 · 강화 · 대장장이 · 상점 · NPC · 퀘스트
5. 몬스터 · 보스 9종 · 제단 · 세계수
6. 결투 (PvP) · 탈것 · 엔더 상자 · 나머지

## 5. 참고

- 자바 에디션은 PC 용. 안드로이드는 비공식 런처로 가능하지만 성능 제약, iOS 는 사실상 어려움 — 모바일이 주 대상이면 베드락 애드온이 계속 본편
