package mmorpg.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import mmorpg.fabric.RpgNet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** 클라이언트: 스탯 창 키 (K), 서버가 보낸 스탯 데이터, 체력 바 HUD (GuiMixin 이 하트 자리에 그림) */
public final class MmorpgClient implements ClientModInitializer {
    public static KeyMapping statsKey;

    @Override
    public void onInitializeClient() {
        statsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.mmorpg.stats", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.mmorpg"));
        ClientPlayNetworking.registerGlobalReceiver(RpgNet.SyncPayload.TYPE, (payload, context) -> {
            ClientRpgState.update(payload);
            if (payload.open()) context.client().setScreen(new StatScreen());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientRpgState.clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (statsKey.consumeClick()) {
                if (client.player == null || client.screen != null) continue;
                if (ClientRpgState.synced()) client.setScreen(new StatScreen());
                else client.player.displayClientMessage(Component.literal("§c이 서버에는 MMO RPG 모드가 없습니다."), true);
            }
            RpgHud.tick(client);
        });
    }
}
