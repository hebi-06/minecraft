package mmorpg.fabric.mixin;

import io.netty.channel.ChannelFutureListener;
import mmorpg.fabric.vanilla.VanillaPackets;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 모드 없는 클라이언트 한 사람에게 가는 패킷 바꾸기 (VanillaPackets — 하트 비율 · 스킬 모드 핫바 그림). 게임 단계 연결만 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class VanillaSendMixin {
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), argsOnly = true)
    private Packet<?> mmorpg$vanilla(Packet<?> packet) {
        if ((Object) this instanceof ServerGamePacketListenerImpl game) return VanillaPackets.rewrite(game.player, packet);
        return packet;
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("TAIL"))
    private void mmorpg$vanillaAfter(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl game) VanillaPackets.after(game.player, packet);
    }
}
