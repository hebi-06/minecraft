package mmorpg.fabric;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Combat;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 근접 무기 참격 연출 (slash.js 의 자바 판 — 같은 그림 · 크기 · 자리 · 색 · 소리, 수치는 rules.json 의 SLASH).
 * - 공격 동작 (허공 포함) 마다 눈앞에 참격이 나가며 사라짐: 칼 초승달 (좌우 번갈아) · 단검 가는 베기 + 반대 방향 한 번 더 (X자) ·
 *   도끼 두꺼운 내려찍기 · 마검 장밋빛 무거운 베기 + 1틱 뒤 하늘빛 룬 베기 · 방패 푸른 강철빛 고리 · 창 찌르기 빛줄기 (SLASH.spear — 애드온의 궁니르.
 *   자바 판은 도끼 초월이 세계수의 도끼 (0.12.2 반고의 도끼) 라 다른 도끼처럼 내려찍기, 궁니르는 창 초월로 옮겨 하늘빛 찌르기 SLASH.spear 를 씀 — rules_more LOOK)
 *   (12단계 새 무기: 창 금빛 찌르기 빛줄기 (초월 궁니르는 하늘빛) · 낫 넓은 보랏빛 초승달 + 양옆 잔상 · 전투 망치 무거운 내려찍기 · 성스러운 홀 금빛 고리 + 빛 십자)
 * - 맞혔을 때: 도끼 · 전투 망치 발밑 충격파, 방패 · 창 대상 앞 폭발 + 고리, 홀 금빛 십자 섬광, 치명타는 칼 금빛 초승달 · 단검 붉은 X · 마검 보랏빛 X + 장밋빛 초승달,
 *   화면 가운데에서 벗어난 대상을 맞혔으면 맞은 자리에도 작은 참격
 * - 영웅 · 초월은 참격 안쪽에 금빛 · 붉은빛 기운, 요구 조건이 모자라면 회색으로 작게 (피해 없음)
 * - 도끼 · 방패로 맞히면 화면이 살짝 흔들림 (애드온 camerashake — 모드가 있는 클라이언트에 RpgNet.shake)
 * - 연출만 (피해는 평타 판정 그대로)
 */
public final class Slash {
    private Slash() {}

    private static final Obj SLASH = Rules.get().obj("SLASH");
    private static final double[] GREY = {0.55, 0.55, 0.55};
    private static final double[] WHITE = {1, 1, 1};
    /** 겹 (기운 → 빛 → 심지) 을 보는 방향으로 조금씩 앞뒤로 — 흰 심지가 늘 위에 */
    private static final double LAYER_GAP = 0.03;
    /** 번갈아 베는 방향 */
    private static final Map<UUID, Boolean> SIDE = new HashMap<>();
    /** 마지막 참격 틱 (한 번 누름에 공격 패킷 + 휘두르기 패킷이 같이 오므로 같은 틱은 한 번만) */
    private static final Map<UUID, Long> LAST = new HashMap<>();

    static void forget(UUID id) {
        SIDE.remove(id);
        LAST.remove(id);
    }

