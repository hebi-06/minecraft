package mmorpg.fabric.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mmorpg.fabric.Fx3d;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.client.motion.ClientMotions;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 0.47.0 입체 효과 물체 (서버 Fx3d — 사용자 "효과 물체를 입체 모델로"): 모델 (assets/mmorpg/fxmodel/&lt;이름&gt;.json — tools/fx_models.py 의 사각형 면 ·
 * 재질 · 부분 · 움직임) 을 세계에 그림 (LevelRenderer.submitEntities 끝 — 참격 궤적과 같은 자리, 카메라 기준).
 * <ul>
 *   <li>몸: 반투명 엔티티 그리기 (빛을 받아 면마다 밝기가 다름 — 입체로 보임, 밝기 최대). 재질 색 = 빛깔 (부른 색) · 속빛 · 흰빛 · 짙은 빛깔 · 쇠 · 금 · 나무 · 뼈 · 그림자 ·
 *       짙은 속빛 × 그림의 밝기</li>
 *   <li>더한 빛 (shine): 같은 면을 속빛으로 한 번 더 더함 (빛나는 물체) · 빛 무리 (모델의 glow 점 — 카메라를 보는 둥근 빛, 더하기 섞기)</li>
 *   <li>움직임: 곧게 (vel · acc) 또는 곡선 길 (at → ctrl → to, travel 초 · ease), follow 면 주인 기준 [오른쪽, 위, 앞] (주인의 보는 쪽을 따라 돎).
 *       방향: dir (모델 +Y) · face (모델 +Z) · roll · align (가는 쪽) · faceCam (넓은 면이 카메라 쪽) · spin (축 y · x · z · 세계 위)</li>
 *   <li>크기 pop (튀어나옴) · grow · shrink, 투명 fadeIn · fadeOut, 모델 움직임 (부분마다 돌림 · 옮김 · 크기 열쇠 — 책장 · 문짝 · 종 흔들림)</li>
 * </ul>
 */
public final class FxModels {
    private FxModels() {}

    private static final int LIGHT = 0xF000F0;
    private static final Identifier HALO = Mmorpg.id("textures/fx/model/halo.png");
    /** 그리는 거리 (칸) · 한꺼번에 둘 수 있는 수 */
    private static final double RANGE = 112;
    private static final int MAX = 700;
    /** 재질 4 ~ 8 의 색 (쇠 · 금 · 나무 · 뼈 · 그림자) */
    private static final float[][] FIXED = {{0.76F, 0.79F, 0.84F}, {1.0F, 0.8F, 0.36F}, {0.55F, 0.38F, 0.22F}, {0.92F, 0.89F, 0.8F}, {0.13F, 0.1F, 0.17F}};

    // ------------------------------------------------------------ 모델

    static final class Part {
        String name;
        float px;
        float py;
        float pz;
        /** 사각형마다 꼭짓점 넷 × (x, y, z, u, v, nx, ny, nz) */
        float[] v;
        int[] mat;
    }

    static final class Track {
        float[][] rot;
        float[][] pos;
        float[][] scale;
    }

    static final class Anim {
        float len = 1;
        final Map<String, Track> tracks = new HashMap<>();
    }

    static final class Model {
        String name;
        Identifier tex;
        final List<Part> parts = new ArrayList<>();
        float[][] glow = new float[0][];
        final Map<String, Anim> anims = new HashMap<>();
    }

    private static final Map<String, Model> MODELS = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    /** 모델 읽기 (처음 쓸 때 — 없으면 null, 한 번만 알림) */
    static Model model(String name) {
        Model m = MODELS.get(name);
        if (m != null || MISSING.contains(name)) return m;
        String path = "/assets/mmorpg/fxmodel/" + name + ".json";
        try (InputStream in = FxModels.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            m = parse(name, o);
            MODELS.put(name, m);
        } catch (Exception e) {
            MISSING.add(name);
            Mmorpg.LOG.warn("[mmorpg] fx model {}: {}", name, e.toString());
        }
        return m;
    }

    private static float[][] keys(JsonArray a) {
        if (a == null) return null;
        float[][] out = new float[a.size()][];
        for (int i = 0; i < a.size(); i++) {
            JsonArray k = a.get(i).getAsJsonArray();
            out[i] = new float[k.size()];
            for (int j = 0; j < k.size(); j++) out[i][j] = k.get(j).getAsFloat();
        }
        return out;
    }

