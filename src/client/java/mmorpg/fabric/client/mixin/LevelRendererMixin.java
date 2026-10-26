package mmorpg.fabric.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import mmorpg.fabric.client.motion.BladeTrails;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.40.0 참격 궤적: 사람들 (엔티티) 을 다 올린 뒤 둘레 사람들의 궤적 띠를 세계에 (카메라 기준 자리 — BladeTrails.submitWorld),
 * 0.47.0 입체 효과 물체 (FxModels — 서버 Fx3d 가 띄운 모델) 도 같은 자리에
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void mmorpg$bladeTrails(PoseStack pose, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        BladeTrails.submitWorld(pose, collector);
        mmorpg.fabric.client.FxModels.submitWorld(pose, collector);
    }
}
