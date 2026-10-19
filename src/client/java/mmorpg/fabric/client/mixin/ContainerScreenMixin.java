package mmorpg.fabric.client.mixin;

import mmorpg.fabric.client.rpgui.RpgScreens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 0.23.0 RPG 상자 창 (상자 · 큰 상자 · 엔더 창고 · 통 · 쓰레기통 — 바닐라 9 × 1 ~ 6): 바닐라 바탕 위를 금테 창 · 어두운 칸으로 덮음 */
@Mixin(ContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void mmorpg$rpgBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick, CallbackInfo ci) {
        RpgScreens.containerBackground((ContainerScreen) (Object) this, g);
    }
}
