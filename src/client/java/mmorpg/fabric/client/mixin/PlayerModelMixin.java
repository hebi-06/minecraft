package mmorpg.fabric.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import mmorpg.fabric.client.motion.MotionRender;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 0.38.0 동작: 팔꿈치를 굽히면 든 무기도 팔뚝을 따라감 (손 자리 끝에서 관절 축으로 돌림) */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("TAIL"))
    private void mmorpg$hand(AvatarRenderState state, HumanoidArm arm, PoseStack pose, CallbackInfo ci) {
        MotionRender.hand((HumanoidModel<?>) (Object) this, state, arm, pose);
        if (mmorpg.fabric.client.motion.BladeTrails.captureEntity == state.id) mmorpg.fabric.client.motion.BladeTrails.captureHand(state, arm, pose); // 0.40.0 시험
    }
}
