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

    /**
     * 팔을 내린 채 든 무기가 앞에서 위로 들린 각 (tools/motion_preview.py BLADE_TILT) — 동작에 "tilt" 가 없을 때.
     * 0.40.0: 근접 무기 모델의 실제 각은 30° (tools/weapon_blades.py — 무기 축) 라 참격 궤적이 있는 동작은 "tilt": 30 (무기 끝이 쓴 blade 를 똑바로 봄)
     */
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
        float raDev;
        float laDev;
        /** 시험: 쓴 무기 끝 방향 · 겉면 (모델 — 손목을 손으로 더하지 않은 팔만, 아니면 null) */
        Vector3f raWant;
        Vector3f laWant;
        Vector3f raFace;
        Vector3f laFace;
        /** 0.40.0 잇는 열쇠 ("gen" — tools/motion_arcs.py) 가 이어 갈 풀린 팔: 위팔 회전 · 손 회전 (모델) */
        final Matrix3f raUpper = new Matrix3f();
        final Matrix3f raHand = new Matrix3f();
        final Matrix3f laUpper = new Matrix3f();
        final Matrix3f laHand = new Matrix3f();
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

        /** 같은 자세 · 다른 t (0.44.0 창 패널의 쉬는 자세 — {@link #restLoop}) */
        Key copyAt(float at) {
            Key k = new Key();
            k.t = at;
            k.ease = ease;
            k.lean = lean;
            k.twist = twist;
            k.side = side;
            k.headX = headX;
            k.headY = headY;
            k.ra.set(ra);
            k.la.set(la);
            k.raBend = raBend;
            k.laBend = laBend;
            k.raWrist = raWrist;
            k.laWrist = laWrist;
            k.raTwist = raTwist;
            k.laTwist = laTwist;
            k.raDev = raDev;
            k.laDev = laDev;
            k.raWant = raWant;
            k.laWant = laWant;
            k.raFace = raFace;
            k.laFace = laFace;
            k.raUpper.set(raUpper);
            k.raHand.set(raHand);
            k.laUpper.set(laUpper);
            k.laHand.set(laHand);
            k.rlX = rlX;
            k.rlY = rlY;
            k.rlZ = rlZ;
            k.llX = llX;
            k.llY = llY;
            k.llZ = llZ;
            k.rk = rk;
            k.lk = lk;
            k.up = up;
            k.fwd = fwd;
            k.spin = spin;
            k.flip = flip;
            return k;
        }
    }

    /**
     * 0.44.0 쉬는 자세에서 시작해 쉬는 자세로 끝나는 같은 동작 (대장장이 창 패널: 망치질 사이에도 망치를 모루 위에 든 채 — 바닐라 자세로 돌아가지 않음).
     * 첫 · 마지막 열쇠를 rest 번째 열쇠 (t 만 0 · 1) 로 바꾸고 들어오고 나가는 무게 (fade) 를 없앰. t = 0 이 쉬는 자세
     */
    public static Motion restLoop(Motion m, int rest) {
        return restLoop(m, m, rest);
    }

    /**
     * 0.45.0 같은 것 — 쉬는 자세를 다른 동작 (idle) 의 열쇠 rest 로 (가게 창 패널: 룬술사 · 상인은 일마다 동작이 달라도 같은 쉬는 자세에서 시작해 거기로
     * 끝남)
     */
    public static Motion restLoop(Motion m, Motion idle, int rest) {
        if (m == null || idle == null || rest <= 0 || rest >= idle.keys.size() - 1) return m;
        List<Key> keys = new ArrayList<>(m.keys);
        keys.set(0, idle.keys.get(rest).copyAt(0F));
        keys.set(keys.size() - 1, idle.keys.get(rest).copyAt(1F));
        return new Motion(m.name, m.ticks, m.parts, m.softLegs, 0F, 0F, m.hideFrom, m.hideTo, List.copyOf(keys), m.fp, m.tilt, m.trail, m.hold,
                m.faceYaw, m.impacts);
    }

    /** 1인칭 열쇠: 손에 든 아이템 자리 (오른쪽 · 위 · 뒤 칸) · 회전 (q — 바닐라 1인칭 자세에서) */
    static final class FpKey {
        float t;
        String ease = "inout";
        float x;
        float y;
        float z;
        final Quaternionf q = new Quaternionf();
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
        /** 팔을 내린 채 든 무기가 위로 들린 각 (라디안 — 팔 풀기의 기준) */
        final float tilt;
        /** 0.40.0 참격 궤적을 남기는 t 구간들 ({시작, 끝} — 없으면 궤적 없음) */
        public final float[][] trail;
        /** 0.44.0 동작 동안 주 손에 드는 아이템 모델 (item_model id — 대장장이 망치 rpg:smith_hammer, 없으면 null) */
        public final String hold;
        /** 0.44.0 몸 · 머리를 서버가 알린 쪽 (yaw — 대장장이: 모루 쪽) 으로 */
        public final boolean faceYaw;
        /** 0.44.0 망치가 닿는 t (0 ~ 1 — 없으면 빈 배열) */
        public final float[] impacts;

        Motion(String name, int ticks, int parts, boolean softLegs, float fadeIn, float fadeOut, float hideFrom, float hideTo, List<Key> keys, List<FpKey> fp,
                float tilt, float[][] trail) {
            this(name, ticks, parts, softLegs, fadeIn, fadeOut, hideFrom, hideTo, keys, fp, tilt, trail, null, false, new float[0]);
        }

        Motion(String name, int ticks, int parts, boolean softLegs, float fadeIn, float fadeOut, float hideFrom, float hideTo, List<Key> keys, List<FpKey> fp,
                float tilt, float[][] trail, String hold, boolean faceYaw, float[] impacts) {
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
            this.tilt = tilt;
            this.trail = trail;
            this.hold = hold;
            this.faceYaw = faceYaw;
            this.impacts = impacts;
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
        float tilt = o.has("tilt") ? rad(o.get("tilt").getAsFloat()) : BLADE_TILT;
        List<Key> keys = new ArrayList<>();
        Key prev = null;
        for (JsonElement k : o.getAsJsonArray("keys")) {
            JsonObject ko = k.getAsJsonObject();
            Key cur = key(ko, tilt, ko.has("gen") && ko.get("gen").getAsBoolean() ? prev : null);
            keys.add(cur);
            prev = cur;
        }
        List<FpKey> fp = new ArrayList<>();
        if (o.has("fp")) for (JsonElement k : o.getAsJsonArray("fp")) fp.add(fpKey(k.getAsJsonObject()));
        boolean soft = o.has("softLegs") && o.get("softLegs").getAsBoolean();
        List<float[]> trail = new ArrayList<>();
        if (o.has("trail")) {
            for (JsonElement w : o.getAsJsonArray("trail")) {
                JsonArray a = w.getAsJsonArray();
                float from = a.get(0).getAsFloat();
                float to = a.get(1).getAsFloat();
                if (to > from) trail.add(new float[] {Math.max(0, from), Math.min(1, to)});
            }
        }
        String hold = o.has("hold") ? o.get("hold").getAsString() : null;
        boolean faceYaw = o.has("faceYaw") && o.get("faceYaw").getAsBoolean();
        float[] impacts = new float[0];
        if (o.has("impacts")) {
            JsonArray a = o.getAsJsonArray("impacts");
            impacts = new float[a.size()];
            for (int i = 0; i < a.size(); i++) impacts[i] = a.get(i).getAsFloat();
        }
        return new Motion(name, ticks, parts, soft, fade[0], fade[1], hide[0], hide[1], List.copyOf(keys), List.copyOf(fp), tilt,
                trail.toArray(new float[0][]), hold, faceYaw, impacts);
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

    /** 열쇠 하나 (prev = 잇는 열쇠면 앞 열쇠 — 팔이 그 자세에서 이어지게 풂, 아니면 null) */
    private static Key key(JsonObject o, float tilt, Key prev) {
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
        float[] r = arm(o, "ra", k.ra, tilt, prev == null ? null : prev.raUpper, prev == null ? null : prev.raHand);
        frames(k.ra, r, k.raUpper, k.raHand);
        k.raBend = r[0];
        k.raTwist = r[1];
        k.raWrist = r[2];
        k.raDev = r[3];
        k.raWant = want(o, "ra", "blade");
        k.raFace = want(o, "ra", "face");
        float[] l = arm(o, "la", k.la, tilt, prev == null ? null : prev.laUpper, prev == null ? null : prev.laHand);
        frames(k.la, l, k.laUpper, k.laHand);
        k.laBend = l[0];
        k.laTwist = l[1];
        k.laWrist = l[2];
        k.laDev = l[3];
        k.laWant = want(o, "la", "blade");
        k.laFace = want(o, "la", "face");
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
        k.x = p[0];
        k.y = p[1];
        k.z = p[2];
        if (o.has("blade")) fpRotation(floats(o, "blade", new float[] {0, 1, 0}), o.has("edge") ? floats(o, "edge", new float[3]) : null, k.q);
        else if (o.has("r")) {
            float[] r = floats(o, "r", new float[3]);
            k.q.rotationY(rad(r[1])).rotateX(rad(r[0])).rotateZ(rad(r[2]));
        }
        return k;
    }

    /** 1인칭에서 세워 든 무기 (칼 · 단검 · 도끼 · 마검 · 창 · 낫 · 망치 · 홀 · 완드) 의 끝 · 날 방향 (화면 공간 +x 오른쪽 · +y 위 · -z 앞 — tools/weapon_models.py UPRIGHT_DIR · edge_forward) */
    static final Vector3f FP_REST_BLADE = new Vector3f(-0.10004F, 0.97034F, -0.22008F);
    static final Vector3f FP_REST_EDGE = new Vector3f(-0.35775F, -0.24147F, -0.90206F);

    /**
     * 1인칭 회전: 1인칭 자세의 무기 (끝 FP_REST_BLADE · 날 FP_REST_EDGE) 를 끝 blade · 날 edge 쪽으로 (화면 기준 [r 오른쪽, u 위, f 앞] — edge 가 없으면 앞에 가까운 쪽).
     * MotionRender.firstPerson 이 무기 손잡이를 축으로 돌림. tools/motion_preview.py fp_rotation 과 같음
     */
    static Quaternionf fpRotation(float[] blade, float[] edge, Quaternionf out) {
        Vector3f b = new Vector3f(blade[0], blade[1], -blade[2]);
        if (b.lengthSquared() < 1e-8F) return out.identity();
        b.normalize();
        Vector3f e = edge == null ? new Vector3f(0, 0, -1) : new Vector3f(edge[0], edge[1], -edge[2]);
        e.fma(-e.dot(b), b);
        if (e.length() < 1e-3F) {
            e.set(0, 1, 0).fma(-b.y, b);
            if (e.length() < 1e-3F) e.set(1, 0, 0).fma(-b.x, b);
        }
        e.normalize();
        Vector3f b0 = new Vector3f(FP_REST_BLADE).normalize();
        Vector3f e0 = new Vector3f(FP_REST_EDGE).fma(-FP_REST_EDGE.dot(b0), b0).normalize();
        Matrix3f target = new Matrix3f(b, e, new Vector3f(b).cross(e));
        Matrix3f rest = new Matrix3f(b0, e0, new Vector3f(b0).cross(e0));
        return out.setFromNormalized(target.mul(rest.transpose()));
    }

    /** 팔꿈치가 굽는 쪽 (위팔 둘레) 을 찾는 칸 수 */
    static final int SEARCH = 144;
    /** 손목 · 팔뚝 비틀기 · 위팔 비틀기 (60° 넘는 몫) 의 무게 (tools/motion_preview.py COST 와 같음) */
    static final double[] COST = {1.0, 0.6, 0.5};

    /** 팔 열쇠 {dir, blade, face, bend, wrist} → 위팔 회전 (q 에 넣음) · {팔꿈치 (로컬 x — 앞으로 굽힘은 음수), 팔뚝 비틀기 (로컬 y), 손목 (로컬 x),
     *  손목 옆 꺾기 (로컬 z)} 라디안. 없으면 내린 팔 */
    /** 풀린 팔 (위팔 q · {팔꿈치, 비틀기, 손목, 옆 꺾기}) → 위팔 회전 · 손 회전 (handOf 와 같은 차례) */
    private static void frames(Quaternionf q, float[] s, Matrix3f upper, Matrix3f hand) {
        upper.set(q);
        hand.set(q).rotateX(s[0]).rotateY(s[1]).rotateX(s[2]).rotateZ(s[3]);
    }

    private static float[] arm(JsonObject o, String name, Quaternionf q, float tilt, Matrix3f prevUpper, Matrix3f prevHand) {
        float[] dir = {0, -1, 0};
        float[] blade = {0, 0, 1};
        float[] face = null;
        float bend = 0;
        float wrist = 0;
        if (o.has(name)) {
            JsonObject a = o.getAsJsonObject(name);
            dir = floats(a, "dir", dir);
            blade = floats(a, "blade", blade);
            if (a.has("face")) face = floats(a, "face", new float[3]);
            bend = a.has("bend") ? a.get("bend").getAsFloat() : 0;
            wrist = a.has("wrist") ? a.get("wrist").getAsFloat() : 0;
        }
        return armSolve(dir, blade, face, bend, wrist, tilt, prevUpper, prevHand, q);
    }

    /** 시험: 이 팔 열쇠의 무기 끝 (blade) · 겉면 (face) 방향 (모델 · 단위) — 없거나 손목을 손으로 더했으면 null */
    private static Vector3f want(JsonObject o, String name, String key) {
        if (!o.has(name)) return null;
        JsonObject a = o.getAsJsonObject(name);
        if (a.has("wrist") && a.get("wrist").getAsFloat() != 0) return null;
        if (!a.has(key) && !key.equals("blade")) return null;
        float[] v3 = floats(a, key, new float[] {0, 0, 1});
        Vector3f v = new Vector3f(-v3[0], -v3[1], -v3[2]);
        return v.lengthSquared() < 1e-8F ? null : v.normalize();
    }

    /** 시험 (사진첩): 모든 열쇠에서 풀린 팔의 무기 끝 · 겉면과 쓴 방향의 가장 큰 차이 (도) */
    public static double worstBladeError() {
        double worst = 0;
        for (Motion m : all().values()) {
            for (Key k : m.keys) {
                worst = Math.max(worst, error(k.ra, k.raBend, k.raTwist, k.raWrist, k.raDev, k.raWant, k.raFace, m.tilt));
                worst = Math.max(worst, error(k.la, k.laBend, k.laTwist, k.laWrist, k.laDev, k.laWant, k.laFace, m.tilt));
            }
        }
        return worst;
    }

    private static double error(Quaternionf q, float bend, float twist, float wrist, float dev, Vector3f want, Vector3f face, float tilt) {
        if (want == null) return 0;
        double e = angle(handOf(q, bend, twist, wrist, dev, new Vector3f(0, (float) -Math.sin(tilt), (float) -Math.cos(tilt))), want);
        if (face != null) {
            Vector3f f = new Vector3f(face).fma(-face.dot(want), want);
            if (f.length() > 1e-4F) e = Math.max(e, angle(handOf(q, bend, twist, wrist, dev, new Vector3f(FACE_REST)), f.normalize()));
        }
        return e;
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

    /** 팔을 내렸을 때 든 무기의 바깥 면 (오른팔 로컬 -x — 몸 바깥쪽) */
    static final Vector3f FACE_REST = new Vector3f(-1, 0, 0);
    /** 손목 옆 꺾기 (face 를 맞출 때만) 의 무게 */
    static final double COST_DEV = 2.0;
    /** 0.40.0 잇는 열쇠 ("gen"): 앞 열쇠의 위팔 · 손 회전에서 벗어난 각² 의 무게 (팔이 갑자기 뒤집히지 않게 — tools/motion_preview.py CONT) */
    static final double CONT = 0.6;

    /** 두 회전 행렬 사이 각 (라디안) */
    static double rotAngle(Matrix3f a, Matrix3f b) {
        double tr = a.m00 * b.m00 + a.m01 * b.m01 + a.m02 * b.m02 + a.m10 * b.m10 + a.m11 * b.m11 + a.m12 * b.m12 + a.m20 * b.m20 + a.m21 * b.m21
                + a.m22 * b.m22;
        return Math.acos(clamp1((tr - 1) / 2));
    }

    /**
     * 방향 (r 오른쪽, u 위, f 앞 — 뿌리 기준) 으로 적은 팔을 풂: 위팔 (로컬 +y) 이 dir, 팔꿈치는 bend 도만큼 굽고, 굽는 쪽 (위팔 둘레) · 팔뚝 비틀기 · 손목은
     * 무기 끝 (팔을 내린 채 앞에서 tilt 만큼 위 — 동작의 "tilt", 없으면 BLADE_TILT) 이 blade 를 똑바로 보게 정함 — 손목 · 비틀기가 작고 위팔이 덜 비틀린 쪽 (COST).
     * face (방패 겉면이 볼 쪽 — null 이면 없음) 가 있으면 무기 겉면 (FACE_REST) 도 맞춤 (손목 옆 꺾기를 더 씀). wrist (도) 는 풀린 손목에 더함.
     * 모델 좌표 = (-r, -u, -f). 돌려줌: {팔꿈치, 팔뚝 비틀기, 손목, 손목 옆 꺾기} (라디안 — 팔꿈치는 앞으로 굽힘이 음수). tools/motion_preview.py arm_solve 와 같음.
     * prevUpper · prevHand (잇는 열쇠 — 앞 열쇠의 위팔 · 손 회전, 아니면 null): 거기서 벗어난 각² × CONT 를 더함
     */
    static float[] armSolve(float[] dir, float[] blade, float[] face, float bendDeg, float wristDeg, float tilt, Matrix3f prevUpper, Matrix3f prevHand,
            Quaternionf out) {
        Vector3f y = new Vector3f(-dir[0], -dir[1], -dir[2]);
        if (y.lengthSquared() < 1e-8F) y.set(0, 1, 0);
        y.normalize();
        Vector3f b = new Vector3f(-blade[0], -blade[1], -blade[2]);
        if (b.lengthSquared() < 1e-8F) b.set(0, 0, -1);
        b.normalize();
        double bend = Math.toRadians(bendDeg);
        double cb = Math.cos(bend);
        double sb = Math.sin(bend);
        // 겉면까지 맞출 때: 손 회전 H = [b, f, b×f] · [내린 팔의 무기 끝, 겉면, 끝×겉면]ᵀ
        Matrix3f h = null;
        if (face != null) {
            Vector3f f = new Vector3f(-face[0], -face[1], -face[2]);
            f.fma(-f.dot(b), b);
            if (f.length() > 1e-4F) {
                f.normalize();
                Vector3f bl0 = new Vector3f(0, (float) -Math.sin(tilt), (float) -Math.cos(tilt));
                Matrix3f target = new Matrix3f(b, f, new Vector3f(b).cross(f));
                Matrix3f rest = new Matrix3f(bl0, FACE_REST, new Vector3f(bl0).cross(FACE_REST));
                h = target.mul(rest.transpose());
            }
        }
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
        double bestChi = 0;
        Vector3f n = new Vector3f();
        Vector3f z = new Vector3f();
        Vector3f x = new Vector3f();
        Vector3f f1 = new Vector3f();
        Vector3f f2 = new Vector3f();
        Matrix3f fm = new Matrix3f();
        Matrix3f k = new Matrix3f();
        Matrix3f rc = new Matrix3f();
        Matrix3f hc = new Matrix3f();
        double[][] cands = new double[2][3];
        for (int i = 0; i < SEARCH; i++) {
            double psi = 2 * Math.PI * i / SEARCH;
            n.set(d).mul((float) Math.cos(psi)).fma((float) Math.sin(psi), e);
            z.set(n).negate();
            y.cross(z, x);
            // 팔뚝 틀 = 위팔 틀 · Rx(-bend): 열 x, cb·y − sb·z, sb·y + cb·z
            f1.set(y).mul((float) cb).fma((float) -sb, z);
            f2.set(y).mul((float) sb).fma((float) cb, z);
            double roll = Math.acos(clamp1(n.dot(n0)));
            double over = Math.max(0, roll - Math.toRadians(60));
            if (h == null) {
                double fx = x.dot(b);
                double fy = f1.dot(b);
                double fz = f2.dot(b);
                double a = Math.asin(clamp1(fy));
                cands[0][0] = wrap(Math.atan2(-fx, -fz));
                cands[0][1] = wrap(a + tilt);
                cands[0][2] = 0;
                a = Math.PI - Math.asin(clamp1(fy));
                cands[1][0] = wrap(Math.atan2(fx, fz));
                cands[1][1] = wrap(a + tilt);
                cands[1][2] = 0;
            } else {
                // K = Fᵀ · H = Ry(τ) · Rx(φ) · Rz(χ)
                fm.set(x, f1, f2);
                fm.transpose(k).mul(h);
                double phi = Math.asin(clamp1(-k.m21));
                double tau = Math.atan2(k.m20, k.m22);
                double chi = Math.atan2(k.m01, k.m11);
                cands[0][0] = tau;
                cands[0][1] = phi;
                cands[0][2] = chi;
                cands[1][0] = wrap(tau + Math.PI);
                cands[1][1] = wrap(Math.PI - phi);
                cands[1][2] = wrap(chi + Math.PI);
            }
            double upperOff = 0;
            if (prevUpper != null) {
                rc.set(x, y, z);
                fm.set(x, f1, f2);
                double au = rotAngle(rc, prevUpper);
                upperOff = au * au;
            }
            for (double[] cd : cands) {
                double cost = COST[0] * cd[1] * cd[1] + COST[1] * cd[0] * cd[0] + COST_DEV * cd[2] * cd[2] + COST[2] * over * over;
                if (prevUpper != null && prevHand != null) {
                    hc.set(fm).rotateY((float) cd[0]).rotateX((float) cd[1]).rotateZ((float) cd[2]);
                    double ah = rotAngle(hc, prevHand);
                    cost += CONT * (upperOff + ah * ah);
                }
                if (cost < best - 1e-9) {
                    best = cost;
                    bx.set(x);
                    bz.set(z);
                    bestTau = cd[0];
                    bestPhi = cd[1];
                    bestChi = cd[2];
                }
            }
        }
        out.setFromNormalized(new Matrix3f(bx, y, bz));
        return new float[] {(float) -bend, (float) bestTau, (float) wrap(bestPhi + Math.toRadians(wristDeg)), (float) bestChi};
    }

    /** 풀린 팔의 손 회전으로 돌린 방향 (모델 — 시험): 위팔 · Rx(팔꿈치) · Ry(비틀기) · Rx(손목) · Rz(옆 꺾기) · v */
    static Vector3f handOf(Quaternionf q, float bend, float twist, float wrist, float dev, Vector3f v) {
        v.rotateZ(dev).rotateX(wrist).rotateY(twist).rotateX(bend);
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
        out.raDev = lerpAngle(k0.raDev, k1.raDev, u);
        out.laDev = lerpAngle(k0.laDev, k1.laDev, u);
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
        out.hold = m.hold;
        out.faceYaw = m.faceYaw;
        float w = 1;
        if (m.fadeIn > 0 && t < m.fadeIn) w = ease(t / m.fadeIn, "inout");
        if (m.fadeOut > 0 && t > 1 - m.fadeOut) w = Math.min(w, ease((1 - t) / m.fadeOut, "inout"));
        out.w = w;
        // 1인칭
        out.fpX = out.fpY = out.fpZ = 0;
        out.fpQ.identity();
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
            a.q.slerp(b.q, v, out.fpQ);
        }
        if (mirror) out.mirror();
        return out;
    }
}
