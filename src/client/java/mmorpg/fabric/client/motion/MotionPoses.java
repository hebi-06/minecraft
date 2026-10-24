package mmorpg.fabric.client.motion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mmorpg.fabric.Mmorpg;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 0.38.0 동작 열쇠 자세 (assets/mmorpg/motion/poses.json — 미리 보기 tools/motion_preview.py 와 같은 식).
 * 열쇠마다 팔은 방향 (어깨 → 팔꿈치 dir · 무기 끝 blade · 팔꿈치 굽힘 bend) 으로 적고 여기서 회전 (사원수) 으로 풂 — 열쇠 사이는 구면 보간.
 */
public final class MotionPoses {
    private MotionPoses() {}

    /** 팔을 내린 채 든 무기가 앞에서 위로 들린 각 (tools/motion_preview.py BLADE_TILT) */
    static final float BLADE_TILT = (float) Math.toRadians(40);

    /** 열쇠 하나 */
    static final class Key {
        float t;
        String ease = "inout";
        float lean;
        float twist;
        float side;
        float headX;
        float headY;
        final Quaternionf ra = new Quaternionf();
        final Quaternionf la = new Quaternionf();
        float raBend;
        float laBend;
        float raWrist;
        float laWrist;
        float rlX;
        float rlY;
        float rlZ;
        float llX;
        float llY;
        float llZ;
        float rk;
        float lk;
        float up;
        float fwd;
        float spin;
        float flip;
    }

    /** 1인칭 열쇠 */
    static final class FpKey {
        float t;
        String ease = "inout";
        float x;
        float y;
        float z;
        float rx;
        float ry;
        float rz;
    }

    /** 동작 하나 */
    public static final class Motion {
        public final String name;
        public final int ticks;
        final int parts;
        final boolean softLegs;
        final float fadeIn;
        final float fadeOut;
        final float hideFrom;
        final float hideTo;
        final List<Key> keys;
        final List<FpKey> fp;

        Motion(String name, int ticks, int parts, boolean softLegs, float fadeIn, float fadeOut, float hideFrom, float hideTo, List<Key> keys, List<FpKey> fp) {
            this.name = name;
            this.ticks = ticks;
            this.parts = parts;
            this.softLegs = softLegs;
            this.fadeIn = fadeIn;
            this.fadeOut = fadeOut;
            this.hideFrom = hideFrom;
            this.hideTo = hideTo;
            this.keys = keys;
            this.fp = fp;
        }
    }

    private static Map<String, Motion> MOTIONS;

    /** 동작들 (처음 부를 때 읽음) */
    public static synchronized Map<String, Motion> all() {
        if (MOTIONS == null) MOTIONS = load("/assets/mmorpg/motion/poses.json");
        return MOTIONS;
    }

    public static Motion get(String name) {
        return name == null ? null : all().get(name);
    }

    public static Set<String> names() {
        return all().keySet();
    }

    // ------------------------------------------------------------ 읽기

