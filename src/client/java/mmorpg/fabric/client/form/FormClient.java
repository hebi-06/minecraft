package mmorpg.fabric.client.form;

import java.util.List;
import mmorpg.fabric.ui.FormNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * 서버 창 패킷 받기: 새 창은 지금 화면을 바꾸고 (앞의 창이면 닫기 응답 없이), 닫기 패킷은 기다리던 그 창만 닫음.
 * NPC 대화창이 열린 동안은 아래쪽 HUD 를 숨김 (대화 상자가 핫바 · 체력 줄 자리를 덮으므로 — 비쳐 보이거나 아래로 삐져나오지 않게),
 * 어느 창이든 열린 동안은 화면 하단 글자 (NPC 안내 · 알림) 를 숨김 (창 뒤에서 반쯤 가려 보이지 않게 — 결과 문구는 창 본문에 나옴, 창을 닫으면 남은 시간만큼 다시 보임).
 */
public final class FormClient {
    private FormClient() {}

    /** 대화창 아래에 숨길 HUD (핫바 · RPG 체력 줄 · 갑옷 · 배고픔 · 공기 · 탈것 · 경험치 · 든 아이템 이름 · 조준점) */
    private static final List<Identifier> UNDER_DIALOG = List.of(VanillaHudElements.HOTBAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.ARMOR_BAR,
            VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR,
            VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.CROSSHAIR);

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(FormNet.ChestPayload.TYPE, (p, ctx) -> open(ctx.client(), new ChestFormScreen(p)));
        ClientPlayNetworking.registerGlobalReceiver(FormNet.DialogPayload.TYPE, (p, ctx) -> open(ctx.client(), new DialogFormScreen(p)));
        ClientPlayNetworking.registerGlobalReceiver(FormNet.MenuPayload.TYPE, (p, ctx) -> open(ctx.client(), new MenuFormScreen(p)));
        ClientPlayNetworking.registerGlobalReceiver(FormNet.ModalPayload.TYPE, (p, ctx) -> open(ctx.client(), new ModalFormScreen(p)));
        ClientPlayNetworking.registerGlobalReceiver(FormNet.ClosePayload.TYPE, (p, ctx) -> {
            Minecraft mc = ctx.client();
            if (mc.gui.screen() instanceof FormScreen fs && fs.id == p.id()) {
                fs.responded = true;
                mc.gui.setScreen(null);
            }
        });
        // 게임이 다 뜬 뒤에 감쌈 — 다른 곳 (MmorpgClient 의 RPG 체력 줄) 이 먼저 바꾼 HUD 도 이 바깥에서 숨기도록
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            for (Identifier id : UNDER_DIALOG) {
                HudElementRegistry.replaceElement(id, old -> (graphics, delta) -> {
                    if (!dialogOpen()) old.extractRenderState(graphics, delta);
                });
            }
            HudElementRegistry.replaceElement(VanillaHudElements.OVERLAY_MESSAGE, old -> (graphics, delta) -> {
                if (!formOpen()) old.extractRenderState(graphics, delta);
            });
        });
    }

    /** NPC 대화창이 열려 있는지 */
    public static boolean dialogOpen() {
        return Minecraft.getInstance().gui.screen() instanceof DialogFormScreen;
    }

    /** 서버 창 (상자 창 · 대화창 · 버튼 목록 · 입력창) 이 열려 있는지 */
    public static boolean formOpen() {
        return Minecraft.getInstance().gui.screen() instanceof FormScreen;
    }

    private static void open(Minecraft mc, FormScreen next) {
        if (mc.gui.screen() instanceof FormScreen old) old.replaced = true;
        mc.gui.setScreen(next);
    }
}
