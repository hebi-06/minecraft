package mmorpg.fabric.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import mmorpg.core.Json;
import mmorpg.core.Molang;
import mmorpg.core.Molang.Expr;
import mmorpg.fabric.AddonFxTable;
import mmorpg.fabric.AddonParticleOptions;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 애드온 파티클 119종 (mmorpg:addon): assets/mmorpg/addon_particles.json (tools/addon_particles.py — 애드온 particle.json 그대로) 을 읽어
 * Molang 식을 미리 풀어 두고, 서버가 보낸 번호 · 변수로 베드락과 같은 계산 (발생기 · 수명 · 속도 · 회전 · 움직임 · 크기 · UV · 색) 으로 그림.
 * 변수 칸: 0~11 파티클 · 발생기 값 (particle_age … emitter_random_4), 12~ 서버가 보내는 변수 (AddonFxTable.VARS 순서).
 */
public final class AddonParticles {
    private AddonParticles() {}

    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-addon-particles");

    static final String[] BUILTIN = {"particle_age", "particle_lifetime", "particle_random_1", "particle_random_2", "particle_random_3", "particle_random_4",
        "emitter_age", "emitter_lifetime", "emitter_random_1", "emitter_random_2", "emitter_random_3", "emitter_random_4"};
    static final int AGE = 0;
    static final int LIFETIME = 1;
    static final int PR1 = 2;
    static final int EAGE = 6;
    static final int ELIFE = 7;
    static final int ER1 = 8;
    static final int CUSTOM = BUILTIN.length;
    static final int SLOTS = CUSTOM + AddonFxTable.VARS.length;

    // 발생기 모양 · 방향
    static final int SHAPE_POINT = 0;
    static final int SHAPE_SPHERE = 1;
    static final int SHAPE_BOX = 2;
    static final int SHAPE_DISC = 3;
    static final int DIR_OUT = 0;
    static final int DIR_IN = 1;
    static final int DIR_VEC = 2;
    // 움직임
    static final int MOTION_NONE = 0;
    static final int MOTION_DYNAMIC = 1;
    static final int MOTION_PARAMETRIC = 2;
    // 빌보드 방향
    static final int ROTATE_XYZ = 0;
    static final int ROTATE_Y = 1;
    static final int LOOKAT_XYZ = 2;
    static final int LOOKAT_Y = 3;
    static final int DIRECTION_X = 4;
    static final int DIRECTION_Y = 5;
    static final int DIRECTION_Z = 6;
    static final int LOOKAT_DIRECTION = 7;
    static final int EMITTER_XZ = 8;
    static final int EMITTER_XY = 9;
    static final int EMITTER_YZ = 10;

    private static final Map<String, Integer> FACING = Map.ofEntries(Map.entry("rotate_xyz", ROTATE_XYZ), Map.entry("rotate_y", ROTATE_Y),
            Map.entry("lookat_xyz", LOOKAT_XYZ), Map.entry("lookat_y", LOOKAT_Y), Map.entry("direction_x", DIRECTION_X),
            Map.entry("direction_y", DIRECTION_Y), Map.entry("direction_z", DIRECTION_Z), Map.entry("lookat_direction", LOOKAT_DIRECTION),
            Map.entry("emitter_transform_xz", EMITTER_XZ), Map.entry("emitter_transform_xy", EMITTER_XY), Map.entry("emitter_transform_yz", EMITTER_YZ));

    private static final Molang.Vars VARS = name -> {
        for (int i = 0; i < BUILTIN.length; i++) if (BUILTIN[i].equals(name)) return i;
        int s = AddonFxTable.var(name);
        return s < 0 ? -1 : CUSTOM + s;
    };

    /** 파티클 하나의 정의 (식은 미리 풀어 둠) */
    static final class Def {
        String id;
        String sprite;
        SingleQuadParticle.Layer layer;
        boolean instant;
        Expr rate;
        Expr max;
        Expr active;
        int shape;
        Expr[] offset;
        Expr radius;
        Expr[] half;
        Expr[] normal;
        boolean surface;
        int dirMode;
        Expr[] dirVec;
        Expr life;
        Expr speed;
        Expr spin0;
        Expr spinRate;
        int motion;
        Expr[] accel;
        Expr drag;
        Expr[] rel;
        Expr sizeX;
        Expr sizeY;
        int facing;
        Expr[] faceDir;
        double minSpeed = 0.01;
        boolean flipbook;
        Expr[] uv;
        Expr[] uvSize;
        Expr[] fbBase;
        double[] fbSize;
        double[] fbStep;
        Expr fps;
        Expr maxFrame;
        boolean stretch;
        boolean loop;
        double tw;
        double th;
        Expr[] color;
    }

