package mmorpg.fabric.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import mmorpg.fabric.Mmorpg;
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
import net.minecraft.resources.Identifier;

/**
 * 사람 모델에 붙는 조각 하나 (갑옷 한 부위 · 장식 — tools/armor_items.py · tools/cosmetic_art.py 가 만든 JSON
 * {"tw", "th", "texture", "emissive"?, "translucent"?, "parts": {head · body · right_arm · left_arm · right_leg · left_leg: 뼈}}).
 * 뿌리 뼈는 사람 모델과 같은 이름 — Fabric ArmorRenderer.submitTransformCopyingModel 로 입은 사람의 움직임을 그대로 받음.
 * 움직이는 뼈 (이름): fw_cape (걷는 빠르기 × 35° + 살랑임 2° 뒤로 — 황제 망토 · 진홍 망토), fw_wing_left · fw_wing_right (날갯짓 —
 * 천천히 ±9°, 달리면 더 크게), fw_flutter_left · fw_flutter_right (0.14.2 요정 날개 — 파르르: 초당 약 3번 ±14°, 달리면 더 빠르고 크게),
 * fw_flutter_low_left · fw_flutter_low_right (아래 날개 — 같은 날갯짓을 조금 늦게 · 작게), fw_spin (초당 40° 돎), fw_bob (위아래 0.6 픽셀 떠다님).
 * emissive 가 있으면 그 그림을 빛나게 한 번 더, translucent 면 반투명으로 (CosmeticLayer).
 */
public final class PieceModel extends Model<HumanoidRenderState> {
    final Identifier texture;
    /** 빛나는 그림 (없으면 null) */
    final Identifier emissive;
    /** 반투명으로 그림 (그림의 알파대로 섞음 — 요정 날개의 막) */
    final boolean translucent;
    private final List<Anim> anims;

    /** 움직이는 뼈 하나 (처음 자세 기억) */
    private record Anim(ModelPart part, String kind, float x, float y, float z, float xRot, float yRot, float zRot) {}

    private PieceModel(ModelPart root, Identifier texture, Identifier emissive, boolean translucent, List<String[]> animPaths) {
        super(root, translucent ? RenderTypes::entityTranslucent : RenderTypes::armorCutoutNoCull);
        this.texture = texture;
        this.emissive = emissive;
        this.translucent = translucent;
        List<Anim> a = new ArrayList<>();
        for (String[] path : animPaths) {
            ModelPart p = root;
            for (String n : path) p = p.getChild(n);
            String name = path[path.length - 1];
            a.add(new Anim(p, kindOf(name), p.x, p.y, p.z, p.xRot, p.yRot, p.zRot));
        }
        this.anims = List.copyOf(a);
    }

    private static String kindOf(String bone) {
        if (bone.startsWith("fw_flutter_low_left")) return "flutter_low_left";
        if (bone.startsWith("fw_flutter_low_right")) return "flutter_low_right";
        if (bone.startsWith("fw_flutter_left")) return "flutter_left";
        if (bone.startsWith("fw_flutter_right")) return "flutter_right";
        if (bone.startsWith("fw_cape")) return "cape";
        if (bone.startsWith("fw_wing_left")) return "wing_left";
        if (bone.startsWith("fw_wing_right")) return "wing_right";
        if (bone.startsWith("fw_spin")) return "spin";
        if (bone.startsWith("fw_bob")) return "bob";
        return "";
    }

    /** 움직이는 뼈의 수 (시험용) */
    public int animCount() {
        return anims.size();
    }

    /** 반투명인지 (시험용) */
    public boolean translucent() {
        return translucent;
    }

