package mmorpg.fabric.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import mmorpg.fabric.client.AddonParticle;
import mmorpg.fabric.client.FxParticle;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 큰 파티클 (애드온 이펙트 그림 · 애드온 파티클 — 빛기둥 · 활 빛줄기 · 마법진) 은 화면 밖 자르기를 가운데 점이 아니라 크기 (reach) 로:
 * 26.3 은 파티클의 가운데 점이 화면 안일 때만 그려서, 가까이서 내려다본 14칸 빛기둥처럼 가운데가 화면 밖이면 통째로 안 보였음.
 */
@Mixin(QuadParticleGroup.class)
public abstract class QuadParticleGroupMixin {
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/Frustum;pointInFrustum(DDD)Z"))
    private boolean mmorpg$cullByReach(Frustum frustum, double x, double y, double z, Operation<Boolean> original, @Local SingleQuadParticle particle) {
        if (original.call(frustum, x, y, z)) return true;
        if (!(particle instanceof AddonParticle) && !(particle instanceof FxParticle)) return false;
        double r = Math.max(particle.getBoundingBox().getXsize(), particle.getBoundingBox().getYsize());
        return r > 0 && frustum.isVisible(new AABB(x - r, y - r, z - r, x + r, y + r, z + r));
    }
}
