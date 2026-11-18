package mmorpg.fabric.mixin;

import mmorpg.fabric.Slash;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 휘두르기 (공격 버튼 — 허공 · 블록 · 몹): 근접 무기 참격 연출 (애드온 playerSwingStart).
 * 끝 (TAIL) 에서 — 처음에는 네트워크 스레드에서 한 번 불렸다가 서버 스레드로 다시 오므로.
 * 0.39.0 공격 패킷: 근접 무기 평타 사이 (MOTION.interval) 가 지나지 않은 공격은 받지 않음 (Slash.admit — 서버 스레드에서 처리할 때만 봄)
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePunch", at = @At("TAIL"))
    private void mmorpg$punch(ServerboundPunchPacket packet, CallbackInfo ci) {
        // 2026-11-18 축복의 길: Shift + 좌클릭 스킬 키 (SkillKeys) · 활 · 완드는 좌클릭으로 쏨 (우클릭은 스킬 키) — 아니면 근접 평타 참격
        if (mmorpg.fabric.job.SkillKeys.onPunch(this.player)) return;
        if (mmorpg.fabric.RpgCombat.punchRanged(this.player)) return;
        Slash.onPunch(this.player);
    }

    @Inject(method = "handleAttack", at = @At("HEAD"), cancellable = true)
    private void mmorpg$attackGate(ServerboundAttackPacket packet, CallbackInfo ci) {
        ServerPlayer p = this.player;
        if (p == null || Thread.currentThread() != p.level().getServer().getRunningThread()) return;
        if (mmorpg.fabric.job.SkillKeys.onAttack(p)) { // 웅크린 채 몹을 누름 = Shift + 좌클릭 스킬 (평타 대신)
            ci.cancel();
            return;
        }
        if (!Slash.admit(p, true)) ci.cancel();
    }
}
