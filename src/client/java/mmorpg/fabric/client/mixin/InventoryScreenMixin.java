package mmorpg.fabric.client.mixin;

import mmorpg.fabric.client.rpgui.RpgScreens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.23.0 RPG 인벤토리 (어둠의 금테): 바닐라 바탕을 그린 뒤 그 위를 금테 창 · 어두운 칸 · 사람 그림 칸 (남빛) · 금 화살표로 덮고,
 * 사람 그림은 그 위에 다시 그림 (바닐라가 바탕과 함께 그린 사람은 덮이므로 — 바닐라 호출은 미뤄 두었다가 한 번만), "제작" 글자는 금빛 · 그림자.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    /** 바닐라 사람 그림을 미룸 (자리 · 크기 · 마우스 그대로 — 바탕을 덮은 뒤 RpgScreens 가 그림). 맞는 호출이 없으면 바닐라대로 */
    @Redirect(method = "extractBackground", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"))
    private void mmorpg$deferEntity(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int scale, float yOffset, float mouseX, float mouseY, LivingEntity entity) {
        RpgScreens.deferEntity(x0, y0, x1, y1, scale, yOffset, mouseX, mouseY, entity);
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void mmorpg$rpgBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick, CallbackInfo ci) {
        RpgScreens.inventoryBackground((InventoryScreen) (Object) this, g, mx, my);
    }

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void mmorpg$rpgLabels(GuiGraphicsExtractor g, int mx, int my, CallbackInfo ci) {
        if (RpgScreens.inventoryLabels((InventoryScreen) (Object) this, g)) ci.cancel();
    }
}
