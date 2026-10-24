package mmorpg.fabric.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import mmorpg.fabric.client.motion.BendMesh;
import mmorpg.fabric.client.motion.BendablePart;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.38.0 팔꿈치 · 무릎 굽힘 (BendablePart): 굽힘이 있는 부위의 compile 에서 굽힘을 시작해 그 부위 (와 아래 부위) 의 상자를 BendMesh 로 그리고,
 * 그 부위의 render 가 끝나면 끝냄. resetPose 에서 굽힘을 지움 (1인칭 손 · 다른 모델이 남은 굽힘을 받지 않게).
 */
@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements BendablePart {
    @Shadow
    @Final
    private List<ModelPart.Cube> cubes;

    @Unique
    private float mmorpg$angle;
    @Unique
    private float mmorpg$jx;
    @Unique
    private float mmorpg$jy;
    @Unique
    private float mmorpg$jz;

    @Override
    public void mmorpg$setBend(float angle, float jx, float jy, float jz) {
        this.mmorpg$angle = angle;
        this.mmorpg$jx = jx;
        this.mmorpg$jy = jy;
        this.mmorpg$jz = jz;
    }

    @Override
    public float mmorpg$bend() {
        return this.mmorpg$angle;
    }

    @Override
    public float mmorpg$jointX() {
        return this.mmorpg$jx;
    }

    @Override
    public float mmorpg$jointY() {
        return this.mmorpg$jy;
    }

    @Override
    public float mmorpg$jointZ() {
        return this.mmorpg$jz;
    }

    @Override
    public float[] mmorpg$center() {
        if (this.cubes.isEmpty()) return null;
        ModelPart.Cube c = this.cubes.get(0);
        return new float[] {(c.minX + c.maxX) / 2F, (c.minY + c.maxY) / 2F, (c.minZ + c.maxZ) / 2F};
    }

    @Inject(method = "resetPose", at = @At("TAIL"))
    private void mmorpg$resetBend(CallbackInfo ci) {
        this.mmorpg$angle = 0;
    }

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void mmorpg$bendCompile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color, CallbackInfo ci) {
        if (this.mmorpg$angle != 0 && !BendMesh.active()) {
            BendMesh.begin((ModelPart) (Object) this, pose, this.mmorpg$angle, this.mmorpg$jx, this.mmorpg$jy, this.mmorpg$jz);
        }
        if (BendMesh.active() && BendMesh.compile(this.cubes, pose, consumer, light, overlay, color)) ci.cancel();
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("RETURN"))
    private void mmorpg$bendEnd(PoseStack pose, VertexConsumer consumer, int light, int overlay, int color, CallbackInfo ci) {
        BendMesh.end((ModelPart) (Object) this);
    }
}