    /** 휘두르기 패킷 (허공 · 블록 · 몹을 누를 때마다): 블록을 캐는 동작은 빼고 참격 */
    public static void onPunch(ServerPlayer sp) {
        if (sp.pick(sp.blockInteractionRange(), 1F, false) instanceof net.minecraft.world.phys.BlockHitResult b
                && b.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        once(sp);
    }

    /** 몹을 침 (Player#attack 처음): 참격 (요구 조건이 모자라 공격이 막혀도 회색으로) */
    public static void onAttack(ServerPlayer sp) {
        once(sp);
    }

    private static void once(ServerPlayer sp) {
        if (mmorpg.fabric.mount.Riding.blocked(sp, "attack")) return; // 탈것에 탄 동안 참격 없음 (피해도 막힘)
        long now = Tasks.now();
        Long last = LAST.get(sp.getUUID());
        if (last != null && last == now) return;
        LAST.put(sp.getUUID(), now);
        swing(sp);
    }

    // ------------------------------------------------------------ 도움

    private static double[] rgb(List<?> c) {
        return new double[] {((Number) c.get(0)).doubleValue(), ((Number) c.get(1)).doubleValue(), ((Number) c.get(2)).doubleValue()};
    }

    private static double[] rgb(Obj style, String key) {
        return rgb(style.list(key));
    }

    private static double[] whiten(double[] c, double t) {
        return new double[] {c[0] + (1 - c[0]) * t, c[1] + (1 - c[1]) * t, c[2] + (1 - c[2]) * t};
    }

    private static double gradeSize(Weapon w) {
        return Fx.gradeSize(w.gradeIndex());
    }

    /** 등급 기운 색 (영웅 · 초월만) */
    private static double[] aura(Weapon w) {
        List<Object> a = SLASH.list("gradeAura");
        Object c = w.gradeIndex() >= 0 && w.gradeIndex() < a.size() ? a.get(w.gradeIndex()) : null;
        return c instanceof List<?> l ? rgb(l) : null;
    }

    static Obj styleOf(Weapon w) {
        return SLASH.objOrNull(w.look() != null ? w.look() : w.type());
    }

    private static double jitter(Obj style, RandomSource r) {
        return (r.nextDouble() * 2 - 1) * style.numOr("jitter", 0);
    }

    private static Vec3 along(Vec3 p, Vec3 dir, double d) {
        return p.add(dir.scale(d));
    }

    private static void emit(ServerLevel level, FxSprite s, Vec3 at, Vec3 dir, double speed, double spin, double size, double life, double[] c,
            double alpha) {
        Fx.fx(level, at, FxOptions.ofSprite(s, dir, speed, size, life, spin, FxOptions.argb(c[0], c[1], c[2], alpha)));
    }

    private static FxSprite sprite(String shape, boolean flip, String layer) {
        return FxSprite.valueOf(("SLASH_" + shape + "_" + (flip ? "l" : "r") + "_" + layer).toUpperCase(java.util.Locale.ROOT));
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent s, double pitch, double volume) {
        Fx.sound(level, at, s, pitch, volume);
    }

    private static Vec3 eye(ServerPlayer sp) {
        return sp.getEyePosition();
    }

    // ------------------------------------------------------------ 참격 한 개 (slashAt)

    /**
     * 참격 한 개: (등급 기운) + 무기 색 빛 + 흰 심지. flip=false 는 오른쪽 위 → 왼쪽 아래 (방향 · 기울기는 그림에 들어 있음)
     * @param dist 보는 사람 눈에서 at 까지 거리 (겹을 앞뒤로 옮겨도 눈에는 딱 겹쳐 보이게 크기를 맞춤)
     */
    static void slashAt(ServerLevel level, Vec3 at, Vec3 dir, double dist, Weapon w, Obj style, boolean flip, boolean ok, double scale, double speed,
            RandomSource rnd) {
        double size = style.num("size") * (ok ? gradeSize(w) : 0.7) * scale;
        String shape = style.str("shape");
        double spin = jitter(style, rnd);
        double life = style.num("life");
        double[] glow = ok ? rgb(style, "glow") : GREY;
        double[] aura = ok ? aura(w) : null;
        if (aura != null) {
            double k = 1;
            emit(level, sprite(shape, flip, "aura"), along(at, dir, k * LAYER_GAP), dir, speed, spin, size * (dist + k * LAYER_GAP) / dist, life, aura, 0.8);
        }
        emit(level, sprite(shape, flip, "glow"), at, dir, speed, spin, size, life, glow, ok ? 0.95 : 0.5);
        double k = -1;
        emit(level, sprite(shape, flip, "core"), along(at, dir, k * LAYER_GAP), dir, speed, spin, size * (dist + k * LAYER_GAP) / dist, life,
                ok ? whiten(glow, 0.8) : GREY, ok ? 1 : 0.5);
    }

    /** 공격 동작의 참격: 눈앞 가운데 (눈높이보다 SLASH.drop 아래) */
    private static void slashOnce(ServerPlayer sp, Weapon w, Obj style, boolean flip, boolean ok, double scale) {
        Vec3 dir = sp.getViewVector(1F);
        Vec3 e = eye(sp);
        Vec3 at = along(new Vec3(e.x, e.y - SLASH.numOr("drop", 0), e.z), dir, style.num("dist"));
        slashAt(sp.level(), at, dir, style.num("dist"), w, style, flip, ok, scale, style.num("speed"), sp.getRandom());
    }

    // ------------------------------------------------------------ 공격 동작 (slashSwing)

    /** 공격 동작 (허공 포함): 손에 든 근접 무기의 참격. ok = 요구 조건 (레벨 · 요구 스탯) 을 채웠는지 */
    public static void swing(ServerPlayer sp) {
        if (!SLASH.truthy("enabled")) return;
        Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
        if (w == null || !Combat.isMeleeWeapon(w)) return;
        Obj style = styleOf(w);
        if (style == null) return;
        boolean ok = RpgCombat.lackOf(sp, w).isEmpty();
        if (style.truthy("bash")) {
            bashSwing(sp, w, style, ok);
            if (ok && style.truthy("holy")) holySwing(sp, w, style);
            return;
        }
        if (style.truthy("thrust")) {
            thrustSwing(sp, w, style, ok);
            return;
        }
        boolean flip = !SIDE.getOrDefault(sp.getUUID(), false);
        SIDE.put(sp.getUUID(), flip);
        slashOnce(sp, w, style, flip, ok, 1);
        ServerLevel level = sp.level();
        Vec3 e = eye(sp);
        SoundEvent miss = SoundEvents.SPEAR_ATTACK.value();
        switch (w.type()) {
            case "dagger" -> {
                sound(level, e, miss, 1.7, 0.45);
                // 반대 방향으로 한 번 더 베어 X자
                Tasks.later((int) style.numOr("second", 2), () -> {
                    if (sp.isRemoved()) return;
                    slashOnce(sp, w, style, !flip, ok, 0.9);
                    sound(sp.level(), eye(sp), miss, 1.95, 0.35);
                });
            }
            case "axe" -> sound(level, e, miss, 0.6, 0.8);
            case "hammer" -> {
                sound(level, e, miss, 0.5, 0.85);
                sound(level, e, SoundEvents.MACE_SMASH_AIR, 0.8, 0.5);
            }
            case "scythe" -> {
                sound(level, e, miss, 0.75, 0.7);
                // 넓게 베기: 같은 방향의 작은 초승달 둘이 양옆으로 (휘두른 폭이 보이게 — 낫 기본 공격은 앞 120° 의 다른 적도 벰)
                if (ok && style.truthy("wide")) Tasks.later(1, () -> {
                    if (!sp.isRemoved()) wideSwing(sp, w, style, flip);
                });
            }
            case "greatsword" -> {
                sound(level, e, miss, 0.85, 0.75);
                if (ok && style.has("rune")) Tasks.later(1, () -> {
                    if (!sp.isRemoved()) runeSlash(sp, w, style, flip);
                });
            }
            default -> sound(level, e, miss, 1.15, 0.6);
        }
    }

    /** 마검의 안쪽 룬 베기: 같은 방향의 가는 하늘빛 베기 (빛 + 흰 심지), 조금 작고 가깝게 */
    private static void runeSlash(ServerPlayer sp, Weapon w, Obj style, boolean flip) {
        Vec3 dir = sp.getViewVector(1F);
        Vec3 e = eye(sp);
        double dist = style.num("dist") - 0.15;
        Vec3 at = along(new Vec3(e.x, e.y - SLASH.numOr("drop", 0), e.z), dir, dist);
        double size = style.num("size") * 0.78 * gradeSize(w);
        double spin = jitter(style, sp.getRandom());
        double life = style.num("life") * 0.85;
        double[] rune = rgb(style, "rune");
        emit(sp.level(), sprite("thin", flip, "glow"), at, dir, style.num("speed"), spin, size, life, rune, 0.9);
        emit(sp.level(), sprite("thin", flip, "core"), along(at, dir, -LAYER_GAP), dir, style.num("speed"), spin, size * (dist - LAYER_GAP) / dist, life,
                whiten(rune, 0.75), 1);
    }

    /** 낫 넓게 베기 (12단계): 가운데 참격 1틱 뒤, 같은 방향의 작은 초승달 둘이 양옆 (±35°) 으로 — 휘두른 폭 */
    private static void wideSwing(ServerPlayer sp, Weapon w, Obj style, boolean flip) {
        Vec3 view = sp.getViewVector(1F);
        Vec3 e = eye(sp);
        double dist = style.num("dist") + 0.2;
        for (int side : new int[] {-1, 1}) {
            Vec3 dir = mmorpg.fabric.skill.V.turnY(view, side * 35);
            Vec3 at = along(new Vec3(e.x, e.y - SLASH.numOr("drop", 0), e.z), dir, dist);
            slashAt(sp.level(), at, dir, dist, w, style, flip, true, 0.62, style.num("speed"), sp.getRandom());
        }
    }

    /** 성스러운 홀 (12단계): 금빛 고리 안에 빛 십자가 번쩍 (rpg:rfx_flare 별빛 — 1인칭 화면을 덮지 않게 작게) */
    private static void holySwing(ServerPlayer sp, Weapon w, Obj style) {
        Vec3 dir = sp.getViewVector(1F);
        Vec3 e = eye(sp);
        Vec3 at = along(new Vec3(e.x, e.y - SLASH.numOr("drop", 0), e.z), dir, style.num("dist") + 0.05);
        double[] glow = rgb(style, "glow");
        mmorpg.fabric.rune.RuneKit.rfx2(sp.level(), "flare", at, mmorpg.fabric.skill.Opt.of("size", 0.32 * gradeSize(w), "life", 0.22, "fade", 0.6, "spin", 45.0),
                glow, WHITE, new int[] {0, 1}, null);
    }

    /** 성스러운 홀로 맞힘 (12단계): 대상 앞 금빛 십자 별빛 + 발밑에서 솟는 빛줄기 (치명타는 크고 하얗게) */
    private static void holyHit(ServerLevel level, ServerPlayer attacker, LivingEntity victim, Vec3 mid, Weapon w, boolean crit, Obj style) {
        Vec3 to = mid.subtract(eye(attacker));
        double len = Math.max(1e-6, to.length());
        Vec3 u = to.scale(1 / len);
        Vec3 at = along(mid, u, -Math.min(0.45, len * 0.5));
        double[] glow = crit ? rgb(style, "critColor") : rgb(style, "glow");
        double size = (crit ? 0.95 : 0.7) * gradeSize(w);
        mmorpg.fabric.rune.RuneKit.rfx2(level, "flare", at, mmorpg.fabric.skill.Opt.of("size", size, "life", crit ? 0.34 : 0.26, "fade", 0.6, "spin", 45.0, "grow", 0.3),
                glow, WHITE, new int[] {0, 1}, null);
        Vec3 feet = victim.position();
        mmorpg.fabric.rune.RuneKit.rfx(level, "rays", feet, mmorpg.fabric.skill.Opt.of("size", crit ? 2.4 : 1.7, "life", 0.4, "fade", 0.6, "color", glow, "alpha", 0.8,
                "cell", 0.0));
        sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, crit ? 0.8 : 1.3, crit ? 1.0 : 0.6);
    }

