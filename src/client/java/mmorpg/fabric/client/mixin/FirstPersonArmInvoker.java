package mmorpg.fabric.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 0.46.0 룬 동작의 1인칭 맨팔 (주먹 · 손바닥): 바닐라 1인칭 맨팔 그리기 (MotionRender.firstPersonHands 가 옮기고 돌린 뒤 부름) */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public interface FirstPersonArmInvoker {
    @Invoker("renderPlayerArm")
    void mmorpg$renderPlayerArm(PoseStack pose, SubmitNodeCollector collector, int light, float equip, float swing, HumanoidArm arm, PlayerRenderState player);
}