    static Model parse(String name, JsonObject o) {
        Model m = new Model();
        m.name = name;
        String tex = o.get("tex").getAsString();
        m.tex = Mmorpg.id(tex.startsWith("mmorpg:") ? tex.substring(7) : tex);
        JsonArray glow = o.getAsJsonArray("glow");
        if (glow != null) m.glow = keys(glow);
        for (JsonElement pe : o.getAsJsonArray("parts")) {
            JsonObject po = pe.getAsJsonObject();
            Part p = new Part();
            p.name = po.get("name").getAsString();
            JsonArray pv = po.getAsJsonArray("pivot");
            p.px = pv.get(0).getAsFloat();
            p.py = pv.get(1).getAsFloat();
            p.pz = pv.get(2).getAsFloat();
            JsonArray qs = po.getAsJsonArray("q");
            p.v = new float[qs.size() * 32];
            p.mat = new int[qs.size()];
            for (int i = 0; i < qs.size(); i++) {
                JsonArray q = qs.get(i).getAsJsonArray();
                for (int j = 0; j < 32; j++) p.v[i * 32 + j] = q.get(j).getAsFloat();
                p.mat[i] = q.get(32).getAsInt();
            }
            m.parts.add(p);
        }
        JsonObject anims = o.getAsJsonObject("anims");
        if (anims != null) {
            for (String an : anims.keySet()) {
                JsonObject ao = anims.getAsJsonObject(an);
                Anim a = new Anim();
                a.len = ao.has("len") ? Math.max(0.01F, ao.get("len").getAsFloat()) : 1F;
                JsonObject tracks = ao.getAsJsonObject("tracks");
                for (String pn : tracks.keySet()) {
                    JsonObject to = tracks.getAsJsonObject(pn);
                    Track t = new Track();
                    t.rot = keys(to.getAsJsonArray("rot"));
                    t.pos = keys(to.getAsJsonArray("pos"));
                    t.scale = keys(to.getAsJsonArray("scale"));
                    a.tracks.put(pn, t);
                }
                m.anims.put(an, a);
            }
        }
        return m;
    }

    // ------------------------------------------------------------ 띄운 것

    static final class Inst {
        int id;
        Model m;
        Anim anim;
        int owner;
        int flags;
        float[] v;
        double x;
        double y;
        double z;
        double born;
        double killAt = -1;
        float killFade;
        Vec3 lastBase;
        float lastYaw;
    }

    private static final List<Inst> LIVE = new ArrayList<>();
    /** 시험: 받은 모델 수 · 지난 그림에서 그린 모델 수 · 사각형 수 */
    public static int received;
    public static int lastDrawn;
    public static int lastQuads;

    private static double now() {
        return ClientMotions.ticks();
    }

    /** 서버가 보낸 것 (띄움 · 지움) */
    public static void receive(Fx3d.Payload p) {
        if ((p.flags() & Fx3d.F_KILL) != 0) {
            double t = now();
            for (Inst i : LIVE) {
                if (i.id == p.id() && i.killAt < 0) {
                    i.killAt = t;
                    i.killFade = p.v()[30];
                }
            }
            return;
        }
        Model m = model(p.model());
        if (m == null) return;
        Inst i = new Inst();
        i.id = p.id();
        i.m = m;
        i.anim = p.anim().isEmpty() ? null : m.anims.get(p.anim());
        i.owner = p.owner();
        i.flags = p.flags();
        i.v = p.v();
        i.x = p.x();
        i.y = p.y();
        i.z = p.z();
        i.born = now();
        LIVE.add(i);
        received++;
        while (LIVE.size() > MAX) LIVE.remove(0);
    }

    public static void clear() {
        LIVE.clear();
    }

    /** 시험: 지금 떠 있는 모델 수 (이름이 null 이면 모두) */
    public static int live(String name) {
        int n = 0;
        for (Inst i : LIVE) if (name == null || i.m.name.equals(name)) n++;
        return n;
    }

    // ------------------------------------------------------------ 그리기

    /** 꼭짓점 모음 (그림 하나 · 그리기 하나) */
    static final class Buf {
        float[] d = new float[2048];
        int[] c = new int[256];
        int n;