    /** 방패 치기: 눈앞에서 퍼지며 사라지는 고리 (영웅 · 초월은 바깥에 등급 빛 고리) */
    private static void bashSwing(ServerPlayer sp, Weapon w, Obj style, boolean ok) {
        ServerLevel level = sp.level();
        Vec3 dir = sp.getViewVector(1F);
        Vec3 e = eye(sp);
        double dist = style.num("dist");
        Vec3 at = along(new Vec3(e.x, e.y - SLASH.numOr("drop", 0), e.z), dir, dist);
        double size = style.num("size") * (ok ? gradeSize(w) : 0.7);
        double[] aura = ok ? aura(w) : null;
        if (aura != null) {
            double k = dist + LAYER_GAP;
            emit(level, FxSprite.BASH_RING, along(at, dir, LAYER_GAP), dir, style.num("speed"), 0, size * 1.18 * k / dist, style.num("life") * 1.1, aura, 0.75);
        }
        emit(level, FxSprite.BASH_RING, at, dir, style.num("speed"), 0, size, style.num("life"), ok ? rgb(style, "glow") : GREY, ok ? 0.9 : 0.5);
        sound(level, e, SoundEvents.SPEAR_ATTACK.value(), 0.7, 0.6);
    }

    /** 창 찌르기 (SLASH 의 thrust — 애드온 궁니르 · 12단계 창): 눈앞으로 곧게 뻗는 빛줄기 (색 빛 + 흰 심지, 초월 기운) — 활 빛줄기 그림 */
    private static void thrustSwing(ServerPlayer sp, Weapon w, Obj style, boolean ok) {
        ServerLevel level = sp.level();
        Vec3 dir = sp.getViewVector(1F);
        Vec3 e = eye(sp);
        Vec3 at = along(new Vec3(e.x, e.y - SLASH.numOr("drop", 0) - 0.1, e.z), dir, style.num("dist") + style.num("length"));
        double grade = ok ? gradeSize(w) : 0.7;
        double[] glow = ok ? rgb(style, "glow") : GREY;
        double[] aura = ok ? aura(w) : null;
        if (aura != null) shot(level, at, dir, style, grade, aura, style.num("thick") * 2.2, 0.7);
        shot(level, at, dir, style, grade, glow, style.num("thick") * 1.4, 0.95);
        shot(level, at, dir, style, grade, ok ? rgb(style, "core") : GREY, style.num("thick") * 0.5, 1);
        sound(level, e, SoundEvents.TRIDENT_THROW.value(), 1.3, 0.45);
    }