    private static Def[] defs = new Def[0];
    private static List<TextureAtlasSprite> cachedList;
    private static Map<String, TextureAtlasSprite> cachedSprites = Map.of();

    /** 클라이언트 시작 때 한 번 (식을 읽다 실패한 파티클은 빠지고 로그) */
    public static void init() {
        String text;
        try (InputStream in = AddonParticles.class.getResourceAsStream("/assets/mmorpg/addon_particles.json")) {
            if (in == null) throw new IOException("missing /assets/mmorpg/addon_particles.json");
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("[mmorpg] add-on particles not loaded", e);
            return;
        }
        Map<String, Object> root = Json.parseObject(text);
        Map<?, ?> particles = (Map<?, ?>) root.get("particles");
        Def[] out = new Def[AddonFxTable.PARTICLES.length];
        int ok = 0;
        for (int i = 0; i < out.length; i++) {
            String id = AddonFxTable.PARTICLES[i];
            Object raw = particles.get(id);
            if (!(raw instanceof Map<?, ?> m)) {
                LOG.warn("[mmorpg] add-on particle {} has no definition", id);
                continue;
            }
            try {
                out[i] = compile(id, m);
                ok++;
            } catch (RuntimeException e) {
                LOG.error("[mmorpg] add-on particle {}: {}", id, e.getMessage());
            }
        }
        defs = out;
        LOG.info("[mmorpg] add-on particles: {} / {}", ok, out.length);
    }

    static Def def(int i) {
        return i >= 0 && i < defs.length ? defs[i] : null;
    }

    private static Expr e(Object o) {
        return Molang.of(o, VARS);
    }

    private static Expr[] vec(Object o, int n) {
        Expr[] r = new Expr[n];
        List<?> l = o instanceof List<?> list ? list : List.of();
        for (int i = 0; i < n; i++) r[i] = e(i < l.size() ? l.get(i) : 0);
        return r;
    }

