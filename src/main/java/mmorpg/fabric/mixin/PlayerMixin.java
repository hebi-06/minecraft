package mmorpg.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Slash;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 플레이어 공격 · 받는 피해.
 * - attack 처음: 공격 충전 정도와 대상을 기억, 참격 연출, 활 · 완드는 쏘고 취소, 요구 조건이 모자란 근접 무기는 취소
 * - attack 의 대상.hurtOrSimulate(…, 피해): RPG 평타 공식으로 바꿈
 * - actuallyHurt 의 setHealth: 방패 감소 · 죽지 않는 피해 (RPG 체력 규칙)
 * - getXpNeededForNextLevel: 레벨업에 필요한 경험치 = 바닐라 × 3 (9단계) × 10렙 뒤로 점점 커지는 배율 (0.24.1 — 40렙에서 3) — 규칙 코어 Leveling, rules_more.json LEVELING
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Unique
    private float mmorpg$strength = 1F;
    @Unique
    private Entity mmorpg$target;

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void mmorpg$beforeAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        this.mmorpg$strength = self.getAttackStrengthScale(0.5F);
        this.mmorpg$target = target;
        if (self instanceof ServerPlayer sp) Slash.onAttack(sp);
        if (RpgCombat.beforeAttack(self, target)) ci.cancel();
    }

    @ModifyArg(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), index = 1)
    private float mmorpg$meleeDamage(float amount) {
        return RpgCombat.meleeDamage((Player) (Object) this, this.mmorpg$target, amount, this.mmorpg$strength);
    }

    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"))
    private void mmorpg$takeLoss(Player self, float health, Operation<Void> original, @Local(argsOnly = true) DamageSource source) {
        float next = self instanceof ServerPlayer sp ? RpgHealth.onLoss(sp, source, self.getHealth(), health) : health;
        original.call(self, next);
    }

    /** 레벨업에 필요한 경험치 (9단계 · 0.24.1): 바닐라 식 × LEVELING.needMult × ramp — 경험치 막대 · /xp · 경험치 구슬 모두 이 값으로 */
    @Inject(method = "getXpNeededForNextLevel", at = @At("HEAD"), cancellable = true)
    private void mmorpg$xpNeeded(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(mmorpg.core.Leveling.need(((Player) (Object) this).experienceLevel));
    }
}
