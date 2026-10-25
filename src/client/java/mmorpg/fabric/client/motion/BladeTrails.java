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
import mmorpg.fabric.motion.MotionNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
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
import net.minecraft.world.entity.player.PlayerModelType;
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
 * 0.41.0 평타 강화 모습 (MotionNet.Look — 평타 강화 룬 · 직업 평타 태세, rules_more SWING_LOOK): 빛 · 기운을 모습 색으로 (심지는 빛을 TRAIL.look.core 만큼 희게,
 * 기운은 등급과 상관없이), 무기 끝을 손잡이 → 끝 방향으로 늘여 빛의 바깥 가장자리가 넓어진 참격 범위만큼 더 나감 ((배율 − 1) × 범위 ÷ glowTo 칸 — 0.40.0 부터
 * 범위 거리 = 빛의 바깥 가장자리), 1인칭은 그 TRAIL.look.fp 몫 (띠 너비 상한도 같은 비율로).
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
        /** 빛 겹의 안 가장자리 (from ~ 무기 끝 사이 몫) · 기운 겹의 바깥 (glowTo 너머) · 안 (몫) */
        float glowIn = 0.35F;
        float auraOut = 0.07F;
        float auraIn = 0.55F;
        float aAura = 0.32F;
        float aGlow = 0.6F;
        float aCore = 0.95F;
        float aRune = 0.9F;
        final Map<String, Float> from = new HashMap<>();
        float fromDefault = 0.35F;
        /** 1인칭 띠: 너비 (무기 길이 × width — 최대 maxWidth 칸) · 불투명 배율 */
        float fpWidth = 0.6F;
        float fpMaxWidth = 0.6F;
        float fpAlpha = 0.85F;
        /** 카메라 가까이 옅어짐 (칸 — 이 거리 아래 0 · 위 1): 1인칭 띠 · 세계 띠 */
        float nearFp0 = 0.4F;
        float nearFp1 = 1.2F;
        float nearWorld0 = 0.6F;
        float nearWorld1 = 1.8F;
        /** 0.41.0 평타 강화 모습: 심지 = 빛을 이만큼 희게 · 1인칭 늘임 몫 (세계 늘임 ×) */
        float lookCore = 0.7F;
        float lookFp = 0.5F;
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
                Obj ly = t.objOrNull("layers");
                if (ly != null) {
                    c.glowIn = (float) Mth.clamp(ly.numOr("glowIn", c.glowIn), 0, 1);
                    c.auraOut = (float) Math.max(0, ly.numOr("auraOut", c.auraOut));
                    c.auraIn = (float) Mth.clamp(ly.numOr("auraIn", c.auraIn), 0, 1);
                }
                Obj fpc = t.objOrNull("fp");
                if (fpc != null) {
                    c.fpWidth = (float) Math.max(0.05, fpc.numOr("width", c.fpWidth));
                    c.fpMaxWidth = (float) Math.max(0.05, fpc.numOr("maxWidth", c.fpMaxWidth));
                    c.fpAlpha = (float) Mth.clamp(fpc.numOr("alpha", c.fpAlpha), 0, 1);
                }
                Obj near = t.objOrNull("near");
                if (near != null) {
                    if (near.has("fp")) {
                        List<Object> l = near.list("fp");
                        c.nearFp0 = ((Number) l.get(0)).floatValue();
                        c.nearFp1 = Math.max(c.nearFp0 + 0.01F, ((Number) l.get(1)).floatValue());
                    }
                    if (near.has("world")) {
                        List<Object> l = near.list("world");
                        c.nearWorld0 = ((Number) l.get(0)).floatValue();
                        c.nearWorld1 = Math.max(c.nearWorld0 + 0.01F, ((Number) l.get(1)).floatValue());
                    }
                }
                Obj lk = t.objOrNull("look");
                if (lk != null) {
                    c.lookCore = (float) Mth.clamp(lk.numOr("core", c.lookCore), 0, 1);
                    c.lookFp = (float) Math.max(0, lk.numOr("fp", c.lookFp));
                }
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

    /**
     * 궤적을 그리는 무기 하나: 무기 점 · 손잡이 쪽 시작 (from) · 색 (ARGB 의 RGB — 없으면 0) · 0.41.0 평타 강화 모습의 무기 끝 늘임 (세계 칸 · 1인칭 칸 — 없으면 0)
     */
    record Spec(WeaponBlades.Blade blade, float from, int glow, int core, int aura, int rune, float ext, float extFp) {}

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
        return specOf(a, null, null);
    }

    /** 같은 것 — 0.41.0 평타 강화 모습 look (null · NONE = 없음) 으로 동작 motion 을 휘두를 때 (색 · 무기 끝 늘임) */
    static Spec specOf(Avatar a, String motion, MotionNet.Look look) {
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
        float ext = 0;
        float extFp = 0;
        if (look != null && !look.none()) {
            if (look.glow() != 0) {
                glow = unpack(look.glow());
                core = whiten(glow, c.lookCore);
                rune = null; // 모습 색만 (마검의 안쪽 하늘빛 줄은 뺌)
            }
            if (look.aura() != 0) aura = unpack(look.aura());
            mmorpg.core.Motions.Area area = motion == null ? null : mmorpg.core.Motions.area(motion, w.type(), w.gradeIndex());
            double k = area == null ? 1 : look.scaleAt(area.range());
            if (k > 1 + 1e-6) {
                ext = (float) ((k - 1) * area.range() / c.glowTo);
                extFp = ext * c.lookFp;
            }
        }
        return new Spec(b, from, pack(glow), pack(core), aura == null ? 0 : pack(aura), rune == null ? 0 : pack(rune), ext, extFp);
    }

    /** 0xRRGGBB → RGB (0 ~ 1) */
    private static double[] unpack(int v) {
        return new double[] {((v >> 16) & 0xFF) / 255.0, ((v >> 8) & 0xFF) / 255.0, (v & 0xFF) / 255.0};
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
                    Spec spec = specOf(av, a.motion(), a.look());
                    if (spec == null) continue;
                    noteSpec(spec, a.look());
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
                Spec spec = specOf(av, m.name, frozenLook);
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
            for (Trail tr : all) build(mesh, tr, now - tr.start, cam.x, cam.y, cam.z, false, 1);
            for (Trail tr : frozen) build(mesh, tr, tr.fixed, cam.x, cam.y, cam.z, false, 1);
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
        boolean slim = slim(av);
        WeaponBlades.Blade b = tr.spec.blade();
        float debugYaw = av.getId() == MotionRender.debugEntity ? MotionRender.debugYaw : 0;
        thirdPerson(POSE, left, crouch, slim, b.gripX(), b.gripY(), b.gripZ(), V);
        toWorld(V, yaw, debugYaw, scale);
        out[0] = x + V.x;
        out[1] = y + V.y;
        out[2] = z + V.z;
        thirdPerson(POSE, left, crouch, slim, b.tipX(), b.tipY(), b.tipZ(), V);
        toWorld(V, yaw, debugYaw, scale);
        out[3] = x + V.x;
        out[4] = y + V.y;
        out[5] = z + V.z;
        extend(out, tr.spec.ext());
        return true;
    }

    /** 0.41.0 평타 강화 모습: 무기 끝을 손잡이 → 끝 방향으로 ext 칸 더 (궤적 = 넓어진 범위). out = {손잡이 xyz, 끝 xyz} */
    static void extend(double[] out, float ext) {
        if (!(ext > 0)) return;
        double dx = out[3] - out[0];
        double dy = out[4] - out[1];
        double dz = out[5] - out[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) return;
        double k = ext / len;
        out[3] += dx * k;
        out[4] += dy * k;
        out[5] += dz * k;
    }

    /** 가는 팔 모델 (스킨 모양 slim — 팔 3픽셀): 어깨 높이 · 손 자리 · 팔 가운데가 넓은 팔과 다름 (thirdPerson) */
    static boolean slim(Avatar av) {
        return av instanceof ClientAvatarEntity c && c.getSkin() != null && c.getSkin().model() == PlayerModelType.SLIM;
    }

    /** 우리 공간 (발 원점 · +x 오른쪽 · +y 위 · -z 앞) → 세계 방향 (몸 방향 · 크기): LivingEntityRenderer 의 Y 180 - 몸 방향 */
    private static void toWorld(Vector3f v, float bodyYaw, float debugYaw, float scale) {
        if (debugYaw != 0) v.rotateY(debugYaw * Mth.DEG_TO_RAD);
        v.mul(scale).rotateY((180F - bodyYaw) * Mth.DEG_TO_RAD);
    }

    /**
     * 3인칭 무기 점 하나: 오른팔 공간 (블록 — 팔 축 기준 · 팔 회전 전, x 왼쪽 · y 아래 · z 뒤 — 왼손은 x 를 뒤집어 씀) → 우리 공간 (발 원점 · +x 오른쪽 · +y 위 ·
     * -z 앞, 블록 — 몸 방향 · 크기 전). 차례: 손 (MotionRender.hand — 손목 · 옆 꺾기 → 팔뚝 비틀기 → 팔꿈치) → 위팔 회전 · 어깨 자리
     * (MotionRender.applyModel — 몸통을 엉덩이 축으로 돌린 어깨) → 모델 (0.9375 · 뒤집기 · 1.501 내림) → 뿌리 (앞구르기 · 회전 · 내딛기).
     * 가는 팔 (slim): 바닐라 어깨 높이 2.5 (넓은 팔 2), PlayerModel.translateToHand 가 팔 자리를 x 로 0.5 옮긴 채 손을 잡음, 팔 상자 가운데 (팔꿈치 ·
     * 비틀기 · 손목 축 — BendablePart 의 상자 가운데) ∓0.5 (넓은 팔 ∓1)
     */
    static void thirdPerson(Pose p, boolean left, boolean crouch, boolean slim, float ax, float ay, float az, Vector3f out) {
        boolean right = !left;
        float w = p.w;
        boolean hasArm = p.has(right ? Pose.RA : Pose.LA);
        float bend = hasArm ? (right ? p.raBend : p.laBend) * w : 0;
        float tw = hasArm ? (right ? p.raTwist : p.laTwist) * w : 0;
        float wr = hasArm ? (right ? p.raWrist : p.laWrist) * w : 0;
        float dv = hasArm ? (right ? p.raDev : p.laDev) * w : 0;
        float half = slim ? 0.5F : 1F;
        float cx = right ? -half : half; // 팔 상자 가운데
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
        float base = slim ? 2.5F : 2F; // 바닐라 어깨 높이
        float sy = crouch ? base + 3.2F : base;
        float sz = 0;
        if (p.has(Pose.BODY) && !crouch) {
            Vector3f s = new Vector3f(sx, 2, 0).sub(HIP).rotateX(p.lean * w).rotateY(p.twist * w).rotateZ(p.side * w);
            // Matrix3f.rotationZYX(side, twist, lean) = Rz · Ry · Rx — 벡터에는 Rx 먼저
            s.add(HIP);
            sx = sx + (s.x - sx) * w;
            sy = sy + (s.y - sy) * w;
            sz = sz + (s.z - sz) * w;
        }
        if (slim) sx += right ? 0.5F : -0.5F; // translateToHand: 가는 팔은 팔 자리를 옮긴 채 손을 잡음
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
                Spec spec = m == null || m.trail.length == 0 ? null : specOf(me, m.name, frozenLook);
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
                        Spec spec = specOf(me, a.motion(), a.look());
                        if (spec != null) noteSpec(spec, a.look());
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
            float side = me.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
            Mesh mesh = new Mesh();
            int n = 0;
            if (frozen != null) {
                build(mesh, frozen, frozen.fixed, 0, 0, 0, true, side);
                n += frozen.samples.size();
            } else {
                if (fp != null) {
                    build(mesh, fp, now - fp.start, 0, 0, 0, true, side);
                    n += fp.samples.size();
                }
                if (fpFading != null) {
                    build(mesh, fpFading, now - fpFading.start, 0, 0, 0, true, side);
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
        extend(out, tr.spec.extFp());
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
     * 겹: 기운 (등급 — glowTo 너머 auraOut ~ 안 auraIn) · 빛 (무기 색 — 무기 끝 너머 glowTo ~ 안 glowIn) · 심지 (무기 끝 ~ from) · 룬 (마검 — 안쪽 가는 줄).
     * 몫 f 의 자리 = 손잡이 + (무기 끝 - 손잡이) × f. local = 지금 (동작 시작부터 틱), o = 그리는 기준 (세계는 카메라 — 카메라 가까이는 옅어짐).
     * 겹마다 같은 사각형 (가장 넓은 겹의 바깥 ~ 안 — 깊이가 똑같아 겹끼리 깜빡이지 않음) 에 그림 자리 (v) 만 달리 붙임: 그 겹의 바깥 = v 0 · 좁힌 안 = v 1,
     * 그 밖은 그림 가장자리 (투명한 맨 끝 줄) 로 늘어남 (clamp).
     * screen (내 1인칭): 무기가 카메라를 축으로 돌아 실제 띠는 옆으로 보여 (선 하나) — 무기 끝이 지나간 화면 길을 바깥 가장자리로 두고 화면에서 그 길에 직각
     * (길 방향 × 카메라 → 끝 쪽 시선) 으로 무기 길이 × fpWidth (최대 fpMaxWidth 칸) 만큼 넓힌 띠 (사진처럼 보이는 초승달). side = 주 손 (왼손 -1 — 거울 동작)
     */
    static void build(Mesh mesh, Trail tr, double local, double ox, double oy, double oz, boolean screen, float side) {
        List<Sample> pts = new ArrayList<>(tr.samples);
        if (tr.head != null) pts.add(tr.head);
        if (pts.size() < 2) return;
        Cfg c = cfg();
        Spec s = tr.spec;
        float from = s.from();
        float g = c.glowTo;
        // 겹: {바깥, 안} (손잡이 → 무기 끝 몫)
        float[] aura = {g + c.auraOut, from + (1 - from) * c.auraIn};
        float[] glow = {g, from + (1 - from) * c.glowIn};
        float[] core = {1F, from};
        float[] rune = {from + (1 - from) * 0.58F, from + (1 - from) * 0.34F};
        float outer = s.aura() != 0 ? Math.max(aura[0], glow[0]) : glow[0];
        float inner = Math.min(core[1], Math.min(glow[1], s.aura() != 0 ? aura[1] : 1F));
        if (s.rune() != 0) inner = Math.min(inner, rune[1]);
        double[][] fr = frames(pts, screen, side, from, c, screen ? capScale(s) : 1);
        float n0 = screen ? c.nearFp0 : c.nearWorld0;
        float n1 = screen ? c.nearFp1 : c.nearWorld1;
        float am = screen ? c.fpAlpha : 1F;
        for (int i = 0; i + 1 < pts.size(); i++) {
            Sample a = pts.get(i);
            Sample b = pts.get(i + 1);
            if (b.start) continue;
            float ka = (float) Mth.clamp((local - a.t) / c.life, 0, 1);
            float kb = (float) Mth.clamp((local - b.t) / c.life, 0, 1);
            if (ka >= 1 && kb >= 1) continue;
            Quad q = new Quad(fr[i], fr[i + 1], outer, inner, ox, oy, oz, n0, n1, screen);
            if (q.hidden()) continue;
            if (s.aura() != 0) band(mesh.aura, q, ka, kb, aura, outer, inner, s.aura(), c.aAura * am, c);
            band(mesh.glow, q, ka, kb, glow, outer, inner, s.glow(), c.aGlow * am, c);
            band(mesh.core, q, ka, kb, core, outer, inner, s.core(), c.aCore * am, c);
            if (s.rune() != 0) band(mesh.rune, q, ka, kb, rune, outer, inner, s.rune(), c.aRune * am, c);
        }
    }

    /**
     * 점마다 띠의 바탕 (몫 f 의 자리 = 바탕 + 방향 × f — {바탕 xyz, 방향 xyz}): 세계 = 손잡이 · 무기 끝 - 손잡이, 1인칭 화면 띠 = 끝 + 옆 × 너비 · -옆 × 너비.
     * 옆 = 화면에서 끝이 가는 길에 직각 (길 × 끝 쪽 시선 — 왼손은 거울). 구간마다 끝이 가장 빨리 움직이는 점의 옆을 기준으로 앞 · 뒤로 이어 가며 같은 쪽을
     * 유지 (휘두름 끝에서 끝이 거의 멈추거나 조금 되돌아와도 띠가 뒤집히지 않게 — 그런 점 (가장 빠른 점의 10% 아래) 은 이웃의 옆)
     */
    static double[][] frames(List<Sample> pts, boolean screen, float side, float from, Cfg c, double capK) {
        int n = pts.size();
        double[][] fr = new double[n][];
        if (!screen) {
            for (int i = 0; i < n; i++) {
                Sample p = pts.get(i);
                fr[i] = new double[] {p.gx, p.gy, p.gz, p.tx - p.gx, p.ty - p.gy, p.tz - p.gz};
            }
            return fr;
        }
        double[][] raw = new double[n][];
        double[] speed = new double[n];
        for (int i = 0; i < n; i++) {
            Sample p = pts.get(i);
            // 같은 구간 안의 앞 · 뒤 점으로 끝이 가는 방향
            int i0 = p.start ? i : Math.max(0, i - 1);
            int i1 = i + 1 < n && !pts.get(i + 1).start ? i + 1 : i;
            Sample s0 = pts.get(i0);
            Sample s1 = pts.get(i1);
            double tx = s1.tx - s0.tx;
            double ty = s1.ty - s0.ty;
            double tz = s1.tz - s0.tz;
            double dt = Math.max(1e-4, s1.t - s0.t);
            speed[i] = Math.sqrt(tx * tx + ty * ty + tz * tz) / dt;
            double rl = Math.sqrt(p.tx * p.tx + p.ty * p.ty + p.tz * p.tz);
            if (rl < 1e-6) continue;
            double rx = p.tx / rl;
            double ry = p.ty / rl;
            double rz = p.tz / rl;
            double nx = (ty * rz - tz * ry) * side;
            double ny = (tz * rx - tx * rz) * side;
            double nz = (tx * ry - ty * rx) * side;
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl > 1e-7) raw[i] = new double[] {nx / nl, ny / nl, nz / nl};
        }
        double[][] nv = new double[n][];
        int ws = 0;
        for (int i = 1; i <= n; i++) {
            if (i < n && !pts.get(i).start) continue;
            // 구간 [ws, i): 가장 빠른 점에서 앞 · 뒤로
            int k = -1;
            for (int j = ws; j < i; j++) if (raw[j] != null && (k < 0 || speed[j] > speed[k])) k = j;
            if (k < 0) {
                for (int j = ws; j < i; j++) nv[j] = new double[] {0, -1, 0};
            } else {
                double lim = speed[k] * 0.1;
                nv[k] = raw[k];
                for (int j = k + 1; j < i; j++) nv[j] = follow(raw[j], speed[j] < lim, nv[j - 1]);
                for (int j = k - 1; j >= ws; j--) nv[j] = follow(raw[j], speed[j] < lim, nv[j + 1]);
            }
            ws = i;
        }
        for (int i = 0; i < n; i++) {
            Sample p = pts.get(i);
            double dx = p.tx - p.gx;
            double dy = p.ty - p.gy;
            double dz = p.tz - p.gz;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double w = Math.min(len * c.fpWidth, c.fpMaxWidth * capK / Math.max(0.05, 1 - from));
            double[] v = nv[i];
            // f = 1 → 끝 · f = from → 끝 + 옆 × (1 - from) × w
            fr[i] = new double[] {p.tx + v[0] * w, p.ty + v[1] * w, p.tz + v[2] * w, -v[0] * w, -v[1] * w, -v[2] * w};
        }
        return fr;
    }

    /** 0.41.0 1인칭 띠 너비 상한 배율: 무기 끝을 늘인 만큼 (무기 길이 + 늘임) ÷ 무기 길이 (늘임이 없으면 1) */
    static double capScale(Spec s) {
        if (!(s.extFp() > 0)) return 1;
        WeaponBlades.Blade b = s.blade();
        double dx = b.fpTipX() - b.fpGripX();
        double dy = b.fpTipY() - b.fpGripY();
        double dz = b.fpTipZ() - b.fpGripZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return len < 1e-6 ? 1 : (len + s.extFp()) / len;
    }

    /** 이웃 (prev) 과 같은 쪽의 옆: 못 구했거나 느린 점이면 이웃 그대로, 반대쪽이면 뒤집음 */
    private static double[] follow(double[] raw, boolean slow, double[] prev) {
        if (raw == null || slow) return prev;
        if (raw[0] * prev[0] + raw[1] * prev[1] + raw[2] * prev[2] < 0) return new double[] {-raw[0], -raw[1], -raw[2]};
        return raw;
    }

    /**
     * 띠 한 칸의 네 귀퉁이 (그리는 기준 자리): a 바깥 · b 바깥 · b 안 · a 안 + 귀퉁이마다 옅어짐 (0 ~ 1 — 카메라 가까이, 1인칭은 시선에서 60° 넘게 벗어난 쪽도:
     * 몸 둘레를 도는 궤적이 화면 가장자리 · 아래를 덮지 않게)
     */
    record Quad(float[] p, float[] fade) {
        Quad(double[] a, double[] b, float outer, float inner, double ox, double oy, double oz, float n0, float n1, boolean screen) {
            this(new float[12], new float[4]);
            double[][] fr = {a, b, b, a};
            float[] fs = {outer, outer, inner, inner};
            for (int i = 0; i < 4; i++) {
                double[] q = fr[i];
                float x = (float) (q[0] + q[3] * fs[i] - ox);
                float y = (float) (q[1] + q[4] * fs[i] - oy);
                float z = (float) (q[2] + q[5] * fs[i] - oz);
                p[i * 3] = x;
                p[i * 3 + 1] = y;
                p[i * 3 + 2] = z;
                float d = (float) Math.sqrt(x * x + y * y + z * z);
                float k = Mth.clamp((d - n0) / (n1 - n0), 0, 1);
                float f = k * k * (3 - 2 * k);
                if (screen && d > 1e-4F) {
                    float e = Mth.clamp((-z / d - 0.17F) / (0.5F - 0.17F), 0, 1); // 시선과의 cos — 80° 에서 0 · 60° 에서 1
                    f *= e * e * (3 - 2 * e);
                }
                fade[i] = f;
            }
        }

        /** 네 귀퉁이 모두 카메라에 붙어 보이지 않음 */
        boolean hidden() {
            return fade[0] <= 0.004F && fade[1] <= 0.004F && fade[2] <= 0.004F && fade[3] <= 0.004F;
        }
    }

    /**
     * 겹 하나 (layer = {바깥, 안} 몫): 같은 사각형 q (바깥 outer · 안 inner) 에 그 겹의 그림 자리 — 몫 f 의 v = (f - 바깥) / ((안 - 바깥) × 좁힘),
     * 나이로 옅어짐 · 귀퉁이마다 카메라 가까이 옅어짐. 앞뒤 두 면 (같은 대각선 — 비틀린 사각형도 앞뒤가 같은 두 삼각형)
     */
    private static void band(Layer l, Quad q, float ka, float kb, float[] layer, float outer, float inner, int rgb, float alpha, Cfg c) {
        float aa = alpha * (float) Math.pow(1 - ka, 1.3);
        float ab = alpha * (float) Math.pow(1 - kb, 1.3);
        if (aa <= 0.004F && ab <= 0.004F) return;
        float wa = c.taper + (1 - c.taper) * (float) Math.pow(1 - ka, 0.6);
        float wb = c.taper + (1 - c.taper) * (float) Math.pow(1 - kb, 0.6);
        float span = layer[1] - layer[0];
        float voA = (outer - layer[0]) / (span * wa);
        float viA = (inner - layer[0]) / (span * wa);
        float voB = (outer - layer[0]) / (span * wb);
        float viB = (inner - layer[0]) / (span * wb);
        float[] p = q.p();
        float[] f = q.fade();
        int c0 = argb(aa * f[0], rgb);
        int c1 = argb(ab * f[1], rgb);
        int c2 = argb(ab * f[2], rgb);
        int c3 = argb(aa * f[3], rgb);
        // 앞면 (a 바깥 → b 바깥 → b 안 → a 안 — 삼각형 0 1 2 · 2 3 0)
        l.add(p[0], p[1], p[2], ka, voA, c0);
        l.add(p[3], p[4], p[5], kb, voB, c1);
        l.add(p[6], p[7], p[8], kb, viB, c2);
        l.add(p[9], p[10], p[11], ka, viA, c3);
        // 뒷면 (b 안 → b 바깥 → a 바깥 → a 안 — 삼각형 2 1 0 · 0 3 2: 같은 대각선 a 바깥 ~ b 안)
        l.add(p[6], p[7], p[8], kb, viB, c2);
        l.add(p[3], p[4], p[5], kb, voB, c1);
        l.add(p[0], p[1], p[2], ka, voA, c0);
        l.add(p[9], p[10], p[11], ka, viA, c3);
    }

    private static int argb(float a, int rgb) {
        int al = Math.round(Mth.clamp(a, 0, 1) * 255);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    // ------------------------------------------------------------ 시험 (사진첩 — 렌더가 실제로 그린 무기 자리와 BladeTrails 의 계산을 비교)

    /** 0.41.0 시험 · 사진첩: 멈춘 자세 (ClientMotions.freeze) 의 궤적에 입힐 평타 강화 모습 (null = 없음) */
    public static MotionNet.Look frozenLook;
    /** 0.41.0 시험: 마지막으로 시작한 모습 실린 궤적의 빛 색 · 무기 끝 늘임 (세계 · 1인칭 — 모습이 없던 궤적은 적지 않음) */
    public static int lastLookGlow;
    public static float lastLookExt;
    public static float lastLookExtFp;

    private static void noteSpec(Spec spec, MotionNet.Look look) {
        if (look == null || look.none()) return;
        lastLookGlow = spec.glow();
        lastLookExt = spec.ext();
        lastLookExtFp = spec.extFp();
    }

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
