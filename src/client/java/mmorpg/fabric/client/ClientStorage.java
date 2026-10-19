package mmorpg.fabric.client;

import mmorpg.core.Storage;
import mmorpg.fabric.storage.StorageNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 0.22.0 엔더 창고 (클라이언트): 서버가 보낸 창고 상태 (열린 창 번호 · 지금 상자 · 열린 상자 수 · 값 · 든 묶음 수) 를 들고 있다가,
 * 큰 상자 창 (바닐라 ContainerScreen 9 × 6) 이 열리면 그 창 왼쪽에 상자 탭 (StorageTabs) 을 붙임 — 창 번호가 같을 때만 보이고 눌림
 * (창이 열린 뒤에 상태가 와도 그때부터 보임). 창이 닫히면 상태를 지움 (창 번호를 다시 쓰는 다른 상자 창에 탭이 붙지 않게).
 */
public final class ClientStorage {
    private ClientStorage() {}

    private static StorageNet.StatePayload state;

    static void init() {
        ClientPlayNetworking.registerGlobalReceiver(StorageNet.StatePayload.TYPE, (payload, context) -> state = payload);
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof ContainerScreen cs) || cs.getMenu().getRowCount() != Storage.ROWS) return;
            Screens.getWidgets(cs).add(new StorageTabs(cs));
            ScreenEvents.remove(cs).register(s -> {
                StorageNet.StatePayload st = state;
                if (st != null && st.containerId() == cs.getMenu().containerId) state = null;
            });
        });
    }

    /** 이 창이 엔더 창고면 그 상태 (아니면 null) */
    public static StorageNet.StatePayload stateFor(AbstractContainerMenu menu) {
        StorageNet.StatePayload s = state;
        return s != null && menu != null && s.containerId() == menu.containerId ? s : null;
    }

    /** 탭 누르기 → 서버 ("page" 그 상자로 · "buy" 다음 상자 사기) */
    static void send(String action, int containerId, int page) {
        if (ClientPlayNetworking.canSend(StorageNet.ActionPayload.TYPE)) ClientPlayNetworking.send(new StorageNet.ActionPayload(action, containerId, page));
    }
}
