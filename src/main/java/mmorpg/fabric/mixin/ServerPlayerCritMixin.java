package mmorpg.fabric.mixin;

import mmorpg.fabric.AttackMute;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 2026-11-26 플레이어 근접 공격 (평타) 안의 바닐라 치명타 · 마법 치명타 알림 (떨어지며 친 바닐라 치명타 · 인챈트 — 받는 클라이언트가 별 파티클을 뿌림) 을 보내지
 * 않음 (AttackMute — 사용자 "마크 파티클은 최소한으로"). RPG 치명타는 노란 숫자 (DamageNumbers). 이름이 바뀐 판이면 건너뜀 (require 0)
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerCritMixin {
    @Inject(method = "crit(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void mmorpg$muteCrit(Entity target, CallbackInfo ci) {
        if (AttackMute.blocksCrit()) ci.cancel();
    }

    @Inject(method = "magicCrit(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void mmorpg$muteMagicCrit(Entity target, CallbackInfo ci) {
        if (AttackMute.blocksCrit()) ci.cancel();
    }
}
