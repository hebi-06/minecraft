package mmorpg.fabric.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import mmorpg.fabric.client.motion.MotionRender;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.38.0 동작: 1인칭 무기 — 동작 중이면 바닐라 휘두르기 대신 동작의 1인칭 열쇠 (가로 · 세로 · 회전 · 던지기 …).
 * 손에 든 아이템 자리 (applyItemArmTransform 끝 — 휘두르지 않아도 늘 불림: 스킬 · 구르기) 에 더하고, 바닐라 휘두르기 회전 (applyItemArmAttackTransform) 은 막음.
 * 손 하나를 그리기 시작할 때 (submitArmWithItem 처음) 의 자리를 적어 두어, 그 사이 바닐라가 옮긴 몫 (휘두를 때 흔들림 · 공격 대기로 내려간 손) 을 동작 무게만큼 되돌림
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsMixin {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void mmorpg$armBase(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partial, float pitch, InteractionHand hand,
            float attack, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        MotionRender.firstPersonBase(pose, hand);
    }

    /** 0.40.0 참격 궤적: 주 손을 그린 뒤 (손을 그리기 시작한 자리로 돌아온 pose) 1인칭 궤적 띠 — BladeTrails.submitFirstPerson */
    @Inject(method = "submitArmWithItem", at = @At("TAIL"))
    private void mmorpg$bladeTrail(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partial, float pitch, InteractionHand hand,
            float attack, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (hand == InteractionHand.MAIN_HAND) mmorpg.fabric.client.motion.BladeTrails.submitFirstPerson(pose, collector);
    }

    @Inject(method = "applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V", at = @At("TAIL"))
    private void mmorpg$motion(PoseStack pose, HumanoidArm arm, float equip, CallbackInfo ci) {
        MotionRender.firstPerson(pose, arm);
    }

    @Inject(method = "applyItemArmAttackTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V", at = @At("HEAD"),
            cancellable = true)
    private void mmorpg$noVanillaSwing(PoseStack pose, HumanoidArm arm, float swing, CallbackInfo ci) {
        if (MotionRender.firstPersonActive(arm)) ci.cancel();
    }
}
