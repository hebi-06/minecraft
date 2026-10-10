package mmorpg.fabric.mixin;

import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.skill.Kit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 생명체 공통.
 * - heal: 플레이어 회복량을 최대 체력 비율로 (회복 1 = 최대 체력의 1/20)
 * - actuallyHurt: 실제로 깎인 양 (방어구 · 흡수 뒤) 으로 피해 숫자, 피해를 받은 틱 (스킬이 몹 무적 시간을 피하려고)
 * - 불사의 토템이 막으면 최대 체력의 10%
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Unique
    private float mmorpg$hpBefore = -1F;

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float mmorpg$scaleHeal(float amount) {
        return (Object) this instanceof ServerPlayer sp ? RpgHealth.scaleHeal(sp, amount) : amount;
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void mmorpg$hurtStart(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        this.mmorpg$hpBefore = self.getHealth() + self.getAbsorptionAmount();
        if (!self.level().isClientSide()) Kit.noteHurt(self);
    }

    @Inject(method = "actuallyHurt", at = @At("RETURN"))
    private void mmorpg$hurtEnd(ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        float before = this.mmorpg$hpBefore;
        this.mmorpg$hpBefore = -1F;
        if (before < 0 || self.level().isClientSide()) return;
        float dealt = before - (self.getHealth() + self.getAbsorptionAmount());
        if (dealt > 0) DamageNumbers.onLivingHurt(self, source, dealt);
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void mmorpg$totem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue()) && (Object) this instanceof ServerPlayer sp) RpgHealth.onTotem(sp);
    }
}
