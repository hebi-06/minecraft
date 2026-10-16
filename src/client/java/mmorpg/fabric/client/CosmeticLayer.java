package mmorpg.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * 장식 렌더 층 (사람 — 플레이어 · 마네킹 렌더러에 Fabric 으로 붙임): 갑옷 스킨 · 머리 장식 · 등 장식을 사람 모델의 움직임 그대로
 * (ArmorRenderer.submitTransformCopyingModel — RPG 갑옷과 같은 방법). 빛나는 그림 (emissive) 이 있으면 eyes 렌더 타입으로 한 번 더 (어두워도 보임).
 * 몸이 투명 (투명화 물약) 이면 머리 · 등 장식은 그리지 않음 (갑옷 스킨은 갑옷처럼 그림).
 */
public final class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final int FULL_BRIGHT = 0xF000F0;

    /** 시험용: 그린 장식 조각 수 */
    public static int drawn;

    public CosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    static void init() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) helper.register(new CosmeticLayer(avatar));
        });
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        CosmeticRender.Data d = state.getData(CosmeticRender.KEY);
        if (d == null || d == CosmeticRender.NONE) return;
        for (PieceModel m : d.armor()) draw(m, pose, collector, light, state);
        if (state.isInvisible) return;
        if (d.head() != null) draw(d.head(), pose, collector, light, state);
        if (d.back() != null) draw(d.back(), pose, collector, light, state);
    }

    private void draw(PieceModel m, PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state) {
        ArmorRenderer.submitTransformCopyingModel(getParentModel(), state, m, state, true, collector, pose, RenderTypes.armorCutoutNoCull(m.texture), light,
                OverlayTexture.NO_OVERLAY, state.outlineColor);
        if (m.emissive != null) {
            ArmorRenderer.submitTransformCopyingModel(getParentModel(), state, m, state, true, collector, pose, RenderTypes.eyes(m.emissive), FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, 0);
        }
        drawn++;
    }
}
