package mmorpg.fabric.mixin;

import mmorpg.fabric.AttackMute;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 2026-11-26 플레이어 근접 공격 (평타) 의 바닐라 파티클 (피해 하트 · 휩쓸기 · 치명타 별) 을 보내지 않음 (AttackMute — 사용자 "마크 파티클은 최소한으로").
 * 둘레 사람 모두에게 보내는 sendParticles 두 가지 (불 두 개가 있는 것 · 없는 것 — 판마다 바닐라가 부르는 쪽이 다를 수 있어 둘 다, 없으면 건너뜀 require 0 —
 * 막혔는지는 AttackMute.muted 로 게임 시험이 봄)
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelParticleMixin {
    @Inject(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I", at = @At("HEAD"), cancellable = true, require = 0)
    private void mmorpg$muteAttack(ParticleOptions o, double x, double y, double z, int n, double dx, double dy, double dz, double speed,
            CallbackInfoReturnable<Integer> cir) {
        if (AttackMute.blocks(o)) cir.setReturnValue(0);
    }

    @Inject(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDIDDDD)I", at = @At("HEAD"), cancellable = true, require = 0)
    private void mmorpg$muteAttackForced(ParticleOptions o, boolean force, boolean always, double x, double y, double z, int n, double dx, double dy, double dz,
            double speed, CallbackInfoReturnable<Integer> cir) {
        if (AttackMute.blocks(o)) cir.setReturnValue(0);
    }
}
