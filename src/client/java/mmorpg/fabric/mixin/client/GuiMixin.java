package mmorpg.fabric.mixin.client;

import mmorpg.fabric.client.RpgHud;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 바닐라 하트 · 배고픔 · 갑옷 · 공기 줄 대신 (모드가 있는 서버의 생존 · 모험에서만) RpgHud 의 체력 바 · 갑옷 · 레벨 · 공기를
 * 같은 자리 · 같은 층에 그림 (최대 체력이 수백이 되면 하트가 여러 줄로 쌓이므로).
 */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true, require = 0)
    private void mmorpg$rpgHealth(GuiGraphics graphics, CallbackInfo ci) {
        if (!RpgHud.active()) return;
        RpgHud.render(graphics);
        ci.cancel();
    }
}
