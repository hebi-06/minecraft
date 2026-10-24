package mmorpg.fabric.client.mixin;

import mmorpg.fabric.client.motion.MotionRender;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 0.38.0 동작: 사람 모델 · 갑옷 모델의 바닐라 자세 뒤에 동작 자세 (몸통 · 팔 · 다리 · 팔꿈치 · 무릎 굽힘) — MotionRender.applyModel */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void mmorpg$motion(HumanoidRenderState state, CallbackInfo ci) {
        MotionRender.applyModel((HumanoidModel<?>) (Object) this, state);
    }
}
