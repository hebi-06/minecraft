package mmorpg.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import mmorpg.core.RuneTip;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.RpgParticles;
import mmorpg.fabric.StatSheet;
import mmorpg.fabric.rune.RuneItem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/**
 * 클라이언트: 스탯 창 키 (K), 서버가 보낸 스탯 데이터, 체력 바 HUD, 이펙트 그림 파티클, 무기 빛 알갱이, 화면 흔들림,
 * 초월 룬 하늘빛 · 화면 번쩍임 (UltFx), 갑옷 입은 모습 (ArmorModels), 길 안내 칸 (NavHud),
 * 스킬 칸 (11단계 — 룬을 올려 둔 칸 8 · 칸마다 키 R Z V B Y H N M · 스킬 칸 창 J · 화면 오른쪽 아래 HUD),
 * 장식 (0.13.0 — 장식 창 U · 무기 · 갑옷 스킨 · 머리 · 등 장식 · 오라 · 칭호를 보이는 사람마다 그림).
 * HUD 는 Fabric HUD API 로 바닐라 하트 자리를 바꿈 (모드가 있는 서버의 생존 · 모험에서만 — 아니면 바닐라 그대로).
 */
public final class MmorpgClient implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Mmorpg.id("main"));
    public static KeyMapping statsKey;
    /** 스킬 칸 키 (칸 순서 — 조작 설정에서 바꿈) · 스킬 칸 창 키 */
    public static KeyMapping[] skillKeys = new KeyMapping[0];
    public static KeyMapping skillBarKey;
    /** 장식 창 키 (0.13.0 — U) */
    public static KeyMapping cosmeticKey;
    /** 0.37.0 아티팩트 창 키 (I — O 는 26.x 의 친구 창 키) */
    public static KeyMapping artifactKey;
    /** 스킬 칸 기본 키: 왼손을 WASD 에 둔 채 닿는 키 중 바닐라가 쓰지 않는 것 (G 는 26.3 의 빠른 동작 키라 Y — CI 화면 시험이 바닐라 키와 겹치지 않는지 확인) */
    public static final int[] SKILL_KEYS = {InputConstants.KEY_R, InputConstants.KEY_Z, InputConstants.KEY_V, InputConstants.KEY_B, InputConstants.KEY_Y,
        InputConstants.KEY_H, InputConstants.KEY_N, InputConstants.KEY_M};

    @Override
    public void onInitializeClient() {
        // 이펙트 그림 (활 빛줄기 · 마법진 · 참격 …) — assets/mmorpg/particles/fx.json 의 그림 순서 = FxParticle 의 TEX_*
        ParticleProviderRegistry.getInstance().register(RpgParticles.FX,
                sprites -> (options, level, x, y, z, xd, yd, zd, random) -> new FxParticle(level, x, y, z, options, sprites));
        // 애드온 파티클 119종 (식 그대로 — assets/mmorpg/addon_particles.json)
        AddonParticles.init();
        ParticleProviderRegistry.getInstance().register(RpgParticles.ADDON,
                sprites -> (options, level, x, y, z, xd, yd, zd, random) -> AddonParticles.create(level, x, y, z, options, sprites));
        // RPG 갑옷 240 의 입은 모습 (애드온 3D 모델 20 — 세트마다, 종류와 상관없음)
        ArmorModels.init();
        // 훈련용 허수아비 (애드온 모델 · 흔들림)
        DummyRenderer.init();
        // 몬스터 · 보스 · 보스 소품 (애드온 모델 · 동작 · 렌더 컨트롤러 그대로)
        mmorpg.fabric.client.bedrock.BedrockRenderer.init();
        // 서버 창 (상자 창 · 대화창 · 버튼 목록 · 입력창)
        mmorpg.fabric.client.form.FormClient.init();
        // 길 안내 화면 칸 (화면 위 가운데 화살표 · 이름 · 거리)
        NavHud.init();
        TitleFit.init();
        // 장식 (0.13.0): 받은 장식 · 모습들, 장식 모델 (갑옷 스킨 · 머리 · 등 장식), 사람 렌더러에 장식 층
        ClientCosmetics.init();
        ClientGacha.init(); // 룬 뽑기 창 · 연출 (0.14.0)
        // 룬 툴팁 (0.16.1): 총 피해 · 회복을 내 스탯으로 (받은 스탯 · 장비 · 버프 · 스탯 트리 공격력 + 룬 피해 % · 최대 체력 — 서버 RuneCast 와 같은 식)
        RuneItem.viewer = () -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (!ClientRpgState.synced() || mc.player == null) return null;
            StatSheet s = ClientRpgState.sheet();
            return new RuneTip.Viewer(s.eff(), 1 + (s.tree("atk") + s.tree("rune")) / 100, mc.player.getMaxHealth());
        };
        CosmeticModels.init();
        CosmeticLayer.init();
        statsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mmorpg.stats", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY));
        // 스킬 칸 (11단계): 칸 8 키 + 스킬 칸 창 키, 서버가 보낸 칸들 · 룬 재사용 대기
        KeyMapping[] keys = new KeyMapping[ClientSkillBar.SIZE];
        for (int i = 0; i < keys.length; i++) {
            int def = i < SKILL_KEYS.length ? SKILL_KEYS[i] : InputConstants.UNKNOWN.getValue();
            keys[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mmorpg.skill" + (i + 1), InputConstants.Type.KEYBOARD, def, CATEGORY));
        }
        skillKeys = keys;
        skillBarKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mmorpg.skillbar", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, CATEGORY));
        cosmeticKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mmorpg.cosmetic", InputConstants.Type.KEYBOARD, InputConstants.KEY_U, CATEGORY));
        artifactKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mmorpg.artifact", InputConstants.Type.KEYBOARD, InputConstants.KEY_I, CATEGORY));
        // 0.37.0 아티팩트: 서버가 보낸 칸 다섯 (open 이면 창) · 아티팩트 설명의 지금 레벨
        ClientPlayNetworking.registerGlobalReceiver(mmorpg.fabric.artifact.ArtifactNet.SlotsPayload.TYPE, (payload, context) -> ClientArtifacts.update(payload));
        mmorpg.fabric.gear.ArtifactItem.viewer = () -> ClientRpgState.synced() ? ClientArtifacts.level() : -1;
        ClientPlayNetworking.registerGlobalReceiver(mmorpg.fabric.rune.SkillBarNet.BarPayload.TYPE, (payload, context) -> ClientSkillBar.update(payload));
        ClientPlayNetworking.registerGlobalReceiver(mmorpg.fabric.rune.SkillBarNet.CooldownPayload.TYPE, (payload, context) -> ClientSkillBar.cooldown(payload));
        // 0.26.0 직업 스킬 칸 5 (Shift + 룬 칸 1 ~ 5 의 키)
        ClientPlayNetworking.registerGlobalReceiver(mmorpg.fabric.job.JobSkills.BarPayload.TYPE, (payload, context) -> ClientJobBar.update(payload));
        ClientPlayNetworking.registerGlobalReceiver(mmorpg.fabric.job.JobSkills.GaugePayload.TYPE, (payload, context) -> ClientJobBar.gauge(payload)); // 0.27.0 직업 자원 · 태세
        ClientPlayNetworking.registerGlobalReceiver(mmorpg.fabric.job.JobUpgrades.FxPayload.TYPE, (payload, context) -> JobUpgradeHud.start(payload)); // 0.31.0 직업 스킬 강화 연출
        ClientPlayNetworking.registerGlobalReceiver(RpgNet.SyncPayload.TYPE, (payload, context) -> {
            ClientRpgState.update(payload);
            if (payload.open()) context.client().gui.setScreen(new StatScreen());
        });
        ClientPlayNetworking.registerGlobalReceiver(RpgNet.MountPayload.TYPE, (payload, context) -> ClientRpgState.updateMount(payload));
        // 0.21.0 지갑 (골드 · 실버 — 화면 왼쪽 아래)
        ClientPlayNetworking.registerGlobalReceiver(mmorpg.fabric.economy.EconomyNet.WalletPayload.TYPE, (payload, context) -> ClientWallet.update(payload));
        // 0.22.0 엔더 창고 (큰 상자 창 왼쪽의 상자 탭 · 사기)
        ClientStorage.init();
        // 0.23.0 RPG 창 (인벤토리 캐릭터 정보 패널 · 상자 정렬 · 넣기 단추 — 바탕 · 글자 · 등급 색 칸은 믹스인)
        mmorpg.fabric.client.rpgui.ClientRpgUi.init();
        // 스탯 트리 (7단계): 기록 · 보너스 합, open 이면 그 탭으로 창 (열려 있으면 탭만 바꿈)
        ClientPlayNetworking.registerGlobalReceiver(RpgNet.StatTreePayload.TYPE, (payload, context) -> {
            String open = ClientRpgState.updateStatTree(payload);
            if (open == null || open.isEmpty()) return;
            if (context.client().gui.screen() instanceof StatTreeScreen s) s.select(open);
            else context.client().gui.setScreen(new StatTreeScreen(open));
        });
        ClientPlayNetworking.registerGlobalReceiver(RpgNet.ShakePayload.TYPE, (payload, context) -> CameraShake.add(payload.intensity(), payload.seconds()));
        // 0.38.0 동작 (다른 사람의 평타 · 스킬 · 구르기, 내 스킬)
        ClientPlayNetworking.registerGlobalReceiver(mmorpg.fabric.motion.MotionNet.PlayPayload.TYPE, (payload, context) -> mmorpg.fabric.client.motion.ClientMotions.receive(payload));
        // 초월 룬 하늘빛 · 화면 번쩍임
        ClientPlayNetworking.registerGlobalReceiver(RpgNet.SkyPayload.TYPE, (payload, context) -> UltFx.sky(payload));
        ClientPlayNetworking.registerGlobalReceiver(RpgNet.FlashPayload.TYPE, (payload, context) -> UltFx.flash(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
            ClientRpgState.clear();
            ClientSkillBar.clear();
            ClientArtifacts.clear();
            ClientJobBar.clear();
            ClientCosmetics.clear();
            ClientGacha.clear();
            ClientWallet.clear();
            CameraShake.clear();
            UltFx.clear();
            mmorpg.fabric.client.motion.ClientMotions.clear();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (statsKey.consumeClick()) {
                if (client.player == null || client.gui.screen() != null) continue;
                if (ClientRpgState.synced()) client.gui.setScreen(new StatScreen());
                else client.player.sendSystemMessage(Component.literal("§c이 서버에는 MMO RPG 모드가 없습니다."));
            }
            // 스킬 칸: 창 키 · 칸 키 (창이 열려 있으면 바닐라가 키를 넘기지 않음 — 창 안에서는 창이 받음)
            while (skillBarKey.consumeClick()) {
                if (client.player == null || client.gui.screen() != null) continue;
                if (ClientRpgState.synced()) client.gui.setScreen(new SkillBarScreen());
                else client.player.sendSystemMessage(Component.literal("§c이 서버에는 MMO RPG 모드가 없습니다."));
            }
            while (cosmeticKey.consumeClick()) {
                if (client.player == null || client.gui.screen() != null) continue;
                if (ClientRpgState.synced() || ClientCosmetics.received()) client.gui.setScreen(new CosmeticScreen());
                else client.player.sendSystemMessage(Component.literal("§c이 서버에는 MMO RPG 모드가 없습니다."));
            }
            while (artifactKey.consumeClick()) {
                if (client.player == null || client.gui.screen() != null) continue;
                if (ClientRpgState.synced() || ClientArtifacts.received()) client.gui.setScreen(new ArtifactScreen());
                else client.player.sendSystemMessage(Component.literal("§c이 서버에는 MMO RPG 모드가 없습니다."));
            }
            for (int i = 0; i < skillKeys.length; i++) {
                while (skillKeys[i].consumeClick()) {
                    if (client.player == null || client.gui.screen() != null || !ClientSkillBar.received()) continue;
                    // 0.26.0: Shift 를 누른 채 룬 칸 1 ~ 5 의 키 = 직업 스킬 칸 1 ~ 5 (사용자 "shift 누르고 r 이런 식으로")
                    if (i < ClientJobBar.SIZE && (client.hasShiftDown() || client.options.keyShift.isDown())) ClientJobBar.press(i);
                    else ClientSkillBar.press(i);
                }
            }
            ClientSkillBar.tick(client);
            ClientJobBar.tick(client);
            RpgHud.tick(client);
            ClientWallet.tick();
            if (!client.isPaused()) CameraShake.tick();
            WeaponAura.tick(client);
            CosmeticAura.tick(client);
            mmorpg.fabric.client.motion.ClientMotions.tick(client); // 0.38.0 동작 · 쉬프트 두 번 구르기
        });
        // 체력 줄 자리에 RPG 체력 바 · 갑옷 · 레벨 · 공기, 배고픔 · 갑옷 · 공기 줄 · 바닐라 레벨 숫자는 숨김 (RPG HUD 일 때만)
        HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, old -> (graphics, delta) -> {
            if (RpgHud.active()) RpgHud.render(graphics);
            else old.extractRenderState(graphics, delta);
        });
        HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, old -> (graphics, delta) -> {
            if (!RpgHud.active()) old.extractRenderState(graphics, delta);
        });
        HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, old -> (graphics, delta) -> {
            if (!RpgHud.active()) old.extractRenderState(graphics, delta);
        });
        HudElementRegistry.replaceElement(VanillaHudElements.AIR_BAR, old -> (graphics, delta) -> {
            if (!RpgHud.active()) old.extractRenderState(graphics, delta);
        });
        // 0.24.1 경험치 막대 가운데 위의 바닐라 레벨 숫자 (초록) 도 숨김 — 레벨은 RPG 줄의 "Lv" 칸만 (사용자 요청, RPG HUD 일 때만 · 경험치 막대는 그대로)
        HudElementRegistry.replaceElement(VanillaHudElements.EXPERIENCE_LEVEL, old -> (graphics, delta) -> {
            if (!RpgHud.active()) old.extractRenderState(graphics, delta);
        });
        // 스킬 칸 (11단계): 화면 오른쪽 아래 (핫바 옆)
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Mmorpg.id("skill_bar"), SkillBarHud::render);
        // 0.21.0 지갑: 화면 왼쪽 아래 (골드 · 실버)
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Mmorpg.id("wallet"), WalletHud::render);
        // 화면 번쩍임 (초월 룬): HUD 맨 위
        HudElementRegistry.addLast(Mmorpg.id("ult_edge"), (graphics, delta) -> UltFx.renderEdge(graphics)); // 6돌 초월 룬의 테두리 빛 (8단계)
        HudElementRegistry.addLast(Mmorpg.id("ult_flash"), (graphics, delta) -> UltFx.renderFlash(graphics));
        HudElementRegistry.addLast(Mmorpg.id("job_up"), (graphics, delta) -> JobUpgradeHud.render(graphics)); // 0.31.0 직업 스킬 강화 성공
    }
}
