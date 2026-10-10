package mmorpg.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.RpgParticles;
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
 * 초월 룬 하늘빛 · 화면 번쩍임 (UltFx), 갑옷 입은 모습 (ArmorModels).
 * HUD 는 Fabric HUD API 로 바닐라 하트 자리를 바꿈 (모드가 있는 서버의 생존 · 모험에서만 — 아니면 바닐라 그대로).
 */
public final class MmorpgClient implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Mmorpg.id("main"));
    public static KeyMapping statsKey;

    @Override
    public void onInitializeClient() {
        // 이펙트 그림 (활 빛줄기 · 마법진 · 참격 …) — assets/mmorpg/particles/fx.json 의 그림 순서 = FxParticle 의 TEX_*
        ParticleProviderRegistry.getInstance().register(RpgParticles.FX,
                sprites -> (options, level, x, y, z, xd, yd, zd, random) -> new FxParticle(level, x, y, z, options, sprites));
        // 애드온 파티클 119종 (식 그대로 — assets/mmorpg/addon_particles.json)
        AddonParticles.init();
        ParticleProviderRegistry.getInstance().register(RpgParticles.ADDON,
                sprites -> (options, level, x, y, z, xd, yd, zd, random) -> AddonParticles.create(level, x, y, z, options, sprites));
        // RPG 갑옷 120 의 입은 모습 (애드온 3D 모델 20)
        ArmorModels.init();
        statsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mmorpg.stats", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY));
        ClientPlayNetworking.registerGlobalReceiver(RpgNet.SyncPayload.TYPE, (payload, context) -> {
            ClientRpgState.update(payload);
            if (payload.open()) context.client().gui.setScreen(new StatScreen());
        });
        ClientPlayNetworking.registerGlobalReceiver(RpgNet.ShakePayload.TYPE, (payload, context) -> CameraShake.add(payload.intensity(), payload.seconds()));
        // 초월 룬 하늘빛 · 화면 번쩍임
        ClientPlayNetworking.registerGlobalReceiver(RpgNet.SkyPayload.TYPE, (payload, context) -> UltFx.sky(payload));
        ClientPlayNetworking.registerGlobalReceiver(RpgNet.FlashPayload.TYPE, (payload, context) -> UltFx.flash(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
            ClientRpgState.clear();
            CameraShake.clear();
            UltFx.clear();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (statsKey.consumeClick()) {
                if (client.player == null || client.gui.screen() != null) continue;
                if (ClientRpgState.synced()) client.gui.setScreen(new StatScreen());
                else client.player.sendSystemMessage(Component.literal("§c이 서버에는 MMO RPG 모드가 없습니다."));
            }
            RpgHud.tick(client);
            if (!client.isPaused()) CameraShake.tick();
            WeaponAura.tick(client);
        });
        // 체력 줄 자리에 RPG 체력 바 · 갑옷 · 레벨 · 공기, 배고픔 · 갑옷 · 공기 줄은 숨김 (RPG HUD 일 때만)
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
        // 화면 번쩍임 (초월 룬): HUD 맨 위
        HudElementRegistry.addLast(Mmorpg.id("ult_flash"), (graphics, delta) -> UltFx.renderFlash(graphics));
    }
}
