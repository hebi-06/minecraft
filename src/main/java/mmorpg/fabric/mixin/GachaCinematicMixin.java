package mmorpg.fabric.mixin;

import mmorpg.fabric.vanilla.GachaCinematic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 룬 뽑기 연출 (GachaCinematic) 동안의 입력: 좌클릭 (휘두르기 · 공격) · 우클릭 (아이템 · 블록 · 몹) = 다음, 그 패킷은 버림 (화면이 가려져 있어
 * 블록을 부수거나 놓거나 몹을 치지 않게 — 클라이언트가 미리 바꾼 블록은 되돌림). 서버 스레드에서만 (sgui 와 같은 자리 — ensureRunningOnSameThread 뒤).
 * F 는 VanillaInput.onSwapKey (그쪽이 먼저 받음).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class GachaCinematicMixin {
    private static final String SAME_THREAD = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V";

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePunch", at = @At(value = "INVOKE", target = SAME_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void mmorpg$cinePunch(ServerboundPunchPacket packet, CallbackInfo ci) {
        if (GachaCinematic.input(this.player)) ci.cancel();
    }

    @Inject(method = "handleAttack", at = @At(value = "INVOKE", target = SAME_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void mmorpg$cineAttack(ServerboundAttackPacket packet, CallbackInfo ci) {
        if (GachaCinematic.input(this.player)) ci.cancel();
    }

    @Inject(method = "handleInteract", at = @At(value = "INVOKE", target = SAME_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void mmorpg$cineInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (GachaCinematic.input(this.player)) ci.cancel();
    }

    @Inject(method = "handleUseItem", at = @At(value = "INVOKE", target = SAME_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void mmorpg$cineUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (GachaCinematic.input(this.player)) ci.cancel();
    }

    @Inject(method = "handleUseItemOn", at = @At(value = "INVOKE", target = SAME_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void mmorpg$cineUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (!GachaCinematic.input(this.player)) return;
        BlockPos pos = packet.hitResult().getBlockPos();
        resync(pos);
        resync(pos.relative(packet.hitResult().getDirection()));
        this.player.connection.send(new ClientboundBlockChangedAckPacket(packet.sequence()));
        ci.cancel();
    }

    @Inject(method = "handlePlayerAction", at = @At(value = "INVOKE", target = SAME_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void mmorpg$cineAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (!GachaCinematic.active(this.player)) return;
        ServerboundPlayerActionPacket.Action a = packet.getAction();
        if (a == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) GachaCinematic.input(this.player);
        if (a == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK || a == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK
                || a == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
            resync(packet.getPos());
            this.player.connection.send(new ClientboundBlockChangedAckPacket(packet.getSequence()));
        }
        ci.cancel();
    }

    private void resync(BlockPos pos) {
        this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.player.level().getBlockState(pos)));
    }
}