    /** 사람 모델의 움직임을 받은 뒤 (TransformCopyingModel): 움직이는 뼈만 */
    @Override
    public void setupAnim(HumanoidRenderState s) {
        if (anims.isEmpty()) return;
        double speed = Math.max(0, Math.min(1, s.walkAnimationSpeed));
        double age = s.ageInTicks;
        for (Anim a : anims) {
            switch (a.kind()) {
                case "cape" -> {
                    double sway = Math.sin(Math.toRadians(age / 20.0 * 100)) * 2;
                    a.part().xRot = a.xRot() + (float) Math.toRadians(speed * 35 + sway);
                }
                case "wing_left", "wing_right" -> {
                    double flap = Math.sin(age * (0.14 + speed * 0.22)) * (9 + speed * 14);
                    double sign = a.kind().equals("wing_right") ? 1 : -1;
                    a.part().yRot = a.yRot() + (float) Math.toRadians(sign * (flap + speed * 6));
                }
                case "flutter_left", "flutter_right", "flutter_low_left", "flutter_low_right" -> {
                    // 파르르 (요정 날개): 틱마다 약 1 라디안 (초당 약 3번) — 아래 날개는 0.9 라디안 늦게 · 0.7 배
                    boolean low = a.kind().startsWith("flutter_low");
                    double flap = Math.sin(age * (0.95 + speed * 0.45) - (low ? 0.9 : 0)) * (14 + speed * 10) * (low ? 0.7 : 1);
                    double sign = a.kind().endsWith("right") ? 1 : -1;
                    a.part().yRot = a.yRot() + (float) Math.toRadians(sign * (flap + speed * 8));
                }
                case "spin" -> a.part().yRot = a.yRot() + (float) Math.toRadians(age * 2.0 % 360);
                case "bob" -> a.part().y = a.y() + (float) (Math.sin(age * 0.09) * 0.6);
                default -> {}
            }
        }
    }

    // ------------------------------------------------------------ 읽기

    /** 자원 (클래스 경로 — /assets/…) 의 JSON 에서 (없으면 null) */
    static PieceModel load(String path) {
        JsonObject m = read(path);
        return m == null ? null : build(m);
    }

    static JsonObject read(String path) {
        try (InputStream in = PieceModel.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException | RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] model {}", path, e);
            return null;
        }
    }

    private static float f(JsonArray a, int i) {
        return a.get(i).getAsFloat();
    }

    /** 뼈 하나 (상자들 + 아래 뼈). 움직이는 뼈 (fw_…) 는 자리 (이름 줄) 를 모아 둠 */
    private static void addPart(PartDefinition parent, String name, JsonObject bone, List<String> path, List<String[]> anims) {
        CubeListBuilder cubes = CubeListBuilder.create();
        for (JsonElement el : bone.getAsJsonArray("cubes")) {
            JsonObject c = el.getAsJsonObject();
            JsonArray from = c.getAsJsonArray("from");
            JsonArray size = c.getAsJsonArray("size");
            JsonArray uv = c.getAsJsonArray("uv");
            boolean mirror = c.has("mirror") && c.get("mirror").getAsBoolean();
            float inflate = c.has("inflate") ? c.get("inflate").getAsFloat() : 0F;
            cubes.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt()).mirror(mirror)
                    .addBox(f(from, 0), f(from, 1), f(from, 2), f(size, 0), f(size, 1), f(size, 2), new CubeDeformation(inflate));
        }
        JsonArray p = bone.getAsJsonArray("pivot");
        JsonArray r = bone.getAsJsonArray("rot");
        PartDefinition part = parent.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(f(p, 0), f(p, 1), f(p, 2), (float) Math.toRadians(f(r, 0)),
                (float) Math.toRadians(f(r, 1)), (float) Math.toRadians(f(r, 2))));
        List<String> here = new ArrayList<>(path);
        here.add(name);
        if (name.startsWith("fw_")) anims.add(here.toArray(new String[0]));
        JsonObject children = bone.getAsJsonObject("children");
        for (String child : children.keySet()) addPart(part, child, children.getAsJsonObject(child), here, anims);
    }

    static PieceModel build(JsonObject m) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        JsonObject parts = m.getAsJsonObject("parts");
        List<String[]> anims = new ArrayList<>();
        for (String name : parts.keySet()) addPart(root, name, parts.getAsJsonObject(name), List.of(), anims);
        ModelPart baked = LayerDefinition.create(mesh, m.get("tw").getAsInt(), m.get("th").getAsInt()).bakeRoot();
        Identifier emissive = m.has("emissive") ? Identifier.parse(m.get("emissive").getAsString()) : null;
        boolean translucent = m.has("translucent") && m.get("translucent").getAsBoolean();
        return new PieceModel(baked, Identifier.parse(m.get("texture").getAsString()), emissive, translucent, anims);
    }
}
