package mmorpg.fabric.client.bedrock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import mmorpg.core.Json;
import mmorpg.core.bedrock.Animator;
import mmorpg.core.bedrock.Geo;
import mmorpg.core.bedrock.RenderCtl;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.mob.MobData;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 애드온 엔티티의 모습 (assets/mmorpg/bedrock/entities.json — tools/bedrock_entities.py 가 리소스 팩 그대로 옮긴 것):
 * 모델 (geo → ModelPart), 동작 (Animator), 렌더 컨트롤러 (RenderCtl), 텍스처 · 빛나는 칸 텍스처, 재질.
 * 리소스 팩으로 바꿀 수 있게 자원 관리자에서 읽음 (렌더러는 자원을 다시 읽을 때마다 새로 만들어짐).
 * <p>
 * 베드락 → 자바 ModelPart: x · z 그대로, y 뒤집기 (뼈 자리 (px, 24 - py, pz), 자식은 부모와의 차이), 회전은 같은 수 (도 → 라디안),
 * 상자 = (ox - px, py - oy - sy, oz - pz) 크기 그대로. 펼친 UV 는 자바 상자와 같은 배치 (크기가 소수면 UV 는 내림 — Blockbench 베드락 규칙),
 * 면마다 UV 는 면 쪽 (베드락 east = -x · west = +x, up = 위 · down = 아래) 의 사각형으로 (위 · 아래 면은 180° 돌린 것 — 베드락 저장 규칙).
 */
public final class BedrockLooks {
    private BedrockLooks() {}

    public static final Identifier SOURCE = Mmorpg.id("bedrock/entities.json");
    /** 덮는 색 (overlay_color) 을 그릴 흰 텍스처 */
    public static final Identifier WHITE = Mmorpg.id("textures/entity/white.png");

    /** 재질 */
    public enum Material { CUTOUT, BLEND }

    /** 엔티티 종류 하나의 모습 */
    public static final class Look {
        public final String id;
        public final Geo geo;
        public final Animator animator;
        public final RenderCtl ctl;
        /** 텍스처 이름 (default · day …) → 자바 텍스처 */
        public final Map<String, Identifier> textures;
        /** 텍스처 이름 → 빛나는 칸 텍스처 (없으면 없음) */
        public final Map<String, Identifier> glow;
        public final Material material;
        /** 모델 뿌리와 뼈마다 부분 (geo.bones 순서) · 기본 자세 */
        public final ModelPart root;
        public final ModelPart[] parts;
        public final PartPose[] base;

        Look(String id, Geo geo, Animator animator, RenderCtl ctl, Map<String, Identifier> textures, Map<String, Identifier> glow, Material material,
                ModelPart root, ModelPart[] parts, PartPose[] base) {
            this.id = id;
            this.geo = geo;
            this.animator = animator;
            this.ctl = ctl;
            this.textures = textures;
            this.glow = glow;
            this.material = material;
            this.root = root;
            this.parts = parts;
            this.base = base;
        }

        /** 이 재질의 렌더 타입 (텍스처 → 타입) */
        public Function<Identifier, RenderType> renderType() {
            return material == Material.BLEND ? RenderTypes::entityTranslucent : RenderTypes::entityCutout;
        }

        public Identifier texture(String key) {
            Identifier t = textures.get(key);
            return t != null ? t : textures.getOrDefault("default", textures.values().stream().findFirst().orElse(WHITE));
        }
    }

    private static ResourceManager lastManager;
    private static Map<String, Object> lastData;