    static Map<String, Motion> load(String path) {
        Map<String, Motion> out = new HashMap<>();
        try (InputStream in = MotionPoses.class.getResourceAsStream(path)) {
            if (in == null) {
                Mmorpg.LOG.warn("[mmorpg] motion poses missing: {}", path);
                return out;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject ms = root.getAsJsonObject("motions");
            for (Map.Entry<String, JsonElement> e : ms.entrySet()) {
                if (e.getKey().startsWith("_")) continue;
                try {
                    out.put(e.getKey(), parse(e.getKey(), e.getValue().getAsJsonObject()));
                } catch (RuntimeException err) {
                    Mmorpg.LOG.warn("[mmorpg] motion {}", e.getKey(), err);
                }
            }
        } catch (Exception e) {
            Mmorpg.LOG.warn("[mmorpg] motion poses", e);
        }
        return Collections.unmodifiableMap(out);
    }

    private static Motion parse(String name, JsonObject o) {
        int ticks = o.has("ticks") ? o.get("ticks").getAsInt() : 10;
        int parts = 0;
        if (o.has("parts")) {
            for (JsonElement p : o.getAsJsonArray("parts")) {
                switch (p.getAsString()) {
                    case "body" -> parts |= Pose.BODY;
                    case "ra" -> parts |= Pose.RA;
                    case "la" -> parts |= Pose.LA;
                    case "arms" -> parts |= Pose.RA | Pose.LA;
                    case "legs" -> parts |= Pose.LEGS;
                    case "head" -> parts |= Pose.HEAD;
                    case "root" -> parts |= Pose.ROOT;
                    default -> {}
                }
            }
        } else parts = Pose.BODY | Pose.RA | Pose.LA | Pose.LEGS | Pose.ROOT;
        float[] fade = floats(o, "fade", new float[] {0.08F, 0.2F});
        float[] hide = floats(o, "hide", new float[] {-1, -1});
        List<Key> keys = new ArrayList<>();
        for (JsonElement k : o.getAsJsonArray("keys")) keys.add(key(k.getAsJsonObject()));
        List<FpKey> fp = new ArrayList<>();
        if (o.has("fp")) for (JsonElement k : o.getAsJsonArray("fp")) fp.add(fpKey(k.getAsJsonObject()));
        boolean soft = o.has("softLegs") && o.get("softLegs").getAsBoolean();
        return new Motion(name, ticks, parts, soft, fade[0], fade[1], hide[0], hide[1], List.copyOf(keys), List.copyOf(fp));
    }

    private static float[] floats(JsonObject o, String k, float[] dflt) {
        if (!o.has(k)) return dflt;
        JsonArray a = o.getAsJsonArray(k);
        float[] out = dflt.clone();
        for (int i = 0; i < Math.min(out.length, a.size()); i++) out[i] = a.get(i).getAsFloat();
        return out;
    }

    private static float rad(float deg) {
        return (float) Math.toRadians(deg);
    }

    private static Key key(JsonObject o) {
        Key k = new Key();
        k.t = o.get("t").getAsFloat();
        if (o.has("ease")) k.ease = o.get("ease").getAsString();
        float[] body = floats(o, "body", new float[3]);
        k.lean = rad(body[0]);
        k.twist = rad(body[1]);
        k.side = rad(body[2]);
        float[] head = floats(o, "head", new float[2]);
        k.headX = rad(head[0]);
        k.headY = rad(head[1]);
        k.raBend = arm(o, "ra", k.ra);
        k.laBend = arm(o, "la", k.la);
        k.raWrist = wrist(o, "ra");
        k.laWrist = wrist(o, "la");
        float[] rl = floats(o, "rl", new float[3]);
        k.rlX = rad(rl[0]);
        k.rlY = rad(rl[1]);
        k.rlZ = rad(rl[2]);
        float[] ll = floats(o, "ll", new float[3]);
        k.llX = rad(ll[0]);
        k.llY = rad(ll[1]);
        k.llZ = rad(ll[2]);
        k.rk = o.has("rk") ? rad(o.get("rk").getAsFloat()) : 0;
        k.lk = o.has("lk") ? rad(o.get("lk").getAsFloat()) : 0;
        float[] root = floats(o, "root", new float[2]);
        k.up = root[0];
        k.fwd = root[1];
        k.spin = o.has("spin") ? rad(o.get("spin").getAsFloat()) : 0;
        k.flip = o.has("flip") ? rad(o.get("flip").getAsFloat()) : 0;
        return k;
    }

    private static FpKey fpKey(JsonObject o) {
        FpKey k = new FpKey();
        k.t = o.get("t").getAsFloat();
        if (o.has("ease")) k.ease = o.get("ease").getAsString();
        float[] p = floats(o, "p", new float[3]);
        float[] r = floats(o, "r", new float[3]);
        k.x = p[0];
        k.y = p[1];
        k.z = p[2];
        k.rx = rad(r[0]);
        k.ry = rad(r[1]);
        k.rz = rad(r[2]);
        return k;
    }

    /** 팔 열쇠 {dir, blade, bend, wrist} → 회전 (q 에 넣음) · 굽힘 (라디안 — 앞으로 굽힘은 음수). 없으면 내린 팔 */
    private static float arm(JsonObject o, String name, Quaternionf q) {
        float[] dir = {0, -1, 0};
        float[] blade = {0, 0, 1};
        float bend = 0;
        float wrist = 0;
        if (o.has(name)) {
            JsonObject a = o.getAsJsonObject(name);
            dir = floats(a, "dir", dir);
            blade = floats(a, "blade", blade);
            bend = a.has("bend") ? a.get("bend").getAsFloat() : 0;
            wrist = a.has("wrist") ? a.get("wrist").getAsFloat() : 0;
        }
        armRotation(dir, blade, bend + wrist, q);
        return -rad(bend);
    }

    /** 손목 (도 → 라디안 — 무기 끝을 팔뚝 쪽으로 꺾음: 팔꿈치와 같은 축, 앞으로 꺾음은 음수) */
    private static float wrist(JsonObject o, String name) {
        if (!o.has(name)) return 0;
        JsonObject a = o.getAsJsonObject(name);
        return a.has("wrist") ? -rad(a.get("wrist").getAsFloat()) : 0;
    }

    /**
     * 방향 (r 오른쪽, u 위, f 앞 — 뿌리 기준) 으로 적은 팔의 회전: 로컬 +y (팔이 내려가는 쪽) 가 dir, 팔꿈치를 굽힌 뒤 무기 (로컬 앞에서 BLADE_TILT 만큼 위)
     * 의 팔에 수직인 몫이 blade 쪽. 모델 좌표 = (-r, -u, -f)
     */
    static Quaternionf armRotation(float[] dir, float[] blade, float bendDeg, Quaternionf out) {
        Vector3f y = new Vector3f(-dir[0], -dir[1], -dir[2]);
        if (y.lengthSquared() < 1e-8F) y.set(0, 1, 0);
        y.normalize();
        Vector3f b = new Vector3f(-blade[0], -blade[1], -blade[2]);
        if (b.lengthSquared() < 1e-8F) b.set(0, 0, -1);
        b.normalize();
        Vector3f bl = new Vector3f(0, (float) -Math.sin(BLADE_TILT), (float) -Math.cos(BLADE_TILT)).rotateX(-rad(bendDeg));
        float s = bl.z >= 0 ? 1 : -1;
        Vector3f d = new Vector3f(b).sub(new Vector3f(y).mul(b.dot(y)));
        if (d.length() < 1e-4F) {
            d.set(0, 0, -1).sub(new Vector3f(y).mul(-y.z));
            if (d.length() < 1e-4F) d.set(0, -1, 0);
        }
        d.normalize();
        Vector3f z = d.mul(s);
        Vector3f x = new Vector3f(y).cross(z);
        Matrix3f m = new Matrix3f(x, y, z);
        return out.setFromNormalized(m);
    }

    // ------------------------------------------------------------ 풀기

    static float ease(float u, String kind) {
        u = Math.max(0, Math.min(1, u));
        return switch (kind) {
            case "linear" -> u;
            case "in" -> u * u;
            case "out" -> 1 - (1 - u) * (1 - u);
            case "out3" -> 1 - (1 - u) * (1 - u) * (1 - u);
            default -> u * u * (3 - 2 * u);
        };
    }

    private static float lerp(float a, float b, float u) {
        return a + (b - a) * u;
    }

    /** 동작 m 의 t (0 ~ 1) 자세를 out 에 (mirror = 주 손이 왼손) */
    public static Pose evaluate(Motion m, float t, boolean mirror, Pose out) {
        t = Math.max(0, Math.min(1, t));
        List<Key> keys = m.keys;
        Key k0 = keys.get(0);
        Key k1 = keys.get(keys.size() - 1);
        for (int i = 0; i < keys.size() - 1; i++) {
            if (keys.get(i).t <= t && t <= keys.get(i + 1).t) {
                k0 = keys.get(i);
                k1 = keys.get(i + 1);
                break;
            }
        }
        float span = k1.t - k0.t;
        float u = ease(span <= 0 ? 0 : (t - k0.t) / span, k1.ease);
        out.motion = m.name;
        out.t = t;
        out.parts = m.parts;
        out.softLegs = m.softLegs;
        out.lean = lerp(k0.lean, k1.lean, u);
        out.twist = lerp(k0.twist, k1.twist, u);
        out.side = lerp(k0.side, k1.side, u);
        out.headX = lerp(k0.headX, k1.headX, u);
        out.headY = lerp(k0.headY, k1.headY, u);
        k0.ra.slerp(k1.ra, u, out.ra);
        k0.la.slerp(k1.la, u, out.la);
        out.raBend = lerp(k0.raBend, k1.raBend, u);
        out.laBend = lerp(k0.laBend, k1.laBend, u);
        out.raWrist = lerp(k0.raWrist, k1.raWrist, u);
        out.laWrist = lerp(k0.laWrist, k1.laWrist, u);
        out.rlX = lerp(k0.rlX, k1.rlX, u);
        out.rlY = lerp(k0.rlY, k1.rlY, u);
        out.rlZ = lerp(k0.rlZ, k1.rlZ, u);
        out.llX = lerp(k0.llX, k1.llX, u);
        out.llY = lerp(k0.llY, k1.llY, u);
        out.llZ = lerp(k0.llZ, k1.llZ, u);
        out.rk = lerp(k0.rk, k1.rk, u);
        out.lk = lerp(k0.lk, k1.lk, u);
        out.up = lerp(k0.up, k1.up, u);
        out.fwd = lerp(k0.fwd, k1.fwd, u);
        out.spin = lerp(k0.spin, k1.spin, u);
        out.flip = lerp(k0.flip, k1.flip, u);
        out.hide = m.hideFrom >= 0 && t >= m.hideFrom && t <= m.hideTo;
        float w = 1;
        if (m.fadeIn > 0 && t < m.fadeIn) w = ease(t / m.fadeIn, "inout");
        if (m.fadeOut > 0 && t > 1 - m.fadeOut) w = Math.min(w, ease((1 - t) / m.fadeOut, "inout"));
        out.w = w;
        // 1인칭
        out.fpX = out.fpY = out.fpZ = out.fpRX = out.fpRY = out.fpRZ = 0;
        if (!m.fp.isEmpty()) {
            List<FpKey> fk = m.fp;
            FpKey a = fk.get(0);
            FpKey b = fk.get(fk.size() - 1);
            for (int i = 0; i < fk.size() - 1; i++) {
                if (fk.get(i).t <= t && t <= fk.get(i + 1).t) {
                    a = fk.get(i);
                    b = fk.get(i + 1);
                    break;
                }
            }
            float sp = b.t - a.t;
            float v = ease(sp <= 0 ? 0 : (t - a.t) / sp, b.ease);
            out.fpX = lerp(a.x, b.x, v);
            out.fpY = lerp(a.y, b.y, v);
            out.fpZ = lerp(a.z, b.z, v);
            out.fpRX = lerp(a.rx, b.rx, v);
            out.fpRY = lerp(a.ry, b.ry, v);
            out.fpRZ = lerp(a.rz, b.rz, v);
        }
        if (mirror) out.mirror();
        return out;
    }
}
