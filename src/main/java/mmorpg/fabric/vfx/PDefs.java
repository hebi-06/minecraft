package mmorpg.fabric.vfx;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.Json;
import mmorpg.core.Molang;
import mmorpg.core.Molang.Expr;
import mmorpg.fabric.AddonFxTable;
import mmorpg.fabric.Mmorpg;

/**
 * 2026-11-12 애드온 파티클 정의 (서버 — 모드 없는 클라이언트의 파티클 다리 {@link PBridge}): 모드 클라이언트 (client AddonParticles) 와 같은 JSON
 * (assets/mmorpg/addon_particles.json) 을 같은 식 (mmorpg.core.Molang) 으로 풀어 둠 — 서버가 파티클마다 같은 계산 (발생기 · 수명 · 움직임 · 크기 · 색 · UV · 방향)
 * 을 하고 그 모습을 글자 표시 엔티티로 보냄. 그림은 글꼴 rpg:vfxp (tools/vfx_particles.py — mmorpg/vfx_particles.json 이 파티클 → 판 · 카드 → 겹).
 */
public final class PDefs {
    private PDefs() {}

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
    static final int VIEW = slot("view");
    static final int OWNER_HI = slot("owner_hi");
    static final int OWNER_LO = slot("owner_lo");
    static final int SOLO = slot("solo");

    private static int slot(String name) {
        int i = AddonFxTable.var(name);
        return i < 0 ? -1 : CUSTOM + i;
    }

    static final int SHAPE_POINT = 0;
    static final int SHAPE_SPHERE = 1;
    static final int SHAPE_BOX = 2;
    static final int SHAPE_DISC = 3;
    static final int DIR_OUT = 0;
    static final int DIR_IN = 1;
    static final int DIR_VEC = 2;
    static final int MOTION_NONE = 0;
    static final int MOTION_DYNAMIC = 1;
    static final int MOTION_PARAMETRIC = 2;
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

    /**
     * 글꼴 판 하나 (uv 칸 하나 = 글자 하나). 2026-11-12 픽셀 글꼴 (rpg:vfxq — 룬): 칸 크기 qW · qH · 글자 너비 qAdv · 흰 심지 (B 글자) 가 있는 칸 qB ("0101…" —
     * 없으면 null)
     */
    record Sheet(int first, int n, int cols, int rows, double uw, double vh, int pxW, int pxH, int adv, int qW, int qH, int qAdv, String qB) {
        /** uv (판 왼쪽 위) → 글자 (칸 밖이면 가장 가까운 칸) */
        int glyph(double u, double v) {
            int c = (int) Math.floor(u / uw + 1e-4);
            int r = (int) Math.floor(v / vh + 1e-4);
            c = Math.floorMod(c, Math.max(1, cols));
            r = Math.max(0, Math.min(rows - 1, r));
            int i = Math.min(n - 1, r * cols + c);
            return first + i;
        }

        /** 글자 가로세로 비 (픽셀) */
        double aspect() {
            return pxW / (double) pxH;
        }

        /** 픽셀 글꼴이 있는지 */
        boolean pixel() {
            return qB != null && qH > 0;
        }

        /** 픽셀 글꼴 글자의 가로세로 비 */
        double qAspect() {
            return qW / (double) qH;
        }

        /** 픽셀 글꼴에서 이 글자 (A) 에 흰 심지 (B) 가 있는지 */
        boolean qCore(int glyph) {
            int i = glyph - first;
            return qB != null && i >= 0 && i < qB.length() && qB.charAt(i) == '1';
        }
    }

    /** 카드 (Fx3d 판) 의 겹 하나: 글자 · mat (0 몸 = color, 1 빛 = glow) · 모델 자리 · 반 크기 */
    record Layer(int glyph, int mat, double x, double y, double z, double hw, double hh, int adv) {}

    /** 파티클 하나의 정의 */
    static final class Def {
        String id;
        String sprite;
        Sheet sheet;
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
        Expr[] color;
    }

    private static Def[] defs = new Def[0];
    private static final Map<String, List<Layer[]>> CARDS = new HashMap<>();
    /** 글꼴 */
    static String font = "rpg:vfxp";
    /** 2026-11-12 픽셀 글꼴 (룬) · B (흰 심지) · C (겹치는 심지 층) 글자 번호 차이 */
    static String qfont = "rpg:vfxq";
    static int qb = 0x400;
    static int qc = 0x800;
    static int glyphH = 64;
    static int spaceFirst = 0xF800;
    static int loaded;
    static int withGlyph;

    static Def def(int i) {
        return i >= 0 && i < defs.length ? defs[i] : null;
    }

    static List<Layer[]> card(String model) {
        return CARDS.get(model);
    }