        void add(float x, float y, float z, float u, float v, float nx, float ny, float nz, int argb) {
            if ((n + 1) * 8 > d.length) d = java.util.Arrays.copyOf(d, d.length * 2);
            if (n + 1 > c.length) c = java.util.Arrays.copyOf(c, c.length * 2);
            int i = n * 8;
            d[i] = x;
            d[i + 1] = y;
            d[i + 2] = z;
            d[i + 3] = u;
            d[i + 4] = v;
            d[i + 5] = nx;
            d[i + 6] = ny;
            d[i + 7] = nz;
            c[n++] = argb;
        }
    }

    private static float ease(int kind, float k) {
        k = Mth.clamp(k, 0F, 1F);
        return switch (kind) {
            case 1 -> k * k;
            case 2 -> 1 - (1 - k) * (1 - k);
            case 3 -> k * k * (3 - 2 * k);
            case 4 -> 1 - (1 - k) * (1 - k) * (1 - k);
            case 5 -> {
                float c1 = 1.70158F;
                float c3 = c1 + 1;
                float x = k - 1;
                yield 1 + c3 * x * x * x + c1 * x * x;
            }
            default -> k;
        };
    }

    private static int argb(float a, float r, float g, float b) {
        int ai = Mth.clamp((int) (a * 255 + 0.5F), 0, 255);
        int ri = Mth.clamp((int) (r * 255 + 0.5F), 0, 255);
        int gi = Mth.clamp((int) (g * 255 + 0.5F), 0, 255);
        int bi = Mth.clamp((int) (b * 255 + 0.5F), 0, 255);
        return ai << 24 | ri << 16 | gi << 8 | bi;
    }

    /** 열쇠들 사이 (t 오름 — 첫 값은 t, 나머지는 값) — 부드럽게 (smoothstep) */
    private static float[] sample(float[][] keys, float u, int n) {
        float[] out = new float[n];
        if (keys == null || keys.length == 0) return null;
        if (u <= keys[0][0]) {
            System.arraycopy(keys[0], 1, out, 0, n);
            return out;
        }
        for (int i = 0; i + 1 < keys.length; i++) {
            float[] a = keys[i];
            float[] b = keys[i + 1];
            if (u <= b[0]) {
                float k = (u - a[0]) / Math.max(1e-5F, b[0] - a[0]);
                k = k * k * (3 - 2 * k);
                for (int j = 0; j < n; j++) out[j] = a[j + 1] + (b[j + 1] - a[j + 1]) * k;
                return out;
            }
        }
        System.arraycopy(keys[keys.length - 1], 1, out, 0, n);
        return out;
    }

