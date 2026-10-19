package mmorpg.fabric.client.mixin;

import mmorpg.fabric.client.rpgui.RpgScreens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.23.0 RPG 창 (인벤토리 · 상자 · 셜커 — RpgScreens.themed): 제목 · "인벤토리" 글자 금빛 · 그림자, 칸마다 아이템 아래에 등급 색
 * (무기 · 갑옷 · 룬 — 일반 회색 ~ 초월 빨강, 영웅 · 초월은 반짝임) · 마우스 아래 칸은 금빛 (바닐라 흰 테 대신).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void mmorpg$rpgLabels(GuiGraphicsExtractor g, int mx, int my, CallbackInfo ci) {
        if (RpgScreens.containerLabels((AbstractContainerScreen<?>) (Object) this, g)) ci.cancel();
    }

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void mmorpg$rpgSlot(GuiGraphicsExtractor g, Slot slot, int mx, int my, CallbackInfo ci) {
        RpgScreens.slotUnder((AbstractContainerScreen<?>) (Object) this, g, slot);
    }

    @Inject(method = "extractSlotHighlightBack", at = @At("HEAD"), cancellable = true)
    private void mmorpg$rpgHighlight(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (RpgScreens.themed((AbstractContainerScreen<?>) (Object) this)) ci.cancel();
    }
}
