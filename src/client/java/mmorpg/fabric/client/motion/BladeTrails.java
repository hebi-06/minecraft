package mmorpg.fabric.client.motion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import mmorpg.core.Combat;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgWeaponItem;
import mmorpg.fabric.client.ClientCosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 0.40.0 참격 궤적 (사용자 "참격 이펙트도 바꾸자 모션에 맞게 칼이 지나간 곳에 참격이 남게" — 사진처럼 흰 초승달 띠): 근접 무기를 든 사람이
 * 참격 궤적이 있는 동작 (poses.json "trail" 구간 — 가로 · 세로 · 회전 베기 · X자 · 내려찍기 · 내려치기 · 스킬 베기들) 을 하는 동안, 무기의 손잡이 쪽 (from) 에서
 * 무기 끝까지 지나간 자리에 띠를 남기고 수명 (TRAIL.life 틱) 동안 옅어지며 좁아짐 (오래된 쪽이 가늘어지는 초승달).
 * <ul>
 *   <li>자리: 그 동작의 자세 (MotionPoses — 렌더와 같은 자세) 로 무기 점 (WeaponBlades — 무기 모델의 손잡이 · 무기 끝) 을 옮김. 3인칭은 MotionRender ·
 *       HumanoidModel · ItemInHandLayer 와 같은 차례 (몸 방향 · 크기 · 뿌리 · 몸통 · 위팔 · 팔꿈치 · 비틀기 · 손목) 로 세계 자리, 1인칭은 MotionRender.firstPerson
 *       과 같은 차례로 카메라 공간 자리. 틱 사이도 잘게 (무기 끝이 TRAIL.maxMove 칸 넘게 움직이지 않게) 찍어 빠른 휘두르기도 매끄러운 띠</li>
 *   <li>그리기: 띠 겹 넷 — 등급 기운 (영웅 · 초월, 바깥으로 넓게) · 무기 색 빛 (무기 끝 너머 glowTo 까지) · 흰 심지 (무기 끝 ~ from) · 마검 룬 (안쪽 가는 하늘빛 줄).
 *       색은 참격 모양 (rules SLASH — Weapons.slashStyle) 그대로. 그림 blade_trail_core · glow (tools/trail_textures.py), 밝기 최대 · 반투명 · 앞뒤 두 면</li>
 *   <li>3인칭 (다른 사람 · 내 3인칭) 은 세계에 (LevelRenderer.submitEntities 끝), 내 1인칭은 손과 같은 화면 공간에 (주 손을 그린 뒤)</li>
 *   <li>사진첩 (ClientMotions.freeze): 멈춘 순간까지의 궤적을 그 순간에 멈춰 그림</li>
 * </ul>
 * 방패 · 활 · 완드 · 맨손은 궤적 없음. 요구 조건이 모자라면 동작이 없어 궤적도 없음 (서버의 회색 참격).
 */
public final class BladeTrails {
    private BladeTrails() {}

    private static final Identifier CORE_TEX = Mmorpg.id("textures/fx/blade_trail_core.png");
    private static final Identifier GLOW_TEX = Mmorpg.id("textures/fx/blade_trail_glow.png");
    private static final int LIGHT = 0xF000F0;
    private static final float MODEL_SCALE = 0.9375F;
    private static final float MODEL_DROP = 1.501F;
    private static final Vector3f HIP = new Vector3f(0, 12, 0);
    /** 1인칭 손에 든 아이템 자리 (오른손 — FirstPersonHandsAndItemsRenderer ITEM_POS) */
    private static final Vector3f FP_ITEM = new Vector3f(0.56F, -0.52F, -0.72F);

    // ------------------------------------------------------------ 설정 (rules_more TRAIL)

    static final class Cfg {
        boolean enabled = true;
        float life = 6;
        float maxStep = 0.25F;
        float maxMove = 0.12F;
        float taper = 0.12F;
        float glowTo = 1.12F;
        float aAura = 0.42F;
        float aGlow = 0.7F;
        float aCore = 0.95F;
        float aRune = 0.9F;
        final Map<String, Float> from = new HashMap<>();
        float fromDefault = 0.35F;
    }

    private static Cfg cfg;

