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
 * 열쇠마다 팔은 방향 (어깨 → 팔꿈치 dir · 무기 끝 blade · 팔꿈치 굽힘 bend) 으로 적고 여기서 위팔 회전 (사원수) · 팔꿈치 · 팔뚝 비틀기 · 손목으로 풂
 * (armSolve — 무기 끝이 blade 를 똑바로 봄) — 열쇠 사이는 구면 · 짧은 각 보간.
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
        float raTwist;
        float laTwist;
        /** 시험: 쓴 무기 끝 방향 (모델 — 손목을 손으로 더하지 않은 팔만, 아니면 null) */
        Vector3f raWant;
        Vector3f laWant;
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
        float[] r = arm(o, "ra", k.ra);
        k.raBend = r[0];
        k.raTwist = r[1];
        k.raWrist = r[2];
        k.raWant = want(o, "ra");
        float[] l = arm(o, "la", k.la);
        k.laBend = l[0];
        k.laTwist = l[1];
        k.laWrist = l[2];
        k.laWant = want(o, "la");
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

    /** 손목을 꺾지 않았을 때 팔뚝과 무기 사이 각 (130°) */
    static final double NATURAL = Math.PI / 2 + BLADE_TILT;
    /** 팔꿈치가 굽는 쪽 (위팔 둘레) 을 찾는 칸 수 */
    static final int SEARCH = 144;
    /** 손목 · 팔뚝 비틀기 · 위팔 비틀기 (60° 넘는 몫) 의 무게 (tools/motion_preview.py COST 와 같음) */
    static final double[] COST = {1.0, 0.6, 0.5};

    /** 팔 열쇠 {dir, blade, bend, wrist} → 위팔 회전 (q 에 넣음) · {팔꿈치 (로컬 x — 앞으로 굽힘은 음수), 팔뚝 비틀기 (로컬 y), 손목 (로컬 x)} 라디안. 없으면 내린 팔 */
    private static float[] arm(JsonObject o, String name, Quaternionf q) {
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
        return armSolve(dir, blade, bend, wrist, q);
    }

    /** 시험: 이 팔 열쇠의 무기 끝 방향 (모델 · 단위) — 팔이 없거나 손목을 손으로 더했으면 null */
    private static Vector3f want(JsonObject o, String name) {
        if (!o.has(name)) return null;
        JsonObject a = o.getAsJsonObject(name);
        if (a.has("wrist") && a.get("wrist").getAsFloat() != 0) return null;
        float[] blade = floats(a, "blade", new float[] {0, 0, 1});
        Vector3f v = new Vector3f(-blade[0], -blade[1], -blade[2]);
        return v.lengthSquared() < 1e-8F ? null : v.normalize();
    }

    /** 시험 (사진첩): 모든 열쇠에서 풀린 팔의 무기 끝과 쓴 방향의 가장 큰 차이 (도) */
    public static double worstBladeError() {
        double worst = 0;
        for (Motion m : all().values()) {
            for (Key k : m.keys) {
                if (k.raWant != null) worst = Math.max(worst, angle(bladeOf(k.ra, k.raBend, k.raTwist, k.raWrist), k.raWant));
                if (k.laWant != null) worst = Math.max(worst, angle(bladeOf(k.la, k.laBend, k.laTwist, k.laWrist), k.laWant));
            }
        }
        return worst;
    }

    private static double angle(Vector3f a, Vector3f b) {
        return Math.toDegrees(Math.acos(clamp1(new Vector3f(a).normalize().dot(b))));
    }

    static double clamp1(double x) {
        return Math.max(-1, Math.min(1, x));
    }

    static double wrap(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    /** 팔을 내린 자세 (앞 = 모델 -z) 를 가장 짧게 돌려 위팔이 y 를 볼 때의 앞 (위팔 비틀기의 기준) */
    static Vector3f restFront(Vector3f y) {
        float c = y.y;
        if (c > 1 - 1e-6F) return new Vector3f(0, 0, -1);
        if (c < -1 + 1e-6F) return new Vector3f(0, 0, 1);
        Vector3f ax = new Vector3f(y.z, 0, -y.x);
        float s = ax.length();
        ax.div(s);
        float ang = (float) Math.atan2(s, c);
        return new Vector3f(0, 0, -1).rotateAxis(ang, ax.x, ax.y, ax.z);
    }

    /**
     * 방향 (r 오른쪽, u 위, f 앞 — 뿌리 기준) 으로 적은 팔을 풂: 위팔 (로컬 +y) 이 dir, 팔꿈치는 bend 도만큼 굽고, 굽는 쪽 (위팔 둘레) · 팔뚝 비틀기 · 손목은
     * 무기 끝 (팔을 내린 채 앞에서 BLADE_TILT 만큼 위) 이 blade 를 똑바로 보게 정함 — 손목 · 비틀기가 작고 위팔이 덜 비틀린 쪽 (COST).
     * wrist (도) 는 풀린 손목에 더함. 모델 좌표 = (-r, -u, -f). 돌려줌: {팔꿈치, 팔뚝 비틀기, 손목} (라디안 — 팔꿈치는 앞으로 굽힘이 음수)
     */
    static float[] armSolve(float[] dir, float[] blade, float bendDeg, float wristDeg, Quaternionf out) {
        Vector3f y = new Vector3f(-dir[0], -dir[1], -dir[2]);
        if (y.lengthSquared() < 1e-8F) y.set(0, 1, 0);
        y.normalize();
        Vector3f b = new Vector3f(-blade[0], -blade[1], -blade[2]);
        if (b.lengthSquared() < 1e-8F) b.set(0, 0, -1);
        b.normalize();
        double bend = Math.toRadians(bendDeg);
        double cb = Math.cos(bend);
        double sb = Math.sin(bend);
        float c = (float) clamp1(b.dot(y));
        Vector3f d = new Vector3f(b).fma(-c, y);
        if (d.length() < 1e-4F) {
            d.set(0, 0, -1).fma(y.z, y);
            if (d.length() < 1e-4F) d.set(0, -1, 0);
        }
        d.normalize();
        Vector3f e = new Vector3f(y).cross(d);
        Vector3f n0 = restFront(y);
        double best = Double.MAX_VALUE;
        Vector3f bx = new Vector3f();
        Vector3f bz = new Vector3f();
        double bestTau = 0;
        double bestPhi = 0;
        Vector3f n = new Vector3f();
        Vector3f z = new Vector3f();
        Vector3f x = new Vector3f();
        for (int i = 0; i < SEARCH; i++) {
            double psi = 2 * Math.PI * i / SEARCH;
            n.set(d).mul((float) Math.cos(psi)).fma((float) Math.sin(psi), e);
            z.set(n).negate();
            y.cross(z, x);
            // 팔뚝 틀 = 위팔 틀 · Rx(-bend): 열 x, cb·y − sb·z, sb·y + cb·z
            double fx = x.dot(b);
            double fy = cb * y.dot(b) - sb * z.dot(b);
            double fz = sb * y.dot(b) + cb * z.dot(b);
            double roll = Math.acos(clamp1(n.dot(n0)));
            for (int br = 0; br < 2; br++) {
                double a;
                double tau;
                if (br == 0) {
                    a = Math.asin(clamp1(fy));
                    tau = Math.atan2(-fx, -fz);
                } else {
                    a = Math.PI - Math.asin(clamp1(fy));
                    tau = Math.atan2(fx, fz);
                }
                double phi = wrap(a + BLADE_TILT);
                double over = Math.max(0, roll - Math.toRadians(60));
                double cost = COST[0] * phi * phi + COST[1] * tau * tau + COST[2] * over * over;
                if (cost < best - 1e-9) {
                    best = cost;
                    bx.set(x);
                    bz.set(z);
                    bestTau = tau;
                    bestPhi = phi;
                }
            }
        }
        out.setFromNormalized(new Matrix3f(bx, y, bz));
        return new float[] {(float) -bend, (float) bestTau, (float) wrap(bestPhi + Math.toRadians(wristDeg))};
    }

    /** 풀린 팔의 무기 끝 방향 (모델 — 시험) */
    static Vector3f bladeOf(Quaternionf q, float bend, float twist, float wrist) {
        Vector3f v = new Vector3f(0, (float) -Math.sin(BLADE_TILT), (float) -Math.cos(BLADE_TILT));
        v.rotateX(wrist).rotateY(twist).rotateX(bend);
        return q.transform(v);
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

    /** 각 (라디안) 의 짧은 쪽 보간 */
    private static float lerpAngle(float a, float b, float u) {
        return (float) (a + wrap(b - a) * u);
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
        out.raWrist = lerpAngle(k0.raWrist, k1.raWrist, u);
        out.laWrist = lerpAngle(k0.laWrist, k1.laWrist, u);
        out.raTwist = lerpAngle(k0.raTwist, k1.raTwist, u);
        out.laTwist = lerpAngle(k0.laTwist, k1.laTwist, u);
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