    private static void shot(ServerLevel level, Vec3 at, Vec3 dir, Obj style, double grade, double[] c, double thick, double alpha) {
        Fx.fx(level, at, new FxOptions(FxOptions.ARROW_SHOT, -1, dir, (float) style.num("speed"), (float) (style.num("length") * grade), (float) (thick * grade),
                (float) style.num("life"), 0F, 0F, 0F, 1F, FxOptions.argb(c[0], c[1], c[2], alpha)));
    }

    // ------------------------------------------------------------ 맞혔을 때 (slashHit)

    /**
     * 피해가 실제로 들어간 근접 공격의 연출 (DamageNumbers 가 부름).
     * @param crit 치명타였는지
     */
    public static void hit(ServerPlayer attacker, LivingEntity victim, Weapon w, boolean crit) {
        if (!SLASH.truthy("enabled") || !Combat.isMeleeWeapon(w) || victim.isRemoved()) return;
        if (!(victim.level() instanceof ServerLevel level)) return;
        Obj style = styleOf(w);
        Vec3 head = victim.getEyePosition();
        Vec3 mid = new Vec3(head.x, head.y - 0.45, head.z);
        Vec3 dir = attacker.getViewVector(1F);
        Vec3 e = eye(attacker);
        RandomSource rnd = attacker.getRandom();
        if (style != null && style.truthy("bash")) {
            bashHit(level, attacker, mid, w, crit, style, rnd);
            if (style.truthy("holy")) holyHit(level, attacker, victim, mid, w, crit, style);
            if (crit) sound(level, mid, SoundEvents.PLAYER_ATTACK_CRIT, 1.0, 0.7);
            return;
        }
        if (style != null && style.truthy("thrust")) {
            thrustHit(level, attacker, mid, w, crit, style, rnd);
            if (crit) sound(level, mid, SoundEvents.PLAYER_ATTACK_CRIT, 1.0, 0.7);
            return;
        }
        // 화면 가운데에서 벗어난 대상을 맞혔으면 맞은 자리에도 참격 (가운데 참격과 같은 방향)
        if (style != null && offAxisDegrees(e, dir, victim) > SLASH.numOr("offAxis", 12)) {
            Vec3 to = mid.subtract(e);
            double len = to.length();
            if (len > 0.6) {
                Vec3 u = to.scale(1 / len);
                double d = Math.max(0.6, len - 0.4);
                slashAt(level, along(e, u, d), u, d, w, style, SIDE.getOrDefault(attacker.getUUID(), false), true, 0.7, 0, rnd);
            }
        }
        if (w.type().equals("axe")) {
            Vec3 feet = victim.position();
            Obj axe = SLASH.obj("axe");
            emit(level, FxSprite.SHOCK_RING, new Vec3(feet.x, feet.y + 0.08, feet.z), Vec3.ZERO, 0, 0, crit ? 1.6 : 1.0, crit ? 0.5 : 0.35, rgb(axe, "glow"), 0.9);
            sound(level, feet, SoundEvents.MACE_SMASH_GROUND, crit ? 0.9 : 1.2, crit ? 0.9 : 0.5);
            if (SLASH.truthy("cameraShake")) RpgNet.shake(attacker, crit ? 0.35 : 0.12, crit ? 0.2 : 0.1);
        }
        if (w.type().equals("hammer") && style != null) {
            // 전투 망치: 발밑 충격파 둘 (안쪽 금빛 · 바깥 흰빛) + 흙먼지, 화면이 크게 흔들림 (내려찍기 — 둘레의 다른 적도 맞음: WeaponTraits)
            Vec3 feet = victim.position();
            double r = Math.max(1, w.typeDef().objOrNull("quake") != null ? w.typeDef().obj("quake").num("radius") : 2);
            double[] glow = rgb(style, "glow");
            emit(level, FxSprite.SHOCK_RING, new Vec3(feet.x, feet.y + 0.08, feet.z), Vec3.ZERO, 0, 0, r * (crit ? 1.0 : 0.8), crit ? 0.5 : 0.38, glow, 0.95);
            emit(level, FxSprite.SHOCK_RING, new Vec3(feet.x, feet.y + 0.1, feet.z), Vec3.ZERO, 0, 0, r * (crit ? 1.35 : 1.1), crit ? 0.42 : 0.3, WHITE, 0.6);
            mmorpg.fabric.skill.SkillFx.fx(level, "rpg:dust", new Vec3(feet.x, feet.y + 0.3, feet.z), mmorpg.fabric.skill.Opt.of("size", r * 0.8, "life", 0.6, "color",
                    new double[] {0.62, 0.52, 0.4}, "alpha", 0.75));
            sound(level, feet, SoundEvents.MACE_SMASH_GROUND, crit ? 0.7 : 0.9, crit ? 1.0 : 0.7);
            if (SLASH.truthy("cameraShake")) RpgNet.shake(attacker, crit ? 0.4 : 0.18, crit ? 0.22 : 0.12);
        }
        if (w.type().equals("greatsword") && style != null && style.has("rune")) {
            // 마검: 맞힌 자리에 칼날 룬의 하늘빛 불꽃
            double[] rune = rgb(style, "rune");
            Fx.fx(level, along(mid, dir, -0.2), new FxOptions(FxOptions.ARROW_SPARK, -1, dir, 0F, 0.32F, 0F, 0.18F, 0F, 0F, 0F, 1F,
                    FxOptions.argb(rune[0], rune[1], rune[2], 1)));
        }
        if (!crit) return;
        Vec3 front = along(mid, dir, -LAYER_GAP);
        switch (w.type()) {
            case "dagger" -> {
                emit(level, FxSprite.SLASH_X, mid, dir, 0, 0, 0.8, 0.28, new double[] {1.0, 0.35, 0.3}, 1);
                emit(level, FxSprite.SLASH_X, front, dir, 0, 0, 0.55, 0.22, new double[] {1.0, 0.95, 0.8}, 1);
            }
            case "sword" -> {
                double spin = (rnd.nextDouble() * 2 - 1) * 40;
                boolean l = rnd.nextBoolean();
                emit(level, sprite("arc", l, "glow"), mid, dir, 0, spin, 1.1, 0.25, new double[] {1.0, 0.85, 0.35}, 0.95);
                emit(level, sprite("arc", l, "core"), front, dir, 0, spin, 1.1, 0.25, new double[] {1.0, 1.0, 0.9}, 1);
            }
            case "scythe" -> {
                // 낫 치명타: 보랏빛 큰 초승달 둘이 X 로 (거둬들이는 낫질)
                double spin = (rnd.nextDouble() * 2 - 1) * 25;
                double[] glow = style != null ? rgb(style, "glow") : new double[] {0.66, 0.5, 1};
                emit(level, sprite("arc", false, "glow"), mid, dir, 0, spin, 1.25, 0.28, glow, 0.95);
                emit(level, sprite("arc", true, "glow"), along(mid, dir, -LAYER_GAP), dir, 0, -spin, 1.25, 0.28, glow, 0.95);
                emit(level, sprite("arc", false, "core"), front, dir, 0, spin, 1.24, 0.26, new double[] {0.95, 0.9, 1}, 1);
            }
            case "greatsword" -> {
                double spin = (rnd.nextDouble() * 2 - 1) * 30;
                boolean l = rnd.nextBoolean();
                emit(level, FxSprite.SLASH_X, mid, dir, 0, 0, 0.95, 0.28, new double[] {0.62, 0.36, 0.9}, 1);
                double[] glow = style != null ? rgb(style, "glow") : new double[] {0.96, 0.52, 0.5};
                emit(level, sprite("heavy", l, "glow"), along(mid, dir, -LAYER_GAP), dir, 0, spin, 1.2, 0.26, glow, 0.95);
                emit(level, sprite("heavy", l, "core"), along(front, dir, -LAYER_GAP), dir, 0, spin, 1.19, 0.26, new double[] {1.0, 0.92, 0.88}, 1);
            }
            default -> {}
        }
        sound(level, mid, SoundEvents.PLAYER_ATTACK_CRIT, 1.0, 0.7);
    }

