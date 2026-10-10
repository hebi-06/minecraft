package mmorpg.fabric.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.npc.Dummy;
import mmorpg.fabric.npc.TrainingDummy;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/**
 * 훈련용 허수아비 그리기 (RPG_RP/entity/rpg_training_dummy.entity.json): 애드온 모델 (geometry.rpg.training_dummy — tools/dummy_model.py 가
 * 자바 좌표로 바꾼 assets/mmorpg/entity/training_dummy.json, 뼈 root > body > arms · head) + 16×16 색 팔레트 텍스처.
 * 움직임 (animation.rpg.training_dummy.wobble): 늘 아주 조금 흔들리고 (q.life_time), 맞으면 몸 · 팔 · 머리가 크게 흔들리다 잦아듦
 * (q.hurt_time — 맞으면 10 에서 틱마다 1 씩 줄어듦, 자바 LivingEntity.hurtTime 과 같음 · 틱 사이도 부드럽게). 베드락 Molang 의 math.sin 은 도 단위,
 * 회전은 자바로 (x, -y, -z). 상자마다 여섯 면이 한 텍셀이라 상자 UV 분모를 키워 펼친 UV 전체가 그 텍셀 안에 들게 함 (CELL).
 */
public final class DummyRenderer extends LivingEntityRenderer<TrainingDummy, DummyRenderer.State, DummyRenderer.DummyModel> {
    static final Identifier TEXTURE = Mmorpg.id("textures/entity/training_dummy.png");
    private static final String MODEL = "/assets/mmorpg/entity/training_dummy.json";

    /** 클라이언트 시작: 허수아비 그리기를 맡김 (MmorpgClient) */
    static void init() {
        EntityRendererRegistry.register(Dummy.TYPE, DummyRenderer::new);
    }

    /** 그릴 때의 상태 (+ q.hurt_time) */
    public static final class State extends LivingEntityRenderState {
        /** 맞은 뒤 흔들림 (10 → 0, 틱 사이 보간) */
        public float hurt;
    }

    /** 허수아비 모델 (뼈 body · arms · head 가 흔들림) */
    public static final class DummyModel extends EntityModel<State> {
        private final ModelPart body;
        private final ModelPart arms;
        private final ModelPart head;

        DummyModel(ModelPart root) {
            super(root);
            ModelPart r = root.getChild("root");
            this.body = r.getChild("body");
            this.arms = body.getChild("arms");
            this.head = body.getChild("head");
        }

        /** wobble: 베드락 식 그대로 (도) → 자바 회전 (x, -y, -z) */
        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            double life = s.ageInTicks / 20.0;
            double h = s.hurt;
            body.xRot = rad(sin(life * 80) * 0.6 + sin(h * 72) * h * 1.4);
            body.zRot = -rad(sin(h * 50) * h * 0.9);
            arms.zRot = -rad(sin(h * 96 + 30) * h * 1.1);
            head.xRot = rad(sin(h * 72 + 70) * h * 1.2);
            head.yRot = -rad(sin(h * 40) * h * 0.8);
            head.zRot = -rad(sin(h * 60 + 45) * h * 1.3);
        }

        /** Molang math.sin (도) */
        private static double sin(double deg) {
            return Math.sin(Math.toRadians(deg));
        }

        private static float rad(double deg) {
            return (float) Math.toRadians(deg);
        }
    }

    public DummyRenderer(EntityRendererProvider.Context context) {
        super(context, new DummyModel(bake()), 0.5F);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TrainingDummy entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.hurt = entity.hurtTime > 0 ? Math.max(0F, entity.hurtTime - partialTick) : 0F;
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return TEXTURE;
    }

    /** 바닐라 이름표는 그리지 않음 (머리 위 기록은 서버의 글자 표시 — 애드온의 여러 줄 이름표) */
    @Override
    protected boolean shouldShowName(TrainingDummy entity, double distanceToCameraSq) {
        return false;
    }

    // ------------------------------------------------------------ 모델 읽기

    private static JsonObject read() {
        try (InputStream in = DummyRenderer.class.getResourceAsStream(MODEL)) {
            if (in == null) throw new IllegalStateException("missing " + MODEL);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + MODEL, e);
        }
    }

    private static float f(JsonArray a, int i) {
        return a.get(i).getAsFloat();
    }

    /** 뼈 하나 (상자들 + 아래 뼈). 상자 UV: 텍셀 (u, v) 의 칸 (CELL × CELL) 안 1 부터 — 분모는 텍스처 크기 × CELL */
    private static ModelPart bone(JsonObject b, int tw, int th, int cell) {
        List<ModelPart.Cube> cubes = new ArrayList<>();
        for (JsonElement el : b.getAsJsonArray("cubes")) {
            JsonObject c = el.getAsJsonObject();
            JsonArray from = c.getAsJsonArray("from");
            JsonArray size = c.getAsJsonArray("size");
            JsonArray tex = c.getAsJsonArray("tex");
            float g = c.get("inflate").getAsFloat();
            cubes.add(new ModelPart.Cube(tex.get(0).getAsInt() * cell + 1, tex.get(1).getAsInt() * cell + 1, f(from, 0), f(from, 1), f(from, 2), f(size, 0),
                    f(size, 1), f(size, 2), g, g, g, c.get("mirror").getAsBoolean(), (float) tw * cell, (float) th * cell, EnumSet.allOf(Direction.class)));
        }
        Map<String, ModelPart> children = new LinkedHashMap<>();
        JsonObject kids = b.getAsJsonObject("children");
        for (String name : kids.keySet()) children.put(name, bone(kids.getAsJsonObject(name), tw, th, cell));
        ModelPart part = new ModelPart(cubes, children);
        JsonArray p = b.getAsJsonArray("pivot");
        JsonArray r = b.getAsJsonArray("rot");
        PartPose pose = PartPose.offsetAndRotation(f(p, 0), f(p, 1), f(p, 2), (float) Math.toRadians(f(r, 0)), (float) Math.toRadians(f(r, 1)),
                (float) Math.toRadians(f(r, 2)));
        part.setInitialPose(pose);
        part.loadPose(pose);
        return part;
    }

    /** 모델 뿌리 (빈 뿌리 아래 애드온의 뼈들) */
    static ModelPart bake() {
        JsonObject m = read();
        int tw = m.get("tw").getAsInt();
        int th = m.get("th").getAsInt();
        int cell = m.get("cell").getAsInt();
        Map<String, ModelPart> roots = new LinkedHashMap<>();
        JsonObject bones = m.getAsJsonObject("bones");
        for (String name : bones.keySet()) roots.put(name, bone(bones.getAsJsonObject(name), tw, th, cell));
        return new ModelPart(List.of(), roots);
    }
}
