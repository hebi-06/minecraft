package mmorpg.fabric.client.rpgui;

import mmorpg.fabric.container.ContainerTools;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;

/**
 * 0.23.0 RPG 창 (클라이언트): 인벤토리가 열리면 왼쪽에 캐릭터 정보 패널, 상자 · 셜커 창이 열리면 서버에 "이 창에 정렬 · 넣기 단추를 보여도 되는지"
 * 묻고 (쓰레기통은 안 됨) 답이 오면 창 오른쪽에 단추. 바탕 · 글자 · 칸은 믹스인 (RpgScreens).
 */
public final class ClientRpgUi {
    private ClientRpgUi() {}

    /** 서버가 단추를 허락한 창 번호 (-1 = 없음) */
    private static int allowedId = -1;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ContainerTools.InfoPayload.TYPE, (p, ctx) -> {
            if (p.allowed()) allowedId = p.containerId();
            else if (allowedId == p.containerId()) allowedId = -1;
        });
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof InventoryScreen inv) {
                Screens.getWidgets(inv).add(new CharacterPanel(inv));
                return;
            }
            if (screen instanceof ContainerScreen || screen instanceof ShulkerBoxScreen) {
                var cs = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) screen;
                int id = cs.getMenu().containerId;
                Screens.getWidgets(cs).add(new ContainerButtons(cs));
                if (allowedId != id && ClientPlayNetworking.canSend(ContainerTools.QueryPayload.TYPE)) ClientPlayNetworking.send(new ContainerTools.QueryPayload(id));
                ScreenEvents.remove(cs).register(s -> {
                    if (allowedId == id) allowedId = -1;
                });
            }
        });
    }

    /** 서버가 이 창에 단추를 허락했는지 */
    public static boolean allowed(int containerId) {
        return containerId == allowedId;
    }

    static void sendAction(int containerId, String action) {
        if (ClientPlayNetworking.canSend(ContainerTools.ActionPayload.TYPE)) ClientPlayNetworking.send(new ContainerTools.ActionPayload(containerId, action));
    }
}
