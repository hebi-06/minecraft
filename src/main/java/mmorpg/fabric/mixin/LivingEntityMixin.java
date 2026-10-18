package mmorpg.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.skill.Kit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
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
 * - hurtServer 앞: 플레이어가 준 피해 × 맞는 쪽 배율 (0.20.0 심연의 낙인)
 * - 불사의 토템이 막으면 최대 체력의 10%
 * - 플레이어의 방어구는 베드락 계산 (getDamageAfterArmorAbsorb): 방어 1포인트 = 4%, 20포인트 (80%) 까지, 단단함 없음 — 애드온의 갑옷 ·
 *   몬스터 · 보스 수치가 이 계산을 기준으로 맞춰져 있음 (사양: "1포인트 = 약 4%, 실제 감소는 최대 80%"). 자바 계산은 큰 한 방을 덜 막아
 *   (20포인트로 트롤 38 을 16% 만) 몬스터 · 보스가 몇 배 아픔
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Unique
    private float mmorpg$hpBefore = -1F;

    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float mmorpg$scaleHeal(float amount) {
        return (Object) this instanceof ServerPlayer sp ? RpgHealth.scaleHeal(sp, amount) : amount;
    }

    /** 0.20.0 플레이어가 준 피해 × 맞는 쪽 배율 (심연의 낙인 — RpgCombat.takenMult, 보스 엔진에 넘어가는 피해는 Bosses.absorb 가 곱함) */
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float mmorpg$takenMult(float amount, @Local(argsOnly = true) DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide() || !(source.getEntity() instanceof ServerPlayer)) return amount;
        double m = RpgCombat.takenMult.applyAsDouble(self);
        return m == 1 || !(m > 0) ? amount : (float) (amount * m);
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

    @Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), cancellable = true)
    private void mmorpg$bedrockArmor(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ServerPlayer sp) || source.is(DamageTypeTags.BYPASSES_ARMOR)) return;
        LivingEntity self = (LivingEntity) (Object) this;
        self.hurtArmor(source, amount); // LivingEntity 의 공개 함수로 (Player 는 protected 로 덮음)
        cir.setReturnValue(amount * (1F - Math.min(20, sp.getArmorValue()) * 0.04F));
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void mmorpg$totem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue()) && (Object) this instanceof ServerPlayer sp) RpgHealth.onTotem(sp);
    }
}
