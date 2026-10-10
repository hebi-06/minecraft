package mmorpg.fabric.client.bedrock;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import mmorpg.core.bedrock.Animator;
import mmorpg.core.bedrock.Ctx;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.mob.MobData;
import mmorpg.fabric.mob.RpgEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

/**
 * 애드온 엔티티 그리기 (몬스터 · 보스 · 소품 — 베드락 리소스 팩 그대로, BedrockLooks):
 * 동작 (scripts.animate + 서버가 튼 동작 · 속성 식) 으로 뼈 자세를 정하고, 렌더 컨트롤러로 텍스처 · 덮는 색 · 부위 숨김을 고름.
 * <ul>
 * <li>바탕: 재질대로 (잘라내기 · 반투명) — 맞으면 바닐라처럼 붉게</li>
 * <li>빛나는 칸 (entity_emissive 의 낮은 알파): 바탕 위에 밝게 덧그림 (GlowLayer)</li>
 * <li>덮는 색 (overlay_color — 보스가 맞을 때 번쩍임 · 분노 · 수르트의 열기): 흰 텍스처를 그 색 · 세기로 덧그림 (TintLayer)</li>
 * </ul>
 * 화면 밖 자르기 · 그리는 거리는 모델의 보이는 범위 (visible_bounds) 로 — 작은 충돌 상자에 큰 모델 (요르문간드의 고리) 이 잘리지 않게.
 */
public final class BedrockRenderer extends LivingEntityRenderer<BedrockMob, BedrockRenderer.State, BedrockRenderer.Model> {
    private final BedrockLooks.Look look;
    /** 그림자 반지름 (크기 1 기준 — 충돌 상자 폭의 절반, 소품은 없음) */
    private final float shadow;

    /** 클라이언트 시작: 애드온 엔티티 26종 그리기 (MmorpgClient) */
    @SuppressWarnings("unchecked")
    public static void init() {
        for (var e : RpgEntities.types().entrySet()) {
            String id = e.getKey();
            EntityRendererRegistry.register((EntityType<BedrockMob>) e.getValue(), ctx -> create(ctx, id));
        }
    }

    static BedrockRenderer create(EntityRendererProvider.Context ctx, String id) {
        BedrockLooks.Look look = BedrockLooks.load(ctx.getResourceManager(), id);
        if (look == null) throw new IllegalStateException("bedrock look missing: " + id);
        return new BedrockRenderer(ctx, look);
    }

    /** 그릴 때의 상태 */
    public static final class State extends LivingEntityRenderState {
        /** 뼈마다 더할 자세 (회전 도 · 위치 픽셀 (베드락 좌표) · 크기) */
        public double[] rot;
        public double[] pos;
        public double[] scale;
        public boolean[] visible;
        public Identifier texture;
        public Identifier glow;
        /** 덮는 색 (ARGB) — 0 이면 없음 */
        public int tint;
    }

    /** 모델: 뼈마다 기본 자세 + 상태의 자세 */
    public static final class Model extends EntityModel<State> {
        private final ModelPart[] parts;
        private final PartPose[] base;

        Model(BedrockLooks.Look look) {
            super(look.root, look.renderType());
            this.parts = look.parts;
            this.base = look.base;
        }

        @Override
        public void setupAnim(State s) {
            resetPose();
            if (s.rot == null) return;
            for (int i = 0; i < parts.length; i++) {
                ModelPart p = parts[i];
                PartPose b = base[i];
                if (p == null || b == null) continue;
                int k = i * 3;
                p.x = b.x() + (float) s.pos[k];
                p.y = b.y() - (float) s.pos[k + 1];
                p.z = b.z() + (float) s.pos[k + 2];
                p.xRot = b.xRot() + BedrockLooks.rad(s.rot[k]);
                p.yRot = b.yRot() + BedrockLooks.rad(s.rot[k + 1]);
                p.zRot = b.zRot() + BedrockLooks.rad(s.rot[k + 2]);
                p.xScale = (float) s.scale[k];
                p.yScale = (float) s.scale[k + 1];
                p.zScale = (float) s.scale[k + 2];
                p.visible = s.visible == null || s.visible[i];
            }
        }
    }