    /** 서버를 켤 때 한 번 */
    @SuppressWarnings("unchecked")
    static void init() {
        Map<String, Object> glyphs = Map.of();
        try (InputStream in = PDefs.class.getResourceAsStream("/mmorpg/vfx_particles.json")) {
            if (in != null) glyphs = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Mmorpg.LOG.warn("[vfx] particle glyphs: {}", e.toString());
        }
        Map<String, Sheet> sheets = new HashMap<>();
        if (glyphs.get("font") instanceof String f) font = f;
        if (glyphs.get("h") instanceof Number n) glyphH = n.intValue();
        if (glyphs.get("space") instanceof Number n) spaceFirst = n.intValue();
        if (glyphs.get("qfont") instanceof String f) qfont = f;
        if (glyphs.get("qb") instanceof Number n) qb = n.intValue();
        if (glyphs.get("qc") instanceof Number n) qc = n.intValue();
        if (glyphs.get("sheets") instanceof Map<?, ?> sm) {
            for (Map.Entry<?, ?> e : sm.entrySet()) {
                Map<String, Object> m = (Map<String, Object>) e.getValue();
                List<?> px = (List<?>) m.get("px");
                Map<String, Object> q = m.get("q") instanceof Map<?, ?> qm ? (Map<String, Object>) qm : null;
                List<?> qpx = q != null && q.get("px") instanceof List<?> l ? l : null;
                sheets.put((String) e.getKey(), new Sheet(num(m.get("first")), num(m.get("n")), num(m.get("cols")), num(m.get("rows")), dbl(m.get("uw")),
                        dbl(m.get("vh")), num(px.get(0)), num(px.get(1)), num(m.get("adv")), qpx != null ? num(qpx.get(0)) : 0, qpx != null ? num(qpx.get(1)) : 0,
                        q != null ? num(q.get("adv")) : 0, q != null && q.get("b") instanceof String b ? b : null));
            }
        }
        Map<String, Object> ofSheet = glyphs.get("particles") instanceof Map<?, ?> pm ? (Map<String, Object>) pm : Map.of();
        if (glyphs.get("cards") instanceof Map<?, ?> cm) {
            for (Map.Entry<?, ?> e : cm.entrySet()) {
                List<Layer[]> frames = new java.util.ArrayList<>();
                for (Object fr : (List<?>) e.getValue()) {
                    List<?> ls = (List<?>) fr;
                    Layer[] out = new Layer[ls.size()];
                    for (int i = 0; i < out.length; i++) {
                        Map<String, Object> l = (Map<String, Object>) ls.get(i);
                        List<?> at = (List<?>) l.get("at");
                        List<?> half = (List<?>) l.get("half");
                        out[i] = new Layer(num(l.get("c")), num(l.get("mat")), dbl(at.get(0)), dbl(at.get(1)), dbl(at.get(2)), dbl(half.get(0)), dbl(half.get(1)),
                                num(l.get("adv")));
                    }
                    frames.add(out);
                }
                CARDS.put((String) e.getKey(), frames);
            }
        }
        String text;
        try (InputStream in = PDefs.class.getResourceAsStream("/assets/mmorpg/addon_particles.json")) {
            if (in == null) throw new IOException("missing addon_particles.json");
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Mmorpg.LOG.warn("[vfx] add-on particles not loaded: {}", e.toString());
            return;
        }
        Map<?, ?> particles = (Map<?, ?>) Json.parseObject(text).get("particles");
        Def[] out = new Def[AddonFxTable.PARTICLES.length];
        int ok = 0;
        int g = 0;
        for (int i = 0; i < out.length; i++) {
            String id = AddonFxTable.PARTICLES[i];
            if (!(particles.get(id) instanceof Map<?, ?> m)) continue;
            try {
                out[i] = compile(id, m);
                Object key = ofSheet.get(id);
                if (key != null) out[i].sheet = sheets.get(key);
                if (out[i].sheet != null) g++;
                ok++;
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[vfx] add-on particle {}: {}", id, e.getMessage());
            }
        }
        defs = out;
        loaded = ok;
        withGlyph = g;
        Mmorpg.LOG.info("[vfx] particle bridge: {} defs, {} with glyphs, {} cards", ok, g, CARDS.size());
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static double dbl(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
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

    private static double[] nums(Object o) {
        List<?> l = (List<?>) o;
        return new double[] {num(l.get(0), 0), num(l.get(1), 0)};
    }

    /** client AddonParticles.compile 과 같음 (그림 · 층 대신 판) */
    private static Def compile(String id, Map<?, ?> m) {
        Def d = new Def();
        d.id = id;
        d.sprite = (String) m.get("sprite");
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
}