    /** 자원 관리자에서 entities.json (리소스 팩이 덮을 수 있음 — 없으면 모드 jar) */
    static synchronized Map<String, Object> data(ResourceManager rm) {
        if (rm != null && rm == lastManager && lastData != null) return lastData;
        String text = null;
        if (rm != null) {
            try {
                Optional<Resource> r = rm.getResource(SOURCE);
                if (r.isPresent()) {
                    try (InputStream in = r.get().open()) {
                        text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            } catch (IOException | RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] bedrock entities: {}", e.toString());
            }
        }
        if (text == null) {
            try (InputStream in = BedrockLooks.class.getResourceAsStream("/assets/mmorpg/bedrock/entities.json")) {
                text = in == null ? "{}" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                text = "{}";
            }
        }
        lastManager = rm;
        lastData = Json.parseObject(text);
        return lastData;
    }

    /** 엔티티 ID (rpg:troll) 의 모습 (없으면 null) */
    @SuppressWarnings("unchecked")
    public static Look load(ResourceManager rm, String id) {
        Map<String, Object> data = data(rm);
        Map<String, Object> ents = map(data.get("entities"));
        Map<String, Object> d = map(ents.get(id));
        if (d.isEmpty()) return null;
        Map<String, Object> models = map(data.get("models"));
        String geoId = String.valueOf(map(d.get("geometry")).get("default"));
        Geo geo = Geo.parse(map(models.get(geoId)));
        MobData.Def def = MobData.get(id);
        List<String> props = def == null ? List.of() : def.propNames();
        Animator animator = new Animator(geo, d, map(data.get("animations")), props);
        RenderCtl ctl = RenderCtl.defaults();
        if (d.get("render_controllers") instanceof List<?> rcs && !rcs.isEmpty()) {
            Object rc = map(data.get("render_controllers")).get(String.valueOf(rcs.get(0)));
            if (rc instanceof Map<?, ?> m) ctl = RenderCtl.parse((Map<String, Object>) m, animator.ctx);
        }
        List<String> emissive = new ArrayList<>();
        for (Object o : data.get("emissive") instanceof List<?> l ? l : List.of()) emissive.add(String.valueOf(o));
        Map<String, Identifier> textures = new LinkedHashMap<>();
        Map<String, Identifier> glow = new LinkedHashMap<>();
        for (Map.Entry<String, Object> t : map(d.get("textures")).entrySet()) {
            String path = String.valueOf(t.getValue());
            String rel = path.startsWith("textures/") ? path : "textures/" + path;
            String key = t.getKey().toLowerCase(Locale.ROOT);
            textures.put(key, Mmorpg.id(rel + ".png"));
            if (emissive.contains(path)) glow.put(key, Mmorpg.id(rel + "_emissive.png"));
        }
        String mat = String.valueOf(map(d.get("materials")).getOrDefault("default", "entity_alphatest"));
        Material material = mat.contains("alphablend") ? Material.BLEND : Material.CUTOUT;
        ModelPart[] parts = new ModelPart[geo.bones.size()];
        PartPose[] base = new PartPose[geo.bones.size()];
        Map<String, ModelPart> roots = new LinkedHashMap<>();
        for (Geo.Bone b : geo.bones) {
            if (b.parent == null || geo.bone(b.parent) == null) roots.put(uniqueName(roots, b.name), bone(geo, b, parts, base));
        }
        ModelPart root = new ModelPart(List.of(), roots);
        return new Look(id, geo, animator, ctl, textures, glow, material, root, parts, base);
    }

    private static String uniqueName(Map<String, ?> m, String name) {
        String n = name;
        for (int i = 2; m.containsKey(n); i++) n = name + "#" + i;
        return n;
    }

    // ------------------------------------------------------------ 모델 → ModelPart

    private static ModelPart bone(Geo geo, Geo.Bone b, ModelPart[] parts, PartPose[] base) {
        List<ModelPart.Cube> cubes = new ArrayList<>();
        Map<String, ModelPart> children = new LinkedHashMap<>();
        int ci = 0;
        for (Geo.Cube c : b.cubes) {
            if (c.rotation != null && (c.rotation[0] != 0 || c.rotation[1] != 0 || c.rotation[2] != 0)) {
                // 상자 회전: 상자 회전 중심에 자식 부분을 두고 그 안에 상자
                double[] cp = c.pivot != null ? c.pivot : b.pivot;
                ModelPart sub = new ModelPart(List.of(cube(geo, c, cp)), Map.of());
                PartPose pose = PartPose.offsetAndRotation((float) (cp[0] - b.pivot[0]), (float) -(cp[1] - b.pivot[1]), (float) (cp[2] - b.pivot[2]),
                        rad(c.rotation[0]), rad(c.rotation[1]), rad(c.rotation[2]));
                sub.setInitialPose(pose);
                sub.loadPose(pose);
                children.put(uniqueName(children, b.name + "#cube" + ci), sub);
            } else {
                cubes.add(cube(geo, c, b.pivot));
            }
            ci++;
        }
        for (Geo.Bone k : geo.bones) {
            if (k.parent != null && k.parent.equalsIgnoreCase(b.name) && k != b) children.put(uniqueName(children, k.name), bone(geo, k, parts, base));
        }
        ModelPart part = new ModelPart(cubes, children);
        Geo.Bone parent = b.parent == null ? null : geo.bone(b.parent);
        float x;
        float y;
        float z;
        if (parent == null) {
            x = (float) b.pivot[0];
            y = (float) (24 - b.pivot[1]);
            z = (float) b.pivot[2];
        } else {
            x = (float) (b.pivot[0] - parent.pivot[0]);
            y = (float) -(b.pivot[1] - parent.pivot[1]);
            z = (float) (b.pivot[2] - parent.pivot[2]);
        }
        PartPose pose = PartPose.offsetAndRotation(x, y, z, rad(b.rotation[0]), rad(b.rotation[1]), rad(b.rotation[2]));
        part.setInitialPose(pose);
        part.loadPose(pose);
        parts[b.index] = part;
        base[b.index] = pose;
        return part;
    }

    /** 상자 하나 (회전 중심 pivot 에서 본 자바 좌표) */
    private static ModelPart.Cube cube(Geo geo, Geo.Cube c, double[] pivot) {
        float fx = (float) (c.origin[0] - pivot[0]);
        float fy = (float) (pivot[1] - c.origin[1] - c.size[1]);
        float fz = (float) (c.origin[2] - pivot[2]);
        float sx = (float) c.size[0];
        float sy = (float) c.size[1];
        float sz = (float) c.size[2];
        float g = (float) c.inflate;
        float tw = geo.textureWidth;
        float th = geo.textureHeight;
        if (c.faces == null) {
            int u = (int) Math.round(c.uv[0]);
            int v = (int) Math.round(c.uv[1]);
            ModelPart.Cube cube = new ModelPart.Cube(u, v, fx, fy, fz, sx, sy, sz, g, g, g, c.mirror, tw, th, EnumSet.allOf(Direction.class));
            if (sx != Math.floor(sx) || sy != Math.floor(sy) || sz != Math.floor(sz)) floorBoxUv(cube, u, v, sx, sy, sz, tw, th);
            return cube;
        }
        ModelPart.Cube cube = new ModelPart.Cube(0, 0, fx, fy, fz, sx, sy, sz, g, g, g, false, tw, th, EnumSet.allOf(Direction.class));
        for (int i = 0; i < cube.polygons.length; i++) {
            ModelPart.Polygon p = cube.polygons[i];
            Direction side = sideOf(p, cube);
            String face = switch (side) {
                case WEST -> "east";
                case EAST -> "west";
                case DOWN -> "up";
                case UP -> "down";
                case NORTH -> "north";
                case SOUTH -> "south";
            };
            double[] r = c.faces.get(face);
            ModelPart.Vertex[] vs = p.vertices();
            if (r == null) {
                cube.polygons[i] = new ModelPart.Polygon(new ModelPart.Vertex[] {vs[0], vs[0], vs[0], vs[0]}, p.normal());
                continue;
            }
            float u1 = (float) r[0];
            float v1 = (float) r[1];
            float u2 = (float) (r[0] + r[2]);
            float v2 = (float) (r[1] + r[3]);
            if (side == Direction.DOWN || side == Direction.UP) { // 베드락 면 UV 의 위 · 아래 면은 180° 돌려 저장됨
                float t = u1;
                u1 = u2;
                u2 = t;
                t = v1;
                v1 = v2;
                v2 = t;
            }
            cube.polygons[i] = new ModelPart.Polygon(vs, u1, v1, u2, v2, tw, th, false, side);
        }
        return cube;
    }

    /** 상자의 면이 어느 쪽인지 (네 꼭짓점이 같은 x · y · z 를 가진 축과 그쪽 끝) */
    private static Direction sideOf(ModelPart.Polygon p, ModelPart.Cube c) {
        ModelPart.Vertex[] v = p.vertices();
        float e = 1e-4F;
        if (Math.abs(v[0].x() - v[1].x()) < e && Math.abs(v[0].x() - v[2].x()) < e && Math.abs(v[0].x() - v[3].x()) < e) {
            return v[0].x() <= (c.minX + c.maxX) / 2 ? Direction.WEST : Direction.EAST;
        }
        if (Math.abs(v[0].y() - v[1].y()) < e && Math.abs(v[0].y() - v[2].y()) < e && Math.abs(v[0].y() - v[3].y()) < e) {
            return v[0].y() <= (c.minY + c.maxY) / 2 ? Direction.DOWN : Direction.UP;
        }
        return v[0].z() <= (c.minZ + c.maxZ) / 2 ? Direction.NORTH : Direction.SOUTH;
    }

    /**
     * 크기가 소수인 상자의 펼친 UV: 자바는 소수 크기 그대로 UV 를 펴지만 베드락 (Blockbench) 은 내린 크기로 펴므로, 면마다 UV 모서리를
     * 내린 크기의 같은 사각형 모서리로 옮김 (거울 · 면 방향은 그대로)
     */
    private static void floorBoxUv(ModelPart.Cube cube, int u, int v, float w, float h, float d, float tw, float th) {
        float[][] real = rects(u, v, w, h, d);
        float[][] fl = rects(u, v, (float) Math.floor(w), (float) Math.floor(h), (float) Math.floor(d));
        float e = 1e-3F;
        for (ModelPart.Polygon p : cube.polygons) {
            ModelPart.Vertex[] vs = p.vertices();
            int match = -1;
            for (int k = 0; k < real.length && match < 0; k++) {
                float[] r = real[k];
                boolean all = true;
                for (ModelPart.Vertex x : vs) {
                    float pu = x.u() * tw;
                    float pv = x.v() * th;
                    boolean onU = Math.abs(pu - r[0]) < e || Math.abs(pu - r[2]) < e;
                    boolean onV = Math.abs(pv - r[1]) < e || Math.abs(pv - r[3]) < e;
                    if (!onU || !onV) {
                        all = false;
                        break;
                    }
                }
                if (all) match = k;
            }
            if (match < 0) continue;
            float[] r = real[match];
            float[] f = fl[match];
            for (int i = 0; i < vs.length; i++) {
                float pu = vs[i].u() * tw;
                float pv = vs[i].v() * th;
                float nu = Math.abs(pu - r[0]) < e ? f[0] : f[2];
                float nv = Math.abs(pv - r[1]) < e ? f[1] : f[3];
                vs[i] = vs[i].remap(nu / tw, nv / th);
            }
        }
    }

    /** 자바 상자 UV 의 여섯 사각형 (아래 · 위 · 서 · 북 · 동 · 남 — 바닐라 Cube 와 같은 계산) */
    private static float[][] rects(float u, float v, float w, float h, float d) {
        return new float[][] {
                {u + d, v, u + d + w, v + d},
                {u + d + w, v + d, u + d + w + w, v},
                {u, v + d, u + d, v + d + h},
                {u + d, v + d, u + d + w, v + d + h},
                {u + d + w, v + d, u + d + w + d, v + d + h},
                {u + d + w + d, v + d, u + d + w + d + w, v + d + h}};
    }

    static float rad(double deg) {
        return (float) Math.toRadians(deg);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }
}
