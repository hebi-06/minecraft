package mmorpg.probe.mixin;

import mmorpg.probe.Probe;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 클라이언트 틱마다 사진기 (Fabric API 의 틱 이벤트 대신) */
@Mixin(Minecraft.class)
public class ProbeMinecraftMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void probe$tick(CallbackInfo ci) {
        Probe.tick((Minecraft) (Object) this);
    }
}
