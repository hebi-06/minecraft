package mmorpg.fabric.mixin;

import mmorpg.fabric.Slash;
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
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePunch", at = @At("TAIL"))
    private void mmorpg$punch(ServerboundPunchPacket packet, CallbackInfo ci) {
        Slash.onPunch(this.player);
    }
}
