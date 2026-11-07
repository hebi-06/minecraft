package mmorpg.fabric.mixin;

import mmorpg.fabric.vanilla.VanillaForms;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 모드 없는 클라이언트의 대화창 단추 (ClickEvent.Custom mmorpg:form — VanillaForms). 끝 (TAIL) 에서 한 번: 서버 스레드면 바로, 아니면 서버 스레드로 넘김
 * (Polymer 도 같은 곳에서 받음).
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class VanillaDialogMixin {
    @Shadow
    @Final
    protected MinecraftServer server;

    @Inject(method = "handleCustomClickAction", at = @At("TAIL"))
    private void mmorpg$formClick(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if (!(this instanceof ServerPlayerConnection connection) || !packet.id().equals(VanillaForms.CLICK)) return;
        ServerPlayer player = connection.getPlayer();
        if (this.server.isSameThread()) VanillaForms.onClick(player, packet.id(), packet.payload());
        else this.server.execute(() -> VanillaForms.onClick(player, packet.id(), packet.payload()));
    }
}
