package mmorpg.fabric.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import mmorpg.core.Armor;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.gear.RpgArmorItem;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 갑옷 입은 모습 (애드온 art/armor 의 3D 모델 — 순찰대 · 강철 기사 · 흑요석 수호자 · 적룡 · 황제): tools/armor_items.py 가 베드락 모델을
 * 자바 사람 모델 좌표로 바꾼 것 (assets/mmorpg/armor/&lt;세트&gt;_&lt;부위&gt;.json) 을 읽어 모델을 만들고, Fabric ArmorRenderer 로 입은 사람의
 * 머리 · 몸 · 팔 · 다리 움직임을 그대로 따라 그림 (바닐라 갑옷 층 대신). 황제 망토는 걷는 빠르기만큼 뒤로 날림 (emperor_cape.animation).
 */
public final class ArmorModels {
    private ArmorModels() {}

    private static final String[] PIECES = {"helmet", "chest", "legs", "boots"};
    private static final Map<String, PieceModel> MODELS = new HashMap<>();

    /** 갑옷 한 부위의 모델: 뿌리 아래 head · body · right_arm · left_arm · right_leg · left_leg (사람 모델과 같은 이름 — 움직임을 그대로 받음) */
    static final class PieceModel extends Model<HumanoidRenderState> {
        final Identifier texture;
        /** 황제 망토 (없으면 null) */
        private final ModelPart cape;
        private final float capeBase;

        PieceModel(ModelPart root, Identifier texture) {
            super(root, RenderTypes::armorCutoutNoCull);
            this.texture = texture;
            ModelPart c = null;
            if (root.hasChild("body") && root.getChild("body").hasChild("fw_cape")) c = root.getChild("body").getChild("fw_cape");
            this.cape = c;
            this.capeBase = c == null ? 0 : c.xRot;
        }

        /** 사람 모델의 움직임을 받은 뒤 (TransformCopyingModel): 망토만 걷는 빠르기 × 35° + 살랑임 2° (베드락 math.sin 은 도) */
        @Override
        public void setupAnim(HumanoidRenderState s) {
            if (cape == null) return;
            double speed = Math.max(0, Math.min(1, s.walkAnimationSpeed));
            double sway = Math.sin(Math.toRadians(s.ageInTicks / 20.0 * 100)) * 2;
            cape.xRot = capeBase + (float) Math.toRadians(speed * 35 + sway);
        }
    }

    private static JsonObject read(String path) {
        try (InputStream in = ArmorModels.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            Mmorpg.LOG.warn("[mmorpg] armor model {}", path, e);
            return null;
        }
    }

    private static float f(JsonArray a, int i) {
        return a.get(i).getAsFloat();
    }

    /** 뼈 하나 (상자들 + 아래 뼈) */
    private static void addPart(PartDefinition parent, String name, JsonObject bone) {
        CubeListBuilder cubes = CubeListBuilder.create();
        for (JsonElement el : bone.getAsJsonArray("cubes")) {
            JsonObject c = el.getAsJsonObject();
            JsonArray from = c.getAsJsonArray("from");
            JsonArray size = c.getAsJsonArray("size");
            JsonArray uv = c.getAsJsonArray("uv");
            cubes.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt()).mirror(c.get("mirror").getAsBoolean())
                    .addBox(f(from, 0), f(from, 1), f(from, 2), f(size, 0), f(size, 1), f(size, 2), new CubeDeformation(c.get("inflate").getAsFloat()));
        }
        JsonArray p = bone.getAsJsonArray("pivot");
        JsonArray r = bone.getAsJsonArray("rot");
        PartDefinition part = parent.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(f(p, 0), f(p, 1), f(p, 2), (float) Math.toRadians(f(r, 0)),
                (float) Math.toRadians(f(r, 1)), (float) Math.toRadians(f(r, 2))));
        JsonObject children = bone.getAsJsonObject("children");
        for (String child : children.keySet()) addPart(part, child, children.getAsJsonObject(child));
    }

    private static PieceModel build(JsonObject m) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        JsonObject parts = m.getAsJsonObject("parts");
        for (String name : parts.keySet()) addPart(root, name, parts.getAsJsonObject(name));
        ModelPart baked = LayerDefinition.create(mesh, m.get("tw").getAsInt(), m.get("th").getAsInt()).bakeRoot();
        return new PieceModel(baked, Identifier.parse(m.get("texture").getAsString()));
    }

    /** 세트 · 부위의 모델 (없으면 null) */
    static PieceModel get(String set, String piece) {
        return MODELS.get(set + "_" + piece);
    }

    /** 클라이언트 시작: 모델을 모두 읽고, RPG 갑옷 120 에 그리기를 맡김 */
    static void init() {
        for (RpgArmorItem item : GearItems.armor()) {
            Armor.Piece a = item.piece();
            String key = a.set() + "_" + a.piece();
            if (MODELS.containsKey(key)) continue;
            JsonObject m = read("/assets/mmorpg/armor/" + key + ".json");
            if (m != null) MODELS.put(key, build(m));
        }
        ArmorRenderer renderer = new ArmorRenderer() {
            @Override
            public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, ItemStack stack,
                    HumanoidRenderState state, net.minecraft.world.entity.EquipmentSlot slot, int light,
                    net.minecraft.client.model.HumanoidModel<HumanoidRenderState> contextModel) {
                if (!(stack.getItem() instanceof RpgArmorItem item)) return;
                PieceModel m = get(item.piece().set(), item.piece().piece());
                if (m == null) return;
                ArmorRenderer.submitTransformCopyingModel(contextModel, state, m, state, true, collector, poseStack, RenderTypes.armorCutoutNoCull(m.texture), light,
                        OverlayTexture.NO_OVERLAY, state.outlineColor);
            }

            /** 투구 칸의 아이템 모양 (평면 아이콘) 을 머리에 또 그리지 않음 */
            @Override
            public boolean shouldRenderDefaultHeadItem(LivingEntity entity, ItemStack stack) {
                return false;
            }
        };
        ArmorRenderer.register(renderer, GearItems.armor().toArray(new RpgArmorItem[0]));
        Mmorpg.LOG.info("MMO RPG: armor models {}", MODELS.size());
    }

    /** 시험용: 읽은 모델 수 (세트 5 × 부위 4 = 20) */
    public static int count() {
        return MODELS.size();
    }

    static String[] pieces() {
        return PIECES;
    }
}