    static Cfg cfg() {
        if (cfg != null) return cfg;
        Cfg c = new Cfg();
        try {
            Obj t = Rules.get().root().objOrNull("TRAIL");
            if (t == null) c.enabled = false;
            else {
                c.enabled = t.truthy("enabled");
                c.life = (float) Math.max(1, t.numOr("life", c.life));
                c.maxStep = (float) Math.max(0.02, t.numOr("maxStep", c.maxStep));
                c.maxMove = (float) Math.max(0.02, t.numOr("maxMove", c.maxMove));
                c.taper = (float) Mth.clamp(t.numOr("taper", c.taper), 0, 1);
                c.glowTo = (float) Math.max(1, t.numOr("glowTo", c.glowTo));
                Obj a = t.objOrNull("alpha");
                if (a != null) {
                    c.aAura = (float) a.numOr("aura", c.aAura);
                    c.aGlow = (float) a.numOr("glow", c.aGlow);
                    c.aCore = (float) a.numOr("core", c.aCore);
                    c.aRune = (float) a.numOr("rune", c.aRune);
                }
                Obj f = t.objOrNull("from");
                if (f != null) {
                    for (String k : f.keys()) {
                        if (k.equals("default")) c.fromDefault = (float) f.num(k);
                        else c.from.put(k, (float) f.num(k));
                    }
                }
            }
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] trail rules", e);
        }
        cfg = c;
        return c;
    }

    // ------------------------------------------------------------ 무기

    /** 궤적을 그리는 무기 하나: 무기 점 · 손잡이 쪽 시작 (from) · 색 (ARGB 의 RGB — 없으면 0) */
    record Spec(WeaponBlades.Blade blade, float from, int glow, int core, int aura, int rune) {}

    /** 무기 종류 · 등급 → 디자인 (보통 = 종류_lv0, 나머지 = 종류_등급_lv0 — tools/weapon_models.py 와 같은 규칙) */
    static String designOf(Weapon w) {
        String g = w.grade() != null ? w.grade().id() : "common";
        return "common".equals(g) ? w.type() + "_lv0" : w.type() + "_" + g + "_lv0";
    }

    static WeaponBlades.Blade bladeOf(Avatar a, Weapon w) {
        if (ClientCosmetics.any()) {
            String skin = ClientCosmetics.lookOf(a.getUUID()).weaponSkin(w.type());
            String d = skin == null ? null : WeaponBlades.SKIN_DESIGN.get(skin);
            if (d != null && WeaponBlades.BY_DESIGN.containsKey(d)) return WeaponBlades.BY_DESIGN.get(d);
        }
        WeaponBlades.Blade b = WeaponBlades.BY_DESIGN.get(designOf(w));
        if (b != null) return b;
        b = WeaponBlades.BY_DESIGN.get(w.type() + "_lv0");
        if (b != null) return b;
        for (Map.Entry<String, WeaponBlades.Blade> e : WeaponBlades.BY_DESIGN.entrySet()) {
            if (e.getKey().startsWith(w.type() + "_")) return e.getValue();
        }
        return null;
    }

    /** 이 사람이 든 무기의 궤적 (근접 무기 · 방패 아님 · 무기 점이 있음 — 아니면 null) */
    static Spec specOf(Avatar a) {
        ItemStack stack = a.getMainHandItem();
        if (!(stack.getItem() instanceof RpgWeaponItem item)) return null;
        Weapon w = item.weapon();
        if (w == null || !Combat.isMeleeWeapon(w) || "shield".equals(w.type())) return null;
        WeaponBlades.Blade b = bladeOf(a, w);
        if (b == null) return null;
        Cfg c = cfg();
        float from = c.from.getOrDefault(w.type(), c.fromDefault);
        Obj style = Weapons.slashStyle(w.look() != null ? w.look() : w.type());
        if (style == null) style = Weapons.slashStyle(w.type());
        double[] glow = style != null && style.has("glow") ? rgb(style.list("glow")) : new double[] {0.8, 0.9, 1.0};
        double[] core = style != null && style.has("core") ? rgb(style.list("core")) : whiten(glow, 0.8);
        double[] aura = null;
        try {
            List<Object> ga = Rules.get().obj("SLASH").list("gradeAura");
            Object g = w.gradeIndex() >= 0 && w.gradeIndex() < ga.size() ? ga.get(w.gradeIndex()) : null;
            if (g instanceof List<?> l) aura = style != null && style.has("aura") ? rgb(style.list("aura")) : rgb(l);
        } catch (RuntimeException e) {
            aura = null;
        }
        double[] rune = style != null && style.has("rune") ? rgb(style.list("rune")) : null;
        return new Spec(b, from, pack(glow), pack(core), aura == null ? 0 : pack(aura), rune == null ? 0 : pack(rune));
    }

    private static double[] rgb(List<?> c) {
        return new double[] {((Number) c.get(0)).doubleValue(), ((Number) c.get(1)).doubleValue(), ((Number) c.get(2)).doubleValue()};
    }

    private static double[] whiten(double[] c, double t) {
        return new double[] {c[0] + (1 - c[0]) * t, c[1] + (1 - c[1]) * t, c[2] + (1 - c[2]) * t};
    }

    /** RGB (0 ~ 1) → 0xRRGGBB (검정이어도 1 — 0 은 "없음") */
    private static int pack(double[] c) {
        int r = (int) Math.round(Mth.clamp(c[0], 0, 1) * 255);
        int g = (int) Math.round(Mth.clamp(c[1], 0, 1) * 255);
        int b = (int) Math.round(Mth.clamp(c[2], 0, 1) * 255);
        int v = (r << 16) | (g << 8) | b;
        return v == 0 ? 1 : v;
    }

    // ------------------------------------------------------------ 궤적

    /** 궤적의 점: 시각 (동작 시작부터 틱) · 손잡이 · 무기 끝 (3인칭 = 세계, 1인칭 = 카메라 공간) · 새 구간의 첫 점 */
    static final class Sample {
        final double t;
        final boolean start;
        final double gx;
        final double gy;
        final double gz;
        final double tx;
        final double ty;
        final double tz;

        Sample(double t, boolean start, double gx, double gy, double gz, double tx, double ty, double tz) {
            this.t = t;
            this.start = start;
            this.gx = gx;
            this.gy = gy;
            this.gz = gz;
            this.tx = tx;
            this.ty = ty;
            this.tz = tz;
        }
    }

    /** 한 번의 동작 궤적 (사람 하나 · 동작 하나) */
    static final class Trail {
        final int entity;
        final double start;
        final String motion;
        final int ticks;
        final Spec spec;
        final List<Sample> samples = new ArrayList<>();
        /** 마지막으로 찍은 시각 (동작 시작부터 틱 — 없으면 -1) · 그때 무기 끝 */
        double last = -1;
        double lx;
        double ly;
        double lz;
        /** 이번 프레임의 무기 끝 (아직 찍지 않은 머리 — 칼에 붙어 있게, 없으면 null) */
        Sample head;
        /** 사진첩: 멈춘 순간 (동작 시작부터 틱 — 멈추지 않았으면 NaN) */
        double fixed = Double.NaN;

        Trail(int entity, double start, String motion, int ticks, Spec spec) {
            this.entity = entity;
            this.start = start;
            this.motion = motion;
            this.ticks = ticks;
            this.spec = spec;
        }
    }

    private static final Map<Integer, Trail> WORLD = new HashMap<>();
    private static final List<Trail> WORLD_FADING = new ArrayList<>();
    private static Trail fp;
    private static Trail fpFading;
    /** 시험: 마지막 프레임에 그린 궤적 수 · 점 수 (세계 · 1인칭) */
    public static int lastWorldTrails;
    public static int lastWorldSamples;
    public static int lastFpSamples;

    public static void clear() {
        WORLD.clear();
        WORLD_FADING.clear();
        fp = null;
        fpFading = null;
    }

    private static double now(Minecraft mc) {
        return ClientMotions.ticks() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    // ------------------------------------------------------------ 3인칭 (세계)

    /** LevelRenderer.submitEntities 끝: 둘레 사람들의 궤적을 이어 찍고 그림 (내 1인칭은 빼고) */
    public static void submitWorld(PoseStack ps, SubmitNodeCollector collector) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            clear();
            return;
        }
        Cfg c = cfg();
        if (!c.enabled) return;
        try {
            double now = now(mc);
            Entity camEnt = mc.getCameraEntity();
            boolean fpCam = mc.options.getCameraType().isFirstPerson();
            // 하는 중인 동작
            for (Map.Entry<Integer, ClientMotions.Active> e : ClientMotions.activeMap().entrySet()) {
                int id = e.getKey();
                ClientMotions.Active a = e.getValue();
                MotionPoses.Motion m = MotionPoses.get(a.motion());
                if (m == null || m.trail.length == 0 || ClientMotions.frozenMap().containsKey(id)) continue;
                if (!(level.getEntity(id) instanceof Avatar av) || av.isRemoved()) continue;
                if (av == camEnt && fpCam) continue;
                Trail tr = WORLD.get(id);
                if (tr == null || tr.start != a.start() || !tr.motion.equals(a.motion())) {
                    if (tr != null) WORLD_FADING.add(tr);
                    WORLD.remove(id);
                    if (!shown(av)) continue;
                    Spec spec = specOf(av);
                    if (spec == null) continue;
                    tr = new Trail(id, a.start(), a.motion(), a.ticks(), spec);
                    WORLD.put(id, tr);
                }
                Trail cur = tr;
                sample(cur, m, now - a.start(), (tau, out) -> worldBlade(av, m, tau, cur, out), true);
            }
            // 사진첩: 멈춘 자세의 궤적 (그 순간까지)
            List<Trail> frozen = new ArrayList<>();
            for (Map.Entry<Integer, ClientMotions.Frozen> e : ClientMotions.frozenMap().entrySet()) {
                int id = e.getKey();
                MotionPoses.Motion m = MotionPoses.get(e.getValue().motion());
                if (m == null || m.trail.length == 0) continue;
                if (!(level.getEntity(id) instanceof Avatar av) || av.isRemoved() || (av == camEnt && fpCam) || !shown(av)) continue;
                Spec spec = specOf(av);
                if (spec == null) continue;
                Trail tr = new Trail(id, 0, m.name, m.ticks, spec);
                tr.fixed = e.getValue().t() * m.ticks;
                sample(tr, m, tr.fixed, (tau, out) -> worldBlade(av, m, tau, tr, out), false);
                frozen.add(tr);
            }
            // 오래된 것 지우기
            WORLD.values().removeIf(tr -> expire(tr, now - tr.start) && now - tr.start > tr.ticks + 1);
            WORLD_FADING.removeIf(tr -> expire(tr, now - tr.start));
            // 그리기 (카메라 기준 자리)
            Vec3 cam = mc.gameRenderer.mainCamera().position();
            List<Trail> all = new ArrayList<>(WORLD.values());
            all.addAll(WORLD_FADING);
            int n = 0;
            for (Trail tr : all) n += tr.samples.size();
            for (Trail tr : frozen) n += tr.samples.size();
            lastWorldTrails = all.size() + frozen.size();
            lastWorldSamples = n;
            Mesh mesh = new Mesh();
            for (Trail tr : all) build(mesh, tr, now - tr.start, cam.x, cam.y, cam.z);
            for (Trail tr : frozen) build(mesh, tr, tr.fixed, cam.x, cam.y, cam.z);
            mesh.submit(ps, collector);
        } catch (RuntimeException err) {
            Mmorpg.LOG.warn("[mmorpg] blade trails", err);
            clear();
        }
    }

    /** 이 사람의 모습이 동작 자세로 그려지는지 (헤엄 · 겉날개 · 관전 · 잠 — 자세 없음) */
    private static boolean shown(Avatar av) {
        return !av.isVisuallySwimming() && !av.isFallFlying() && !av.isSpectator() && !av.isSleeping() && !av.isInvisible();
    }

    /** 오래된 점을 지우고 남은 것이 없으면 true (local = 지금 · 동작 시작부터 틱) */
    private static boolean expire(Trail tr, double local) {
        float life = cfg().life;
        Iterator<Sample> it = tr.samples.iterator();
        while (it.hasNext()) {
            if (local - it.next().t > life) it.remove();
            else break;
        }
        if (tr.head != null && local - tr.head.t > life) tr.head = null;
        return tr.samples.isEmpty() && tr.head == null;
    }

    /** 무기 점 계산: 시각 tau (동작 시작부터 틱) 의 손잡이 · 무기 끝 → out[6] (false 면 못 구함) */
    interface BladeAt {
        boolean at(double tau, double[] out);
    }

    /**
     * 궤적 구간 안에서 지난번부터 local (동작 시작부터 지금 틱) 까지 점을 찍음: 기본 간격 maxStep, 무기 끝이 maxMove 칸 넘게 움직이면 간격을 반씩 (최소 0.01틱).
     * 구간 끝은 정확히 찍고, keepHead 면 이번 프레임의 무기 끝 (머리) 도 따로 둠 (다음 프레임에 다시 계산)
     */
    static void sample(Trail tr, MotionPoses.Motion m, double local, BladeAt at, boolean keepHead) {
        Cfg c = cfg();
        double[] p = new double[6];
        tr.head = null;
        for (float[] win : m.trail) {
            double w0 = win[0] * tr.ticks;
            double w1 = win[1] * tr.ticks;
            if (local < w0 || tr.last >= w1 - 1e-6) continue;
            double end = Math.min(local, w1);
            boolean first = tr.last < w0 - 1e-6;
            if (first) {
                if (!at.at(w0, p)) return;
                commit(tr, w0, true, p);
            }
            int guard = 0;
            while (tr.last < end - 1e-6 && guard++ < 4000) {
                double dt = Math.min(c.maxStep, end - tr.last);
                double tau = tr.last + dt;
                if (!at.at(tau, p)) return;
                while (dt > 0.011 && dist(p[3], p[4], p[5], tr.lx, tr.ly, tr.lz) > c.maxMove) {
                    dt *= 0.5;
                    tau = tr.last + dt;
                    if (!at.at(tau, p)) return;
                }
                // 지금 (구간 끝 전) 에 닿음: 머리로 두고 다음 프레임에 다시 (사진첩 — 멈춘 궤적은 찍음)
                if (tau >= end - 1e-6 && end < w1 - 1e-6) {
                    if (keepHead) tr.head = new Sample(tau, false, p[0], p[1], p[2], p[3], p[4], p[5]);
                    else commit(tr, tau, false, p);
                    break;
                }
                commit(tr, tau, false, p);
            }
        }
    }

    private static void commit(Trail tr, double tau, boolean start, double[] p) {
        tr.samples.add(new Sample(tau, start, p[0], p[1], p[2], p[3], p[4], p[5]));
        tr.last = tau;
        tr.lx = p[3];
        tr.ly = p[4];
        tr.lz = p[5];
    }

    private static double dist(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static final Pose POSE = new Pose();
    private static final Vector3f V = new Vector3f();

    /** 3인칭: 시각 tau 의 손잡이 · 무기 끝 (세계) */
    private static boolean worldBlade(Avatar av, MotionPoses.Motion m, double tau, Trail tr, double[] out) {
        boolean left = av.getMainArm() == HumanoidArm.LEFT;
        MotionPoses.evaluate(m, (float) Math.max(0, Math.min(1, tau / tr.ticks)), left, POSE);
        // 사람 자리 · 몸 방향: 그 시각의 틱 사이 몫 (지금 틱보다 앞이면 앞 틱 자리)
        double abs = tr.start + tau;
        float part = (float) Mth.clamp(abs - ClientMotions.ticks(), 0, 1);
        if (!Double.isNaN(tr.fixed)) part = 1;
        double x = Mth.lerp(part, av.xo, av.getX());
        double y = Mth.lerp(part, av.yo, av.getY());
        double z = Mth.lerp(part, av.zo, av.getZ());
        float yaw = Mth.rotLerp(part, av.yBodyRotO, av.yBodyRot);
        float scale = av.getScale();
        boolean crouch = av.isCrouching();
        if (crouch) y -= scale * 2.0 / 16.0;
        WeaponBlades.Blade b = tr.spec.blade();
        float debugYaw = av.getId() == MotionRender.debugEntity ? MotionRender.debugYaw : 0;
        thirdPerson(POSE, left, crouch, b.gripX(), b.gripY(), b.gripZ(), V);
        toWorld(V, yaw, debugYaw, scale);
        out[0] = x + V.x;
        out[1] = y + V.y;
        out[2] = z + V.z;
        thirdPerson(POSE, left, crouch, b.tipX(), b.tipY(), b.tipZ(), V);
        toWorld(V, yaw, debugYaw, scale);
        out[3] = x + V.x;
        out[4] = y + V.y;
        out[5] = z + V.z;
        return true;
    }

    /** 우리 공간 (발 원점 · +x 오른쪽 · +y 위 · -z 앞) → 세계 방향 (몸 방향 · 크기): LivingEntityRenderer 의 Y 180 - 몸 방향 */
    private static void toWorld(Vector3f v, float bodyYaw, float debugYaw, float scale) {
        if (debugYaw != 0) v.rotateY(debugYaw * Mth.DEG_TO_RAD);
        v.mul(scale).rotateY((180F - bodyYaw) * Mth.DEG_TO_RAD);
    }

    /**
     * 3인칭 무기 점 하나: 오른팔 공간 (블록 — 팔 축 기준 · 팔 회전 전, x 왼쪽 · y 아래 · z 뒤 — 왼손은 x 를 뒤집어 씀) → 우리 공간 (발 원점 · +x 오른쪽 · +y 위 ·
     * -z 앞, 블록 — 몸 방향 · 크기 전). 차례: 손 (MotionRender.hand — 손목 · 옆 꺾기 → 팔뚝 비틀기 → 팔꿈치) → 위팔 회전 · 어깨 자리
     * (MotionRender.applyModel — 몸통을 엉덩이 축으로 돌린 어깨) → 모델 (0.9375 · 뒤집기 · 1.501 내림) → 뿌리 (앞구르기 · 회전 · 내딛기)
     */
    static void thirdPerson(Pose p, boolean left, boolean crouch, float ax, float ay, float az, Vector3f out) {
        boolean right = !left;
        float w = p.w;
        boolean hasArm = p.has(right ? Pose.RA : Pose.LA);
        float bend = hasArm ? (right ? p.raBend : p.laBend) * w : 0;
        float tw = hasArm ? (right ? p.raTwist : p.laTwist) * w : 0;
        float wr = hasArm ? (right ? p.raWrist : p.laWrist) * w : 0;
        float dv = hasArm ? (right ? p.raDev : p.laDev) * w : 0;
        float cx = right ? -1 : 1; // 팔 상자 가운데 (넓은 팔)
        // 무기 점 (픽셀)
        Vector3f v = out.set(right ? ax * 16 : -ax * 16, ay * 16, az * 16);
        // 손목 (cx, 9.5, 0): Rx(손목) · Rz(옆 꺾기)
        v.sub(cx, 9.5F, 0).rotateZ(dv).rotateX(wr).add(cx, 9.5F, 0);
        // 팔뚝 비틀기 (cx, *, 0) 둘레
        v.sub(cx, 0, 0).rotateY(tw).add(cx, 0, 0);
        // 팔꿈치 (cx, 4, 0)
        v.sub(cx, 4, 0).rotateX(bend).add(cx, 4, 0);
        // 위팔 회전 (바닐라 자세와 w 만큼 섞임 — 궤적 구간은 w = 1)
        Quaternionf q = new Quaternionf().slerp(right ? p.ra : p.la, hasArm ? w : 0);
        q.transform(v);
        // 어깨 자리
        float sx = right ? -5 : 5;
        float sy = crouch ? 5.2F : 2F;
        float sz = 0;
        if (p.has(Pose.BODY) && !crouch) {
            Vector3f s = new Vector3f(sx, 2, 0).sub(HIP).rotateX(p.lean * w).rotateY(p.twist * w).rotateZ(p.side * w);
            // Matrix3f.rotationZYX(side, twist, lean) = Rz · Ry · Rx — 벡터에는 Rx 먼저
            s.add(HIP);
            sx = sx + (s.x - sx) * w;
            sy = sy + (s.y - sy) * w;
            sz = sz + (s.z - sz) * w;
        }
        // 모델 (블록): 어깨 + 팔 공간
        float mx = sx / 16F + v.x / 16F;
        float my = sy / 16F + v.y / 16F;
        float mz = sz / 16F + v.z / 16F;
        // 뒤집기 (-1, -1, 1) · 0.9375 · 1.501 내림
        out.set(-mx * MODEL_SCALE, (MODEL_DROP - my) * MODEL_SCALE, mz * MODEL_SCALE);
        if (p.has(Pose.ROOT)) {
            if (p.flip != 0) {
                out.y -= MotionRender.FLIP_PIVOT;
                out.rotateX(-p.flip);
                out.y += MotionRender.FLIP_PIVOT;
            }
            if (p.spin != 0) out.rotateY(p.spin);
            out.add(0, p.up * w, -p.fwd * w);
        }
    }

    // ------------------------------------------------------------ 1인칭

    /**
     * 내 1인칭 (주 손을 그린 뒤 — FirstPersonHandsAndItemsRenderer.submitArmWithItem 끝, ps = 손을 그리기 시작한 자리): 1인칭이면 궤적을 이어 찍고 그림
     */
    public static void submitFirstPerson(PoseStack ps, SubmitNodeCollector collector) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Cfg c = cfg();
        if (!c.enabled) return;
        try {
            Avatar me = mc.player;
            double now = now(mc);
            ClientMotions.Frozen fz = ClientMotions.frozenMap().get(me.getId());
            ClientMotions.Active a = ClientMotions.activeMap().get(me.getId());
            Trail frozen = null;
            if (fz != null) {
                MotionPoses.Motion m = MotionPoses.get(fz.motion());
                Spec spec = m == null || m.trail.length == 0 ? null : specOf(me);
                if (spec != null) {
                    frozen = new Trail(me.getId(), 0, m.name, m.ticks, spec);
                    frozen.fixed = fz.t() * m.ticks;
                    Trail f = frozen;
                    sample(f, m, f.fixed, (tau, out) -> fpBlade(me, m, tau, f, out), false);
                }
            } else if (a != null) {
                MotionPoses.Motion m = MotionPoses.get(a.motion());
                if (m != null && m.trail.length > 0) {
                    if (fp == null || fp.start != a.start() || !fp.motion.equals(a.motion())) {
                        if (fp != null) fpFading = fp;
                        Spec spec = specOf(me);
                        fp = spec == null ? null : new Trail(me.getId(), a.start(), a.motion(), a.ticks(), spec);
                    }
                    if (fp != null) {
                        Trail f = fp;
                        sample(f, m, now - a.start(), (tau, out) -> fpBlade(me, m, tau, f, out), true);
                    }
                }
            }
            if (fp != null && expire(fp, now - fp.start) && now - fp.start > fp.ticks + 1) fp = null;
            if (fpFading != null && expire(fpFading, now - fpFading.start)) fpFading = null;
            Mesh mesh = new Mesh();
            int n = 0;
            if (frozen != null) {
                build(mesh, frozen, frozen.fixed, 0, 0, 0);
                n += frozen.samples.size();
            } else {
                if (fp != null) {
                    build(mesh, fp, now - fp.start, 0, 0, 0);
                    n += fp.samples.size();
                }
                if (fpFading != null) {
                    build(mesh, fpFading, now - fpFading.start, 0, 0, 0);
                    n += fpFading.samples.size();
                }
            }
            lastFpSamples = n;
            mesh.submit(ps, collector);
        } catch (RuntimeException err) {
            Mmorpg.LOG.warn("[mmorpg] first person trail", err);
            fp = null;
            fpFading = null;
        }
    }

    /** 1인칭: 시각 tau 의 손잡이 · 무기 끝 (손을 그리기 시작한 자리 기준 화면 공간) */
    private static boolean fpBlade(Avatar me, MotionPoses.Motion m, double tau, Trail tr, double[] out) {
        boolean left = me.getMainArm() == HumanoidArm.LEFT;
        MotionPoses.evaluate(m, (float) Math.max(0, Math.min(1, tau / tr.ticks)), left, POSE);
        WeaponBlades.Blade b = tr.spec.blade();
        firstPerson(POSE, left, b, 0, V);
        out[0] = V.x;
        out[1] = V.y;
        out[2] = V.z;
        firstPerson(POSE, left, b, 1, V);
        out[3] = V.x;
        out[4] = V.y;
        out[5] = V.z;
        return true;
    }

    /**
     * 1인칭 무기 점 (손잡이 → 무기 끝 frac): MotionRender.firstPerson 과 같은 차례 — 손 자리 (바닐라 흔들림은 되돌린 것으로) + fp 옮김 × w,
     * 손잡이를 축으로 fp 회전 (단위 → fpQ 를 w 만큼). 왼손은 x 를 뒤집음 (Pose.mirror 가 fp 를 이미 뒤집음)
     */
    static void firstPerson(Pose p, boolean left, WeaponBlades.Blade b, float frac, Vector3f out) {
        float s = left ? -1 : 1;
        float w = p.w;
        Vector3f v = new Vector3f(s * (b.fpTipX() - b.fpGripX()), b.fpTipY() - b.fpGripY(), b.fpTipZ() - b.fpGripZ()).mul(frac);
        new Quaternionf().slerp(p.fpQ, w).transform(v);
        out.set(s * b.fpGripX(), b.fpGripY(), b.fpGripZ()).add(p.fpX * w, p.fpY * w, p.fpZ * w).add(v);
    }

    // ------------------------------------------------------------ 띠

    /** 띠 겹 하나의 사각형들 (앞뒤 두 면) — 자리는 그리는 기준 (세계는 카메라 기준) */
    static final class Layer {
        float[] v = new float[1000];
        int[] col = new int[200];
        /** 꼭짓점 수 */
        int n;

        void add(float x, float y, float z, float u, float vv, int argb) {
            if ((n + 1) * 5 > v.length) v = java.util.Arrays.copyOf(v, v.length * 2);
            if (n + 1 > col.length) col = java.util.Arrays.copyOf(col, col.length * 2);
            int i = n * 5;
            v[i] = x;
            v[i + 1] = y;
            v[i + 2] = z;
            v[i + 3] = u;
            v[i + 4] = vv;
            col[n++] = argb;
        }
    }

    static final class Mesh {
        final Layer aura = new Layer();
        final Layer glow = new Layer();
        final Layer core = new Layer();
        final Layer rune = new Layer();

        void submit(PoseStack ps, SubmitNodeCollector collector) {
            put(ps, collector, aura, GLOW_TEX);
            put(ps, collector, glow, GLOW_TEX);
            put(ps, collector, core, CORE_TEX);
            put(ps, collector, rune, GLOW_TEX);
        }

        private static void put(PoseStack ps, SubmitNodeCollector collector, Layer l, Identifier tex) {
            if (l.n == 0) return;
            float[] data = java.util.Arrays.copyOf(l.v, l.n * 5);
            int[] cols = java.util.Arrays.copyOf(l.col, l.n);
            RenderType type = RenderTypes.text(tex);
            collector.submitCustomGeometry(ps, type, (pose, vc) -> emit(pose, vc, data, cols));
        }

        /** 꼭짓점 (그림 띠 — 위치 · 색 · 그림 자리 · 밝기 최대; 쓰지 않는 요소 (겹침 · 법선) 는 형식에 없으면 무시됨) */
        private static void emit(PoseStack.Pose pose, VertexConsumer vc, float[] d, int[] cols) {
            for (int k = 0; k < cols.length; k++) {
                int i = k * 5;
                vc.addVertex(pose, d[i], d[i + 1], d[i + 2]).setColor(cols[k]).setUv(d[i + 3], d[i + 4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT)
                        .setNormal(pose, 0, 1, 0);
            }
        }
    }

    /**
     * 궤적 하나를 띠로: 점마다 나이 k (0 = 지금 · 1 = 수명 끝) 로 옅어지고 (1 - k)^1.3, 안쪽 가장자리가 바깥 쪽으로 좁아짐 (taper + (1 - taper)(1 - k)^0.6).
     * 겹: 기운 (등급 · 바깥으로 넓게) · 빛 (무기 끝 너머 glowTo) · 심지 (무기 끝 ~ from) · 룬 (마검 — 안쪽 가는 줄). local = 지금 (동작 시작부터 틱), o = 그리는 기준
     */
    static void build(Mesh mesh, Trail tr, double local, double ox, double oy, double oz) {
        List<Sample> pts = new ArrayList<>(tr.samples);
        if (tr.head != null) pts.add(tr.head);
        if (pts.size() < 2) return;
        Cfg c = cfg();
        Spec s = tr.spec;
        float from = s.from();
        float g = c.glowTo;
        for (int i = 0; i + 1 < pts.size(); i++) {
            Sample a = pts.get(i);
            Sample b = pts.get(i + 1);
            if (b.start) continue;
            float ka = (float) Mth.clamp((local - a.t) / c.life, 0, 1);
            float kb = (float) Mth.clamp((local - b.t) / c.life, 0, 1);
            if (ka >= 1 && kb >= 1) continue;
            if (s.aura() != 0) band(mesh.aura, a, b, ka, kb, g + 0.14F, from + (1 - from) * 0.2F, s.aura(), c.aAura, c, ox, oy, oz);
            band(mesh.glow, a, b, ka, kb, g, from * 0.85F, s.glow(), c.aGlow, c, ox, oy, oz);
            band(mesh.core, a, b, ka, kb, 1F, from, s.core(), c.aCore, c, ox, oy, oz);
            if (s.rune() != 0) band(mesh.rune, a, b, ka, kb, from + (1 - from) * 0.58F, from + (1 - from) * 0.34F, s.rune(), c.aRune, c, ox, oy, oz);
        }
    }

    /** 점 a → b 사이 띠 한 칸 (바깥 = 손잡이 → 무기 끝 outF, 안 = inF — 나이만큼 바깥으로 좁아짐) */
    private static void band(Layer l, Sample a, Sample b, float ka, float kb, float outF, float inF, int rgb, float alpha, Cfg c, double ox, double oy,
            double oz) {
        float aa = alpha * (float) Math.pow(1 - ka, 1.3);
        float ab = alpha * (float) Math.pow(1 - kb, 1.3);
        if (aa <= 0.004F && ab <= 0.004F) return;
        float wa = c.taper + (1 - c.taper) * (float) Math.pow(1 - ka, 0.6);
        float wb = c.taper + (1 - c.taper) * (float) Math.pow(1 - kb, 0.6);
        // 바깥 · 안 (나이로 좁힌) 자리
        double[] ao = at(a, outF);
        double[] bo = at(b, outF);
        double[] ai = at(a, outF + (inF - outF) * wa);
        double[] bi = at(b, outF + (inF - outF) * wb);
        int ca = argb(aa, rgb);
        int cb = argb(ab, rgb);
        float x0 = (float) (ao[0] - ox), y0 = (float) (ao[1] - oy), z0 = (float) (ao[2] - oz);
        float x1 = (float) (bo[0] - ox), y1 = (float) (bo[1] - oy), z1 = (float) (bo[2] - oz);
        float x2 = (float) (bi[0] - ox), y2 = (float) (bi[1] - oy), z2 = (float) (bi[2] - oz);
        float x3 = (float) (ai[0] - ox), y3 = (float) (ai[1] - oy), z3 = (float) (ai[2] - oz);
        // 앞면
        l.add(x0, y0, z0, ka, 0, ca);
        l.add(x1, y1, z1, kb, 0, cb);
        l.add(x2, y2, z2, kb, 1, cb);
        l.add(x3, y3, z3, ka, 1, ca);
        // 뒷면
        l.add(x3, y3, z3, ka, 1, ca);
        l.add(x2, y2, z2, kb, 1, cb);
        l.add(x1, y1, z1, kb, 0, cb);
        l.add(x0, y0, z0, ka, 0, ca);
    }

    /** 점의 손잡이 → 무기 끝 f 자리 */
    private static double[] at(Sample s, float f) {
        return new double[] {s.gx + (s.tx - s.gx) * f, s.gy + (s.ty - s.gy) * f, s.gz + (s.tz - s.gz) * f};
    }

    private static int argb(float a, int rgb) {
        int al = Math.round(Mth.clamp(a, 0, 1) * 255);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    // ------------------------------------------------------------ 시험 (사진첩 — 렌더가 실제로 그린 무기 자리와 BladeTrails 의 계산을 비교)

    /** 시험: 이 사람 (id) 의 주 손 무기를 그릴 때 그 자리를 적어 둠 */
    public static int captureEntity = Integer.MIN_VALUE;
    /** 시험: 렌더가 그린 주 손 무기의 손잡이 · 무기 끝 (세계 — PlayerModel.translateToHand 끝의 pose + 카메라 자리) */
    public static double[] captured;
    /** 시험: 내 1인칭 무기 자리를 적어 둠 */
    public static boolean captureFp;
    /** 시험: 1인칭 렌더가 그린 손잡이 · 무기 끝 (손을 그리기 시작한 자리 기준 — MotionRender.firstPerson 끝) */
    public static double[] capturedFp;

    /** PlayerModelMixin (translateToHand 끝 — MotionRender.hand 뒤): 시험 대상이면 팔 공간의 무기 점을 세계 자리로 */
    public static void captureHand(net.minecraft.client.renderer.entity.state.AvatarRenderState state, HumanoidArm arm, PoseStack ps) {
        if (state.id != captureEntity || arm != state.mainArm) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(state.id) instanceof Avatar av)) return;
        Spec spec = specOf(av);
        if (spec == null) return;
        WeaponBlades.Blade b = spec.blade();
        float s = arm == HumanoidArm.LEFT ? -1 : 1;
        Vector3f g = ps.last().pose().transformPosition(s * b.gripX(), b.gripY(), b.gripZ(), new Vector3f());
        Vector3f t = ps.last().pose().transformPosition(s * b.tipX(), b.tipY(), b.tipZ(), new Vector3f());
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        captured = new double[] {cam.x + g.x, cam.y + g.y, cam.z + g.z, cam.x + t.x, cam.y + t.y, cam.z + t.z};
    }

    /** MotionRender.firstPerson 끝: 시험이면 손을 그리기 시작한 자리 (base) 기준의 무기 손잡이 · 끝 */
    public static void captureFirstPerson(org.joml.Matrix4f base, org.joml.Matrix4f cur, boolean left) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Spec spec = specOf(mc.player);
        if (spec == null) return;
        WeaponBlades.Blade b = spec.blade();
        float s = left ? -1 : 1;
        org.joml.Matrix4f rel = new org.joml.Matrix4f(base).invertAffine().mul(cur);
        // 무기 자리 (손에 든 아이템 자리 기준) = 쉬는 자세의 카메라 공간 자리 - 손 자리
        Vector3f g = rel.transformPosition(s * (b.fpGripX() - FP_ITEM.x), b.fpGripY() - FP_ITEM.y, b.fpGripZ() - FP_ITEM.z, new Vector3f());
        Vector3f t = rel.transformPosition(s * (b.fpTipX() - FP_ITEM.x), b.fpTipY() - FP_ITEM.y, b.fpTipZ() - FP_ITEM.z, new Vector3f());
        capturedFp = new double[] {g.x, g.y, g.z, t.x, t.y, t.z};
    }

    /** 시험: 이 사람의 지금 (멈춘) 자세에서 BladeTrails 가 계산한 손잡이 · 무기 끝 (세계) */
    public static double[] testWorldBlade(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(entityId) instanceof Avatar av)) return null;
        ClientMotions.Frozen f = ClientMotions.frozenMap().get(entityId);
        if (f == null) return null;
        MotionPoses.Motion m = MotionPoses.get(f.motion());
        Spec spec = specOf(av);
        if (m == null || spec == null) return null;
        Trail tr = new Trail(entityId, 0, m.name, m.ticks, spec);
        tr.fixed = f.t() * m.ticks;
        double[] out = new double[6];
        worldBlade(av, m, tr.fixed, tr, out);
        return out;
    }

    /** 시험: 내 지금 (멈춘) 자세에서 BladeTrails 가 계산한 1인칭 손잡이 · 무기 끝 */
    public static double[] testFirstPersonBlade() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        ClientMotions.Frozen f = ClientMotions.frozenMap().get(mc.player.getId());
        if (f == null) return null;
        MotionPoses.Motion m = MotionPoses.get(f.motion());
        Spec spec = specOf(mc.player);
        if (m == null || spec == null) return null;
        Trail tr = new Trail(mc.player.getId(), 0, m.name, m.ticks, spec);
        double[] out = new double[6];
        fpBlade(mc.player, m, f.t() * m.ticks, tr, out);
        return out;
    }
}