    private static double num(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    private static Def compile(String id, Map<?, ?> m) {
        Def d = new Def();
        d.id = id;
        d.sprite = (String) m.get("sprite");
        String material = (String) m.get("material");
        d.layer = "alpha".equals(material) ? SingleQuadParticle.Layer.OPAQUE : SingleQuadParticle.Layer.TRANSLUCENT;
        Map<?, ?> rate = (Map<?, ?>) m.get("rate");
        d.instant = rate.containsKey("instant");
        d.rate = e(d.instant ? rate.get("instant") : rate.get("steady"));
        d.max = e(rate.containsKey("max") ? rate.get("max") : 50);
        d.active = e(m.get("active"));
        Map<?, ?> shape = (Map<?, ?>) m.get("shape");
        d.shape = switch ((String) shape.get("type")) {
            case "sphere" -> SHAPE_SPHERE;
            case "box" -> SHAPE_BOX;
            case "disc" -> SHAPE_DISC;
            default -> SHAPE_POINT;
        };
        d.offset = shape.containsKey("offset") ? vec(shape.get("offset"), 3) : null;
        d.radius = e(shape.containsKey("radius") ? shape.get("radius") : 0);
        d.half = vec(shape.get("half"), 3);
        d.normal = shape.containsKey("normal") ? vec(shape.get("normal"), 3) : new Expr[] {Molang.constant(0), Molang.constant(1), Molang.constant(0)};
        d.surface = Boolean.TRUE.equals(shape.get("surface"));
        Object dir = shape.get("dir");
        if (dir instanceof List<?>) {
            d.dirMode = DIR_VEC;
            d.dirVec = vec(dir, 3);
        } else {
            d.dirMode = "inwards".equals(dir) ? DIR_IN : DIR_OUT;
        }
        d.life = e(m.get("life"));
        d.speed = e(m.get("speed"));
        if (m.get("spin") instanceof List<?> spin) {
            d.spin0 = e(spin.get(0));
            d.spinRate = e(spin.get(1));
        }
        if (m.get("motion") instanceof Map<?, ?> motion) {
            if (motion.containsKey("parametric")) {
                d.motion = MOTION_PARAMETRIC;
                d.rel = vec(motion.get("parametric"), 3);
            } else {
                d.motion = MOTION_DYNAMIC;
                d.accel = vec(motion.get("accel"), 3);
                d.drag = e(motion.get("drag"));
            }
        }
        List<?> size = (List<?>) m.get("size");
        d.sizeX = e(size.get(0));
        d.sizeY = e(size.get(1));
        Integer f = FACING.get((String) m.get("facing"));
        if (f == null) throw new IllegalArgumentException("facing " + m.get("facing"));
        d.facing = f;
        if (m.containsKey("face_dir")) d.faceDir = vec(m.get("face_dir"), 3);
        d.minSpeed = num(m.get("min_speed"), 0.01);
        Map<?, ?> uv = (Map<?, ?>) m.get("uv");
        d.tw = num(uv.get("tw"), 1);
        d.th = num(uv.get("th"), 1);
        if (uv.get("flipbook") instanceof Map<?, ?> fb) {
            d.flipbook = true;
            d.fbBase = vec(fb.get("base"), 2);
            d.fbSize = nums(fb.get("size"));
            d.fbStep = nums(fb.get("step"));
            d.fps = e(fb.get("fps"));
            d.maxFrame = e(fb.get("max"));
            d.stretch = Boolean.TRUE.equals(fb.get("stretch"));
            d.loop = Boolean.TRUE.equals(fb.get("loop"));
        } else {
            d.uv = vec(uv.get("static"), 2);
            d.uvSize = vec(uv.get("size"), 2);
        }
        d.color = vec(m.get("color"), 4);
        return d;
    }

    private static double[] nums(Object o) {
        List<?> l = (List<?>) o;
        return new double[] {num(l.get(0), 0), num(l.get(1), 0)};
    }

    /** 그림 이름 → 아틀라스 그림 (FabricSpriteSet 이 바뀌면 — 리소스를 다시 읽으면 — 다시 만듦) */
    static Map<String, TextureAtlasSprite> sprites(FabricSpriteSet set) {
        List<TextureAtlasSprite> list = set.getSprites();
        if (list != cachedList) {
            Map<String, TextureAtlasSprite> m = new HashMap<>();
            for (TextureAtlasSprite s : list) m.put(s.contents().name().toString(), s);
            cachedSprites = m;
            cachedList = list;
        }
        return cachedSprites;
    }

    /** 서버가 보낸 파티클 하나 (베드락 spawnParticle) → 알갱이 (한 번에 여럿이면 나머지는 엔진에 바로 넣음) 또는 계속 뿜는 발생기 */
    public static Particle create(ClientLevel level, double x, double y, double z, AddonParticleOptions o, FabricSpriteSet set) {
        Def d = def(o.particle());
        if (d == null) return null;
        TextureAtlasSprite sprite = sprites(set).get(d.sprite);
        if (sprite == null) sprite = set.getSprites().get(0);
        double[] ev = new double[SLOTS];
        for (int i = 0; i < o.slots().length; i++) {
            int s = CUSTOM + o.slots()[i];
            if (s >= CUSTOM && s < SLOTS) ev[s] = o.values()[i];
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0; k < 4; k++) ev[ER1 + k] = r.nextDouble();
        ev[ELIFE] = d.active.eval(ev);
        Vec3 origin = new Vec3(x, y, z);
        if (!d.instant) return new AddonEmitter(level, origin, d, ev, sprite);
        int n = (int) Math.round(d.rate.eval(ev));
        if (n <= 0) return null;
        Particle first = null;
        for (int i = 0; i < n; i++) {
            AddonParticle p = new AddonParticle(level, origin, d, ev, 0, sprite);
            if (first == null) first = p;
            else Minecraft.getInstance().particleEngine.add(p);
        }
        return first;
    }
}
