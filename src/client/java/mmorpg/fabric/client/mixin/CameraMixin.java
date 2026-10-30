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

    @Shadow
    protected abstract void setPosition(net.minecraft.world.phys.Vec3 pos);

    @Shadow
    public abstract net.minecraft.world.phys.Vec3 position();

    @Shadow
    public abstract boolean isDetached();

    @Inject(method = "alignWithEntity", at = @At("RETURN"))
    private void mmorpg$shake(float partialTick, CallbackInfo ci) {
        // 0.48.2 스킬 영상 카메라 (화면 시험만 — 정한 자리 · 방향, 흔들림은 그 위에 더함)
        double[] film = mmorpg.fabric.client.FilmCamera.pose();
        if (film != null) {
            this.setPosition(new net.minecraft.world.phys.Vec3(film[0], film[1], film[2]));
            this.setRotation((float) film[3], (float) film[4]);
        }
        float[] o = CameraShake.offset(partialTick);
        if (o != null) this.setRotation(this.yRot + o[0], this.xRot + o[1]);
        // 0.38.0 구르기 (1인칭): 화면을 낮추고 살짝 내려다봄
        float[] d = mmorpg.fabric.client.motion.ClientMotions.cameraDip(partialTick);
        if (d != null && !this.isDetached()) {
            this.setPosition(this.position().add(0, -d[0], 0));
            this.setRotation(this.yRot, this.xRot + d[1]);
        }
    }
}
