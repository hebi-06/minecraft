package mmorpg.probe.mixin;

import mmorpg.probe.Probe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 서버의 알림 줄 → 사진기 신호 (TAIL — 네트워크 스레드에서 한 번 거른 뒤 메인 스레드에서 한 번만) */
@Mixin(ClientPacketListener.class)
public class ProbeChatMixin {
    @Inject(method = "handleSystemChat", at = @At("TAIL"))
    private void probe$chat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        Probe.chat(packet.content().getString());
    }
}
