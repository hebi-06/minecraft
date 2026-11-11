package mmorpg.probe.mixin;

import mmorpg.probe.Probe;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 2026-11-11 영상 카메라 (cam film — 모드 영상과 같은 자리): 카메라가 사람 쪽으로 맞춘 뒤 (alignWithEntity) 정한 자리 · 방향으로 옮김 */
@Mixin(Camera.class)
public abstract class ProbeCameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Inject(method = "alignWithEntity", at = @At("RETURN"))
    private void probe$film(float partialTick, CallbackInfo ci) {
        double[] c = Probe.filmCamera(Minecraft.getInstance());
        if (c == null) return;
        this.setPosition(new Vec3(c[0], c[1], c[2]));
        this.setRotation((float) c[3], (float) c[4]);
    }
}
