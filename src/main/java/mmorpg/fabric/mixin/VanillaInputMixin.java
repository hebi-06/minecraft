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
 * 모드 없는 클라이언트의 스킬 입력 (VanillaInput): F (왼손 바꾸기) = 모드 넘김 · 스킬 모드의 칸 바뀜 = 스킬 · 스킬 모드에서 가방 누름 · 고르기 막음.
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

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void mmorpg$slotKey(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (!mmorpg$serverThread()) return;
        if (VanillaInput.onSlot(this.player, packet.getSlot())) ci.cancel();
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
