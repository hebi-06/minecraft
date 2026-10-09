package mmorpg.fabric.client.mixin;

import mmorpg.fabric.client.CameraShake;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 화면 흔들림: 카메라가 사람 쪽으로 맞춘 뒤 (alignWithEntity) 보는 방향에 흔들림 각을 더함 — 시야 · 잘라내기 계산 전 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "alignWithEntity", at = @At("RETURN"))
    private void mmorpg$shake(float partialTick, CallbackInfo ci) {
        float[] o = CameraShake.offset(partialTick);
        if (o != null) this.setRotation(this.yRot + o[0], this.xRot + o[1]);
    }
}
