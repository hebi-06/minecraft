package mmorpg.fabric.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import mmorpg.fabric.client.motion.MotionRender;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.38.0 동작: 1인칭 무기 — 동작 중이면 바닐라 휘두르기 대신 동작의 1인칭 열쇠 (가로 · 세로 · 회전 · 던지기 …).
 * 손에 든 아이템 자리 (applyItemArmTransform 끝 — 휘두르지 않아도 늘 불림: 스킬 · 구르기) 에 더하고, 바닐라 휘두르기 회전 (applyItemArmAttackTransform) 은 막음
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsMixin {
    @Inject(method = "applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V", at = @At("TAIL"))
    private void mmorpg$motion(PoseStack pose, HumanoidArm arm, float equip, CallbackInfo ci) {
        MotionRender.firstPerson(pose, arm);
    }

    @Inject(method = "applyItemArmAttackTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V", at = @At("HEAD"),
            cancellable = true)
    private void mmorpg$noVanillaSwing(PoseStack pose, HumanoidArm arm, float swing, CallbackInfo ci) {
        if (MotionRender.firstPersonActive(arm)) ci.cancel();
    }
}