    /** 방패 치기 맞힘: 대상 몸 앞 (공격자 쪽) 에 가시 달린 충격 폭발 + 흰 고리 + 방패 소리, 치명타는 금빛으로 크고 길게 */
    private static void bashHit(ServerLevel level, ServerPlayer attacker, Vec3 mid, Weapon w, boolean crit, Obj style, RandomSource rnd) {
        Vec3 to = mid.subtract(eye(attacker));
        double len = Math.max(1e-6, to.length());
        Vec3 u = to.scale(1 / len);
        Vec3 at = along(mid, u, -Math.min(0.4, len * 0.5));
        double size = style.num("hitSize") * gradeSize(w) * (crit ? 1.35 : 1);
        double life = style.num("hitLife") * (crit ? 1.25 : 1);
        emit(level, FxSprite.BASH_BURST, at, u, 0, jitter(style, rnd), size, life, crit ? rgb(style, "critColor") : whiten(rgb(style, "glow"), 0.35), 0.95);
        emit(level, FxSprite.BASH_RING, along(at, u, -LAYER_GAP), u, 0, 0, size * 0.55, life * 0.8, WHITE, 0.9);
        sound(level, at, SoundEvents.SHIELD_BLOCK.value(), crit ? 0.7 : 0.85, 0.9);
        if (SLASH.truthy("cameraShake")) RpgNet.shake(attacker, crit ? 0.2 : 0.08, crit ? 0.15 : 0.08);
    }

