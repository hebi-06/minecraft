package mmorpg.fabric.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import mmorpg.fabric.client.motion.MotionRender;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 0.38.0 동작: 1인칭 무기 — 동작 중이면 바닐라 휘두르기 대신 동작의 1인칭 열쇠 (가로 · 세로 · 회전 · 던지기 …) */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsMixin {
    @Inject(method = "applyItemArmAttackTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V", at = @At("HEAD"),
            cancellable = true)
    private void mmorpg$motion(PoseStack pose, HumanoidArm arm, float swing, CallbackInfo ci) {
        if (MotionRender.firstPerson(pose, arm)) ci.cancel();
    }
}
