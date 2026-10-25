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
 * - attack 처음: 대상을 기억, 참격 연출 (0.39.0 — 같은 휘두르기의 범위 피해에서 조준한 적을 뺌), 활 · 완드는 쏘고 취소, 요구 조건이 모자란 근접 무기는 취소
 * - attack 의 대상.hurtOrSimulate(…, 피해): RPG 평타 공식으로 바꿈 (0.39.0 — 공격 대기 충전과 상관없이 온전한 피해)
 * - actuallyHurt 의 setHealth: 방패 감소 · 죽지 않는 피해 (RPG 체력 규칙)
 * - getXpNeededForNextLevel: 레벨업에 필요한 경험치 = 바닐라 × 3 (9단계) × 10렙 뒤로 점점 커지는 배율 (0.24.1 — 40렙에서 3) — 규칙 코어 Leveling, rules_more.json LEVELING
 * - giveExperiencePoints · giveExperienceLevels: 최고 레벨 (0.24.2 — 60) 에서 멈춤 (그 뒤 경험치는 오르지 않음)
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Unique
    private Entity mmorpg$target;

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void mmorpg$beforeAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        this.mmorpg$target = target;
        if (self instanceof ServerPlayer sp) Slash.onAttack(sp, target);
        if (RpgCombat.beforeAttack(self, target)) ci.cancel();
    }

    @ModifyArg(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), index = 1)
    private float mmorpg$meleeDamage(float amount) {
        return RpgCombat.meleeDamage((Player) (Object) this, this.mmorpg$target, amount, 1F);
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

    /**
     * 0.24.2 최고 레벨 (LEVELING.maxLevel = 60): 최고 레벨이면 경험치를 더 받지 않음 (몬스터 · 보스 · 퀘스트 · 경험치 구슬 · /xp — 막대는 비움).
     * 빼는 쪽 (음수 — 인챈트 · 모루가 레벨을 씀) 은 그대로
     */
    @Inject(method = "giveExperiencePoints", at = @At("HEAD"), cancellable = true)
    private void mmorpg$xpCapBefore(int amount, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (amount > 0 && mmorpg.core.Leveling.atMax(self.experienceLevel)) {
            self.experienceProgress = 0F;
            ci.cancel();
        }
    }

    /** 경험치를 받다가 최고 레벨에 이르면 그 레벨에서 멈추고 남은 경험치는 버림 */
    @Inject(method = "giveExperiencePoints", at = @At("TAIL"))
    private void mmorpg$xpCapAfter(int amount, CallbackInfo ci) {
        mmorpg$clampLevel();
    }

    /** 레벨을 더해도 (giveExperiencePoints 안의 레벨업 · /xp add … levels) 최고 레벨까지만 */
    @Inject(method = "giveExperienceLevels", at = @At("TAIL"))
    private void mmorpg$levelCap(int levels, CallbackInfo ci) {
        mmorpg$clampLevel();
    }

    @Unique
    private void mmorpg$clampLevel() {
        Player self = (Player) (Object) this;
        int max = mmorpg.core.Leveling.maxLevel();
        if (self.experienceLevel >= max) {
            self.experienceLevel = max;
            self.experienceProgress = 0F;
        }
    }
}
