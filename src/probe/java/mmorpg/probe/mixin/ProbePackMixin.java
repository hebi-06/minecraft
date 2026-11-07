package mmorpg.probe.mixin;

import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 서버 리소스 팩을 묻지 않고 받음 (사람이 "예" 를 누른 것과 같음) */
@Mixin(ServerData.class)
public class ProbePackMixin {
    @Inject(method = "getResourcePackStatus", at = @At("HEAD"), cancellable = true)
    private void probe$accept(CallbackInfoReturnable<ServerData.ServerPackStatus> cir) {
        cir.setReturnValue(ServerData.ServerPackStatus.ENABLED);
    }
}
