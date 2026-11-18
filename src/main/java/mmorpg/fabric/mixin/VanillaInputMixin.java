package mmorpg.fabric.mixin;

import mmorpg.fabric.vanilla.VanillaInput;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 모드 없는 클라이언트의 스킬 입력 (VanillaInput): F (왼손 바꾸기) = 룬 모드 켜고 끄기 · 룬 모드의 칸 바뀜 = 룬 스킬 · 룬 모드에서 가방 누름 · 고르기 막음.
 * 2026-11-18 스킬 키 여섯 (SkillKeys — 모든 클라이언트): 버리기 · 블록 캐기 시작 · 움직임 입력.
 * 서버 스레드에서 처리할 때만 봄 (처음에는 네트워크 스레드에서 한 번 불렸다가 서버 스레드로 다시 옴 — 네트워크 스레드에서 막으면 서버로 넘어가지 않음).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class VanillaInputMixin {
    @Shadow
    public ServerPlayer player;

    private boolean mmorpg$serverThread() {
        ServerPlayer p = this.player;
        return p != null && Thread.currentThread() == p.level().getServer().getRunningThread();
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void mmorpg$swapKey(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() != ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND || !mmorpg$serverThread()) return;
        if (VanillaInput.onSwapKey(this.player)) ci.cancel();
    }

    /**
     * 2026-11-18 축복의 길 스킬 키 (SkillKeys — 모드 · 모드 없는 클라이언트 모두): Q · Shift + Q · Ctrl + Q = 끼운 스킬 (버리지 않음 — 손 칸을 되돌림),
     * Shift + 좌클릭 스킬 키면 블록 캐기 시작을 막음 (클라이언트의 미리 부숨은 확인 패킷으로 되돌림)
     */
    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void mmorpg$dropKey(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        ServerboundPlayerActionPacket.Action a = packet.getAction();
        if (!mmorpg$serverThread()) return;
        if (a == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            if (mmorpg.fabric.job.SkillKeys.blocksMining(this.player)) {
                this.player.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket(packet.getSequence()));
                ci.cancel();
            }
            return;
        }
        if (a != ServerboundPlayerActionPacket.Action.DROP_ITEM && a != ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS) return;
        if (mmorpg.fabric.job.SkillKeys.onDrop(this.player, a == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS)) ci.cancel();
    }

    /** 2026-11-18 스킬 키 Shift 두 번 (SkillKeys.onInput — 움직임 입력의 웅크림 켜짐 · 꺼짐) */
    @Inject(method = "handlePlayerInput", at = @At("TAIL"))
    private void mmorpg$input(net.minecraft.network.protocol.game.ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        if (!mmorpg$serverThread()) return;
        mmorpg.fabric.job.SkillKeys.onInput(this.player, packet.input().shift());
    }

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void mmorpg$slotKey(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (!mmorpg$serverThread()) return;
        if (VanillaInput.onSlot(this.player, packet.getSlot())) ci.cancel();
    }

    /** 2026-11-18 1인칭 모션 (VanillaFirstPerson.onSlot): 핫바로 바꿔 든 무기에 지금 동작 이름을 다시 보냄 (켰을 때만) */
    @Inject(method = "handleSetCarriedItem", at = @At("RETURN"))
    private void mmorpg$slotChanged(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (!mmorpg$serverThread()) return;
        mmorpg.fabric.vanilla.VanillaFirstPerson.onSlot(this.player);
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void mmorpg$click(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (!mmorpg$serverThread()) return;
        if (VanillaInput.onInventoryClick(this.player)) ci.cancel();
    }

    @Inject(method = "handlePickItemFromBlock", at = @At("HEAD"), cancellable = true)
    private void mmorpg$pickBlock(ServerboundPickItemFromBlockPacket packet, CallbackInfo ci) {
        if (!mmorpg$serverThread()) return;
        if (VanillaInput.onPick(this.player)) ci.cancel();
    }

    @Inject(method = "handlePickItemFromEntity", at = @At("HEAD"), cancellable = true)
    private void mmorpg$pickEntity(ServerboundPickItemFromEntityPacket packet, CallbackInfo ci) {
        if (!mmorpg$serverThread()) return;
        if (VanillaInput.onPick(this.player)) ci.cancel();
    }
}
