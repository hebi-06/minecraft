package mmorpg.fabric.client.mixin;

import mmorpg.fabric.client.rpgui.RpgScreens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.23.0 RPG 인벤토리 (어둠의 금테): 바닐라 바탕을 그린 뒤 그 위를 금테 창 · 어두운 칸 · 사람 그림 칸 (남빛) · 금 화살표로 덮음
 * (사람 그림은 같은 층에서 나중에 그려지므로 그대로 보임), "제작" 글자는 금빛 · 그림자.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void mmorpg$rpgBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick, CallbackInfo ci) {
        RpgScreens.inventoryBackground((InventoryScreen) (Object) this, g);
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void mmorpg$rpgLabels(GuiGraphicsExtractor g, int mx, int my, CallbackInfo ci) {
        if (RpgScreens.inventoryLabels((InventoryScreen) (Object) this, g)) ci.cancel();
    }
}
