package mmorpg.fabric.client.form;

import mmorpg.fabric.ui.FormNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** 서버 창 패킷 받기: 새 창은 지금 화면을 바꾸고 (앞의 창이면 닫기 응답 없이), 닫기 패킷은 기다리던 그 창만 닫음 */
public final class FormClient {
    private FormClient() {}

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
    }

    private static void open(Minecraft mc, FormScreen next) {
        if (mc.gui.screen() instanceof FormScreen old) old.replaced = true;
        mc.gui.setScreen(next);
    }
}
