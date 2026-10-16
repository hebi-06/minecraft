package mmorpg.fabric.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import mmorpg.fabric.client.CosmeticRender;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 장식 (0.13.0): 사람 (플레이어 · 마네킹) 렌더 상태를 다 채운 뒤 갑옷 숨기기 · 스킨 · 머리 · 등 장식 · 칭호를 붙이고 (CosmeticRender.extract),
 * 이름표를 그린 뒤 칭호를 한 줄 위에 (CosmeticRender.title).
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void mmorpg$cosmetics(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        CosmeticRender.extract(entity, state);
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("TAIL"))
    private void mmorpg$title(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        CosmeticRender.title(state, pose, collector, camera);
    }
}
