package mmorpg.fabric.client.mixin;

import mmorpg.fabric.client.motion.MotionRender;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 0.46.0 룬 동작의 1인칭 소품 (연막탄 · 표창 · 덫 …): 그 t 구간에 그릴 손 아이템을 소품으로 바꿈 (렌더 상태만 — MotionRender.firstPersonProps) */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonItemsMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void mmorpg$props(LocalPlayer player, float partial, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        MotionRender.firstPersonProps(player, partial, state);
    }
}
