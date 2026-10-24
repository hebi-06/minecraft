package mmorpg.fabric.client.mixin;

import mmorpg.fabric.client.motion.ClientMotions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 0.38.0 동작: 내 사람이 공격 키로 휘두르는 순간 (startAttack 안의 swing) 평타 동작 시작 */
@Mixin(LivingEntity.class)
public abstract class ClientLivingEntityMixin {
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("HEAD"))
    private void mmorpg$swing(InteractionHand hand, SwingAnimation animation, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if (hand == InteractionHand.MAIN_HAND && ClientMotions.inAttack()) ClientMotions.attackSwing((Entity) (Object) this);
    }
}