    private BedrockRenderer(EntityRendererProvider.Context ctx, BedrockLooks.Look look) {
        super(ctx, new Model(look), 0.5F);
        this.look = look;
        MobData.Def def = MobData.get(look.id);
        this.shadow = def == null || def.prop() ? 0F : def.width() * 0.5F;
        addLayer(new GlowLayer(this));
        addLayer(new TintLayer(this));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BedrockMob e, State s, float partialTick) {
        super.extractRenderState(e, s, partialTick);
        Ctx ctx = look.animator.ctx;
        double[] v = new double[ctx.size()];
        v[Ctx.LIFE_TIME] = s.ageInTicks / 20.0;
        v[Ctx.MOVE_SPEED] = s.walkAnimationSpeed;
        v[Ctx.DISTANCE_MOVED] = s.walkAnimationPos;
        v[Ctx.HURT_TIME] = e.hurtTime > 0 ? Math.max(0, e.hurtTime - partialTick) : 0;
        v[Ctx.TARGET_X] = s.xRot;
        v[Ctx.TARGET_Y] = s.yRot;
        v[Ctx.ON_GROUND] = e.onGround() ? 1 : 0;
        v[Ctx.IN_WATER] = e.isInWater() ? 1 : 0;
        v[Ctx.HEALTH] = e.getHealth();
        v[Ctx.MAX_HEALTH] = e.getMaxHealth();
        v[Ctx.GROUND_SPEED] = e.getDeltaMovement().horizontalDistance() * 20;
        v[Ctx.IS_MOVING] = s.walkAnimationSpeed > 0.01 ? 1 : 0;
        for (String p : ctx.props()) {
            int slot = ctx.prop(p);
            if (slot >= 0) v[slot] = e.prop(p);
        }
        long now = e.level().getGameTime();
        List<Animator.Playing> playing = new ArrayList<>();
        for (BedrockMob.Playing p : e.syncedAnims()) {
            playing.add(new Animator.Playing(p.anim(), Math.max(0, (now - p.start() + partialTick) / 20.0), p.stop()));
        }
        Animator.Pose pose = new Animator.Pose(look.geo.bones.size());
        look.animator.apply(v, playing, pose);
        s.rot = pose.rot;
        s.pos = pose.pos;
        s.scale = pose.scale;
        if (look.ctl.hasVisibility()) {
            boolean[] vis = new boolean[look.geo.bones.size()];
            look.ctl.visibility(v, look.geo, vis);
            s.visible = vis;
        } else {
            s.visible = null;
        }
        String key = look.ctl.texture(v);
        s.texture = look.texture(key);
        s.glow = look.glow.get(key);
        float[] rgba = new float[4];
        s.tint = look.ctl.overlay(v, rgba) ? ARGB.colorFromFloat(rgba[3], rgba[0], rgba[1], rgba[2]) : 0;
    }

    /** 그림자: 충돌 상자 폭의 절반 × 크기 (보스는 크게) */
    @Override
    protected float getShadowRadius(State s) {
        return shadow * s.scale;
    }

    @Override
    public Identifier getTextureLocation(State s) {
        return s.texture != null ? s.texture : look.texture("default");
    }

    /** 화면 밖 자르기: 충돌 상자와 모델의 보이는 범위 (× 크기) 중 큰 것 */
    @Override
    protected AABB getBoundingBoxForCulling(BedrockMob e, float partialTick) {
        AABB box = super.getBoundingBoxForCulling(e, partialTick);
        double sc = e.getScale();
        double hw = look.geo.boundsWidth * sc / 2;
        double hh = look.geo.boundsHeight * sc;
        double ox = look.geo.boundsOffset[0] * sc;
        double oy = look.geo.boundsOffset[1] * sc;
        double oz = look.geo.boundsOffset[2] * sc;
        AABB vis = new AABB(e.getX() + ox - hw, e.getY() + oy - hh / 2, e.getZ() + oz - hw, e.getX() + ox + hw, e.getY() + oy + hh, e.getZ() + oz + hw);
        return box.minmax(vis);
    }

    /** 바닐라 이름표: 이름표가 보이게 된 것만 (몬스터의 여러 줄 이름표는 글자 표시 엔티티) */
    @Override
    protected boolean shouldShowName(BedrockMob e, double distanceToCameraSq) {
        return e.isCustomNameVisible() && super.shouldShowName(e, distanceToCameraSq);
    }

    // ------------------------------------------------------------ 덧그림

    /** 빛나는 칸 (entity_emissive): 밝기 최대로 바탕 위에 */
    static final class GlowLayer extends RenderLayer<State, Model> {
        GlowLayer(BedrockRenderer r) {
            super(r);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, State s, float yRot, float xRot) {
            if (s.glow == null || s.isInvisible) return;
            collector.submitModel(getParentModel(), s, poseStack, RenderTypes.entityTranslucentEmissive(s.glow), 0xF000F0, OverlayTexture.NO_OVERLAY,
                    s.outlineColor);
        }
    }

    /** 덮는 색 (overlay_color): 흰 텍스처에 그 색 · 세기 */
    static final class TintLayer extends RenderLayer<State, Model> {
        TintLayer(BedrockRenderer r) {
            super(r);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, State s, float yRot, float xRot) {
            if (s.tint == 0 || s.isInvisible) return;
            collector.submitModel(getParentModel(), s, poseStack, RenderTypes.entityTranslucent(BedrockLooks.WHITE), light, OverlayTexture.NO_OVERLAY, s.tint,
                    null, s.outlineColor);
        }
    }

    static {
        Mmorpg.LOG.debug("BedrockRenderer loaded");
    }
}