    /** LevelRenderer.submitEntities 끝: 떠 있는 모델을 모두 그림 (카메라 기준 자리) */
    public static void submitWorld(PoseStack ps, SubmitNodeCollector collector) {
        if (LIVE.isEmpty()) {
            lastDrawn = 0;
            lastQuads = 0;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            LIVE.clear();
            return;
        }
        try {
            draw(mc, level, ps, collector);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] fx models", e);
            LIVE.clear();
        }
    }

    private static void draw(Minecraft mc, ClientLevel level, PoseStack ps, SubmitNodeCollector collector) {
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = ClientMotions.ticks() + partial;
        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 cp = cam.position();
        Quaternionf cq = new Quaternionf(cam.rotation());
        Vector3f camRight = cq.transform(new Vector3f(1, 0, 0));
        Vector3f camUp = cq.transform(new Vector3f(0, 1, 0));
        Entity camEnt = mc.getCameraEntity();
        boolean fp = mc.options.getCameraType().isFirstPerson();
        Map<Identifier, Buf> bodies = new LinkedHashMap<>();
        Map<Identifier, Buf> shines = new LinkedHashMap<>();
        Buf halos = new Buf();
        int drawn = 0;
        int quads = 0;
        Matrix4f M = new Matrix4f();
        Matrix4f MP = new Matrix4f();
        Matrix3f R = new Matrix3f();
        Matrix3f RP = new Matrix3f();
        Vector3f tmp = new Vector3f();
        Vector3f nrm = new Vector3f();
        float[] col = new float[30];
        Iterator<Inst> it = LIVE.iterator();
        while (it.hasNext()) {
            Inst in = it.next();
            float[] v = in.v;
            double t = (now - in.born) / 20.0 - v[41];
            float life = v[23];
            float killK = 1F;
            if (in.killAt >= 0) {
                double kt = (now - in.killAt) / 20.0;
                if (kt >= in.killFade) {
                    it.remove();
                    continue;
                }
                killK = in.killFade <= 0 ? 0F : (float) (1 - kt / in.killFade);
            }
            if (t > life) {
                it.remove();
                continue;
            }
            if (t < 0 || killK <= 0) continue;
            int view = (in.flags >> Fx3d.VIEW_SHIFT) & 3;
            if (view != 0) {
                boolean ownerFp = fp && camEnt != null && camEnt.getId() == in.owner;
                if (view == 1 && ownerFp || view == 2 && !ownerFp) continue;
            }
            float tf = (float) t;
            float tn = Mth.clamp(tf / Math.max(0.01F, life), 0F, 1F);
            // 자리 (주인 기준이면 [오른쪽, 위, 앞])
            float lx;
            float ly;
            float lz;
            float gx;
            float gy;
            float gz;
            if ((in.flags & Fx3d.F_PATH) != 0) {
                float e = ease((int) v[22], tf / Math.max(0.01F, v[21]));
                float a = (1 - e) * (1 - e);
                float b = 2 * (1 - e) * e;
                float c = e * e;
                lx = (float) (a * in.x + b * v[18] + c * v[15]);
                ly = (float) (a * in.y + b * v[19] + c * v[16]);
                lz = (float) (a * in.z + b * v[20] + c * v[17]);
                gx = (float) (2 * (1 - e) * (v[18] - in.x) + 2 * e * (v[15] - v[18]));
                gy = (float) (2 * (1 - e) * (v[19] - in.y) + 2 * e * (v[16] - v[19]));
                gz = (float) (2 * (1 - e) * (v[20] - in.z) + 2 * e * (v[17] - v[20]));
            } else {
                lx = (float) (in.x + v[9] * t + 0.5 * v[12] * t * t);
                ly = (float) (in.y + v[10] * t + 0.5 * v[13] * t * t);
                lz = (float) (in.z + v[11] * t + 0.5 * v[14] * t * t);
                gx = v[9] + v[12] * tf;
                gy = v[10] + v[13] * tf;
                gz = v[11] + v[14] * tf;
            }
            double wx;
            double wy;
            double wz;
            Vector3f dir = new Vector3f(v[0], v[1], v[2]);
            Vector3f face = new Vector3f(v[3], v[4], v[5]);
            Vector3f tan = new Vector3f(gx, gy, gz);
            if ((in.flags & Fx3d.F_FOLLOW) != 0) {
                Entity o = level.getEntity(in.owner);
                if (o != null && !o.isRemoved()) {
                    in.lastBase = o.getPosition(partial);
                    in.lastYaw = o.getViewYRot(partial);
                }
                if (in.lastBase == null) continue;
                float yr = in.lastYaw * Mth.DEG_TO_RAD;
                float sy = Mth.sin(yr);
                float cy = Mth.cos(yr);
                // 오른쪽 (-cos, 0, -sin) · 위 · 앞 (-sin, 0, cos)
                wx = in.lastBase.x + (-cy) * lx + (-sy) * lz;
                wy = in.lastBase.y + ly;
                wz = in.lastBase.z + (-sy) * lx + cy * lz;
                dir = local(dir, sy, cy);
                face = local(face, sy, cy);
                tan = local(tan, sy, cy);
            } else {
                wx = lx;
                wy = ly;
                wz = lz;
            }
            double dx = wx - cp.x;
            double dy = wy - cp.y;
            double dz = wz - cp.z;
            if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) continue;
            // 방향: Y = dir (또는 가는 쪽), Z = face (또는 카메라 · 위), X = Y × Z
            Vector3f Y = ((in.flags & Fx3d.F_ALIGN) != 0 && tan.lengthSquared() > 1e-6F) ? new Vector3f(tan) : new Vector3f(dir);
            if (Y.lengthSquared() < 1e-8F) Y.set(0, 1, 0);
            Y.normalize();
            Vector3f Z;
            if ((in.flags & Fx3d.F_FACE_CAM) != 0) Z = new Vector3f((float) -dx, (float) -dy, (float) -dz);
            else if (face.lengthSquared() > 1e-6F) Z = new Vector3f(face);
            else Z = new Vector3f(0, 1, 0);
            Z.sub(new Vector3f(Y).mul(Z.dot(Y)));
            if (Z.lengthSquared() < 1e-6F) {
                Z.set(Math.abs(Y.y) < 0.9F ? 0 : 1, Math.abs(Y.y) < 0.9F ? 1 : 0, 0);
                Z.sub(new Vector3f(Y).mul(Z.dot(Y)));
            }
            Z.normalize();
            Vector3f X = new Vector3f(Y).cross(Z).normalize();
            R.set(X, Y, Z);
            if (v[6] != 0) R.rotateY(v[6] * Mth.DEG_TO_RAD);
            float spin = v[7] * tf * Mth.DEG_TO_RAD;
            if (spin != 0) {
                switch ((int) v[8]) {
                    case 1 -> R.rotateX(spin);
                    case 2 -> R.rotateZ(spin);
                    case 3 -> R.set(new Matrix3f().rotationY(spin).mul(R));
                    default -> R.rotateY(spin);
                }
            }
            // 크기 · 투명
            float s = v[24];
            if (v[26] > 0) s *= Math.max(0.01F, ease(5, tf / v[26]));
            if (v[25] != 0) s *= 1 + v[25] * tn;
            if (v[27] > 0) s *= Mth.clamp((life - tf) / v[27], 0F, 1F);
            float a = v[28] * killK;
            if (v[29] > 0) a *= Mth.clamp(tf / v[29], 0F, 1F);
            if (v[30] > 0) a *= Mth.clamp((life - tf) / v[30], 0F, 1F);
            if (a <= 0.004F || s <= 0.001F) continue;
            M.identity().translate((float) dx, (float) dy, (float) dz).mul(new Matrix4f().set(R)).scale(s);
            // 재질 색
            float cr = v[31];
            float cg = v[32];
            float cb = v[33];
            float hr = v[34];
            float hg = v[35];
            float hb = v[36];
            setCol(col, 0, cr, cg, cb);
            setCol(col, 1, hr, hg, hb);
            setCol(col, 2, 1, 1, 1);
            setCol(col, 3, cr * 0.45F, cg * 0.45F, cb * 0.45F);
            for (int k = 0; k < FIXED.length; k++) setCol(col, 4 + k, FIXED[k][0], FIXED[k][1], FIXED[k][2]);
            setCol(col, 9, hr * 0.5F, hg * 0.5F, hb * 0.5F);
            Buf body = bodies.computeIfAbsent(in.m.tex, k -> new Buf());
            float shine = v[38] * a;
            Buf sh = shine > 0.004F ? shines.computeIfAbsent(in.m.tex, k -> new Buf()) : null;
            int shineCol = argb(1F, hr * shine, hg * shine, hb * shine);
            float animU = 0;
            if (in.anim != null) {
                float u = (tf * v[39] + v[40]) / in.anim.len;
                animU = (in.flags & Fx3d.F_LOOP) != 0 ? u - Mth.floor(u) : Mth.clamp(u, 0F, 1F);
            }
            for (Part p : in.m.parts) {
                Track tr = in.anim != null ? in.anim.tracks.get(p.name) : null;
                if (tr != null) {
                    float[] rot = sample(tr.rot, animU, 3);
                    float[] pos = sample(tr.pos, animU, 3);
                    float[] sc = sample(tr.scale, animU, 1);
                    MP.set(M).translate(p.px + (pos != null ? pos[0] : 0), p.py + (pos != null ? pos[1] : 0), p.pz + (pos != null ? pos[2] : 0));
                    RP.set(R);
                    if (rot != null) {
                        MP.rotateZ(rot[2] * Mth.DEG_TO_RAD).rotateY(rot[1] * Mth.DEG_TO_RAD).rotateX(rot[0] * Mth.DEG_TO_RAD);
                        RP.rotateZ(rot[2] * Mth.DEG_TO_RAD).rotateY(rot[1] * Mth.DEG_TO_RAD).rotateX(rot[0] * Mth.DEG_TO_RAD);
                    }
                    if (sc != null) MP.scale(Math.max(0.001F, sc[0]));
                    MP.translate(-p.px, -p.py, -p.pz);
                    if (sc != null && sc[0] <= 0.002F) continue;
                } else {
                    MP.set(M);
                    RP.set(R);
                }
                int nq = p.mat.length;
                for (int q = 0; q < nq; q++) {
                    int m = Mth.clamp(p.mat[q], 0, 9);
                    int c = argb(a, col[m * 3], col[m * 3 + 1], col[m * 3 + 2]);
                    int base = q * 32;
                    for (int k = 0; k < 4; k++) {
                        int o = base + k * 8;
                        MP.transformPosition(p.v[o], p.v[o + 1], p.v[o + 2], tmp);
                        RP.transform(p.v[o + 5], p.v[o + 6], p.v[o + 7], nrm);
                        body.add(tmp.x, tmp.y, tmp.z, p.v[o + 3], p.v[o + 4], nrm.x, nrm.y, nrm.z, c);
                        if (sh != null) sh.add(tmp.x, tmp.y, tmp.z, p.v[o + 3], p.v[o + 4], nrm.x, nrm.y, nrm.z, shineCol);
                    }
                }
                quads += nq;
            }
            // 빛 무리 (카메라를 보는 둥근 빛 — 더하기)
            float ga = v[37] * a;
            if (ga > 0.004F) {
                int hc = argb(1F, hr * ga, hg * ga, hb * ga);
                for (float[] g : in.m.glow) {
                    M.transformPosition(g[0], g[1], g[2], tmp);
                    float r = g[3] * s;
                    float rx = camRight.x * r;
                    float ry = camRight.y * r;
                    float rz = camRight.z * r;
                    float ux = camUp.x * r;
                    float uy = camUp.y * r;
                    float uz = camUp.z * r;
                    halos.add(tmp.x - rx - ux, tmp.y - ry - uy, tmp.z - rz - uz, 0, 1, 0, 1, 0, hc);
                    halos.add(tmp.x + rx - ux, tmp.y + ry - uy, tmp.z + rz - uz, 1, 1, 0, 1, 0, hc);
                    halos.add(tmp.x + rx + ux, tmp.y + ry + uy, tmp.z + rz + uz, 1, 0, 0, 1, 0, hc);
                    halos.add(tmp.x - rx + ux, tmp.y - ry + uy, tmp.z - rz + uz, 0, 0, 0, 1, 0, hc);
                }
            }
            drawn++;
        }
        lastDrawn = drawn;
        lastQuads = quads;
        for (Map.Entry<Identifier, Buf> e : bodies.entrySet()) put(ps, collector, RenderTypes.entityTranslucent(e.getKey()), e.getValue());
        for (Map.Entry<Identifier, Buf> e : shines.entrySet()) put(ps, collector, RenderTypes.eyes(e.getKey()), e.getValue());
        put(ps, collector, RenderTypes.eyes(HALO), halos);
    }

    /** 주인 기준 [오른쪽, 위, 앞] → 세계 (보는 쪽 sin · cos) */
    private static Vector3f local(Vector3f l, float sy, float cy) {
        return new Vector3f(-cy * l.x - sy * l.z, l.y, -sy * l.x + cy * l.z);
    }

    private static void setCol(float[] col, int i, float r, float g, float b) {
        col[i * 3] = r;
        col[i * 3 + 1] = g;
        col[i * 3 + 2] = b;
    }

    private static void put(PoseStack ps, SubmitNodeCollector collector, net.minecraft.client.renderer.rendertype.RenderType type, Buf b) {
        if (b.n == 0) return;
        float[] d = java.util.Arrays.copyOf(b.d, b.n * 8);
        int[] c = java.util.Arrays.copyOf(b.c, b.n);
        collector.submitCustomGeometry(ps, type, (pose, vc) -> emit(pose, vc, d, c));
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer vc, float[] d, int[] c) {
        for (int k = 0; k < c.length; k++) {
            int i = k * 8;
            vc.addVertex(pose, d[i], d[i + 1], d[i + 2]).setColor(c[k]).setUv(d[i + 3], d[i + 4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT)
                    .setNormal(pose, d[i + 5], d[i + 6], d[i + 7]);
        }
    }
}
