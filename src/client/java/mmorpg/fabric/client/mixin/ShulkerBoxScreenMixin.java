package mmorpg.fabric.client.mixin;

import mmorpg.fabric.client.rpgui.RpgScreens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 0.23.0 RPG 셜커 상자 창: 바닐라 바탕 위를 금테 창 · 어두운 칸으로 덮음 */
@Mixin(ShulkerBoxScreen.class)
public abstract class ShulkerBoxScreenMixin {
    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void mmorpg$rpgBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick, CallbackInfo ci) {
        RpgScreens.containerBackground((ShulkerBoxScreen) (Object) this, g);
    }
}