    /** 창으로 맞혔을 때: 대상 앞에 얼음빛 섬광 + 흰 고리 (치명타는 금빛 · 크게) */
    private static void thrustHit(ServerLevel level, ServerPlayer attacker, Vec3 mid, Weapon w, boolean crit, Obj style, RandomSource rnd) {
        Vec3 to = mid.subtract(eye(attacker));
        double len = Math.max(1e-6, to.length());
        Vec3 u = to.scale(1 / len);
        Vec3 at = along(mid, u, -Math.min(0.35, len * 0.5));
        double size = style.num("hitSize") * gradeSize(w) * (crit ? 1.4 : 1);
        emit(level, FxSprite.BASH_BURST, at, u, 0, rnd.nextDouble() * 360, size, crit ? 0.3 : 0.22, crit ? new double[] {1.0, 0.85, 0.35} : rgb(style, "glow"),
                0.95);
        emit(level, FxSprite.BASH_RING, along(at, u, -LAYER_GAP), u, 0, 0, size * 0.6, 0.2, WHITE, 0.9);
        sound(level, at, SoundEvents.TRIDENT_HIT, crit ? 0.8 : 1.1, 0.8);
    }

    /** 보는 방향에서 대상이 얼마나 벗어났는지 (도): 발 ~ 머리 사이에서 가장 가까운 곳, 몸 폭만큼 빼줌 */
    static double offAxisDegrees(Vec3 eye, Vec3 dir, Entity victim) {
        Vec3 feet = victim.position();
        Vec3 head = victim.getEyePosition();
        double best = -1;
        double dist = 1;
        for (int i = 0; i <= 4; i++) {
            double s = i / 4.0;
            Vec3 p = new Vec3(feet.x + (head.x - feet.x) * s, feet.y + 0.1 + (head.y - feet.y - 0.1) * s, feet.z + (head.z - feet.z) * s);
            Vec3 v = p.subtract(eye);
            double len = v.length();
            if (len < 1e-9) len = 1;
            double dot = v.dot(dir) / len;
            if (dot > best) {
                best = dot;
                dist = len;
            }
        }
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, best))));
        return angle - Math.toDegrees(Math.atan(0.35 / Math.max(0.5, dist)));
    }
}
