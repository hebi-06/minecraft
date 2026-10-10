package mmorpg.fabric.client.mixin;

import mmorpg.fabric.client.UltFx;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 초월 룬 하늘빛: 프레임마다 정한 안개 (거리 · 색) 를 하늘빛 쪽으로 바꿈 (UltFx.applyFog) */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void mmorpg$ultSky(Camera camera, int renderDistance, DeltaTracker deltaTracker, float darken, ClientLevel level,
            CallbackInfoReturnable<FogData> cir) {
        UltFx.applyFog(cir.getReturnValue());
    }
}
