package mmorpg.fabric;

import java.util.List;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 연출 (fx.js · combat.js 의 파티클 · 소리). 활 빛줄기 · 마법진 · 맞힘은 애드온 그림 그대로 (mmorpg:fx 파티클 — 클라이언트가 그림),
 * 완드 궤적 · 치명타 불꽃 등 애드온도 바닐라 파티클을 쓰는 것은 자바의 같은 파티클.
 */
public final class Fx {
    private Fx() {}

    private static final Obj BOW = Rules.get().obj("BOW");
    private static final Obj WAND = Rules.get().obj("WAND");
    private static final Obj SLASH = Rules.get().obj("SLASH");

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, double pitch, double volume) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, (float) volume, (float) pitch);
    }

    public static void particle(ServerLevel level, ParticleOptions p, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(p, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    /** 등급 크기 배율 (SLASH.gradeSize) */
    static double gradeSize(int gradeIndex) {
        List<Double> s = SLASH.numbersOrNull("gradeSize");
        return s != null && gradeIndex >= 0 && gradeIndex < s.size() ? s.get(gradeIndex) : 1;
    }

    /** 치명타 (fx.js critFx): 머리에 치명타 불꽃 + 강한 공격 소리 + 모루 소리 (작게) */
    public static void crit(Entity victim) {
        if (!(victim.level() instanceof ServerLevel level)) return;
        Vec3 at = victim.getEyePosition();
        particle(level, ParticleTypes.CRIT, at, 14, 0.25, 0.35);
        sound(level, at, SoundEvents.PLAYER_ATTACK_STRONG, 1.1, 1);
        sound(level, at, SoundEvents.ANVIL_LAND, 2.0, 0.25);
    }

    /** 방패 막기 (fx.js blockFx): 바라보는 쪽 앞에 불꽃 + 방패 소리 */
    public static void block(ServerPlayer sp) {
        Vec3 d = sp.getViewVector(1F);
        Vec3 h = sp.getEyePosition();
        Vec3 at = new Vec3(h.x + d.x * 0.8, h.y - 0.4, h.z + d.z * 0.8);
        particle(sp.level(), ParticleTypes.CRIT, at, 10, 0.15, 0.3);
        sound(sp.level(), at, SoundEvents.SHIELD_BLOCK.value(), 1, 1);
    }

    /** 스탯을 찍었을 때 (statui.js): 토템 반짝이 */
    public static void statUp(ServerPlayer sp) {
        Vec3 h = sp.getEyePosition();
        particle(sp.level(), ParticleTypes.TOTEM_OF_UNDYING, new Vec3(h.x, h.y - 0.6, h.z), 16, 0.3, 0.25);
    }

    // ------------------------------------------------------------ 이펙트 그림 (mmorpg:fx — 애드온 그림 그대로, 클라이언트가 그림)

    /** 등급 색 (config [[r,g,b], …] 0~1) */
    static double[] gradeRgb(Obj cfg, int gradeIndex) {
        List<Object> colors = cfg.list("color");
        Object c = gradeIndex >= 0 && gradeIndex < colors.size() ? colors.get(gradeIndex) : colors.get(0);
        List<?> rgb = (List<?>) c;
        return new double[] {((Number) rgb.get(0)).doubleValue(), ((Number) rgb.get(1)).doubleValue(), ((Number) rgb.get(2)).doubleValue()};
    }

    static int argb(double[] rgb, double alpha) {
        return FxOptions.argb(rgb[0], rgb[1], rgb[2], alpha);
    }

    /** 이펙트 하나를 그 자리 둘레 (늘 보이게) 에 보냄 */
    public static void fx(ServerLevel level, Vec3 at, FxOptions o) {
        level.sendParticles(o, true, true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------ 활 (combat.js arrowFx · arrowImpactFx)

    /** 빛줄기 모양 (combat.js SHOT): 반 길이 · 반 두께 · 지나간 자리 선 조각 간격 · 반 길이 · 반 두께 · 사라지는 시간 */
    static final double SHOT_HALF = 1.1;
    static final double SHOT_THICK = 0.2;
    static final double SEG_GAP = 0.5;
    static final double SEG_HALF = 0.3;
    static final double SEG_THICK = 0.08;
    static final double SEG_FADE = 0.4;

    /**
     * 화살 연출 (쏘는 순간 한 번에): 날아가는 빛줄기 + 빛줄기 가운데가 지나가는 순간 켜져 가늘어지며 사라지는 선 조각.
     * 빛줄기 앞끝이 수명이 끝날 때 조준점에 닿음 (RpgCombat.arrowShot).
     */
    static void arrowFx(ServerLevel level, RpgCombat.Path path, RpgCombat.Shot shot, int gradeIndex) {
        double[] c = gradeRgb(BOW, gradeIndex);
        Vec3 d = path.dir();
        float thick = (float) (SHOT_THICK * (shot.half() / SHOT_HALF));
        fx(level, path.from().add(d.scale(shot.half())), new FxOptions(FxOptions.ARROW_SHOT, -1, d, (float) shot.speed(), (float) shot.half(), thick,
                (float) shot.life(), 0F, 0F, 0F, 1F, argb(c, 1)));
        for (double s = SEG_GAP / 2; s <= path.length() - 0.1; s += SEG_GAP) {
            double delay = shot.speed() > 0 ? Math.max(0, (s - shot.half()) / shot.speed()) : 0;
            fx(level, path.from().add(d.scale(s)), new FxOptions(FxOptions.ARROW_TRAIL, -1, d, 0F, (float) SEG_HALF, (float) SEG_THICK,
                    (float) (delay + SEG_FADE), (float) delay, 0F, 0F, 1F, argb(c, 0.9)));
        }
    }

    /** 맞은 자리: 커지며 사라지는 번쩍임 + 쏜 쪽으로 튀는 불꽃 8개 */
    static void arrowImpact(ServerLevel level, Vec3 at, Vec3 dir, int gradeIndex) {
        double[] c = gradeRgb(BOW, gradeIndex);
        fx(level, at, new FxOptions(FxOptions.ARROW_SPARK, -1, dir, 0F, 0.45F, 0F, 0.15F, 0F, 1.6F, 0F, 1F, argb(c, 0.95)));
        Vec3 u = dir.cross(Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = u.cross(dir);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            Vec3 sd = dir.scale(-0.7).add(u.scale(Math.cos(a))).add(v.scale(Math.sin(a))).normalize();
            fx(level, at, new FxOptions(FxOptions.ARROW_SPARK, -1, sd, 5F, 0.1F, 0F, 0.25F, 0F, 0F, 0F, 1F, argb(c, 0.9)));
        }
    }

    // ------------------------------------------------------------ 완드 (combat.js startWandCast · fireWand)

    static double circleSize(int gradeIndex) {
        return WAND.obj("circle").num("size") * gradeSize(gradeIndex);
    }

    /** 마법진 한 틱 (0.1초짜리를 틱마다 새로 — 플레이어를 따라감): 보는 사람 쪽을 향하고, progress 만큼 그려진 그림 (10단계) */
    static void magicCircle(ServerLevel level, Vec3 at, int gradeIndex, double spinDeg, double progress) {
        Obj circle = WAND.obj("circle");
        fx(level, at, new FxOptions(FxOptions.MAGIC_CIRCLE, -1, Vec3.ZERO, 0F, (float) circleSize(gradeIndex), 0F, 0.1F, 0F, 0F, (float) spinDeg,
                (float) progress, argb(gradeRgb(WAND, gradeIndex), circle.num("alpha"))));
    }

    /** 다 그려진 마법진이 1.35배로 커지며 사라짐 */
    static void circleFlash(ServerLevel level, Vec3 at, int gradeIndex, double spinDeg) {
        Obj circle = WAND.obj("circle");
        fx(level, at, new FxOptions(FxOptions.MAGIC_FLASH, -1, Vec3.ZERO, 0F, (float) circleSize(gradeIndex), 0F, (float) circle.num("flashLife"), 0F, 0F,
                (float) spinDeg, 1F, argb(gradeRgb(WAND, gradeIndex), 1)));
    }

    /** 완드 궤적 파티클 (WAND.trail — 베드락 이름을 자바 파티클로) */
    static ParticleOptions wandTrail(int gradeIndex) {
        return switch (gradeIndex) {
            case 1 -> ParticleTypes.SOUL_FIRE_FLAME; // blue_flame_particle
            case 2 -> ParticleTypes.WITCH; // dragon_breath_trail (보라)
            case 3 -> ParticleTypes.FLAME; // basic_flame_particle
            case 4 -> DustParticleOptions.REDSTONE; // redstone_wire_dust_particle
            default -> ParticleTypes.END_ROD; // endrod
        };
    }

    static void wandBeam(ServerLevel level, Vec3 from, Vec3 to, int gradeIndex) {
        ParticleOptions p = wandTrail(gradeIndex);
        Vec3 d = to.subtract(from);
        double len = d.length();
        Vec3 u = len > 1e-6 ? d.scale(1 / len) : Vec3.ZERO;
        for (double s = 0.8; s <= len; s += 0.4) {
            Vec3 at = from.add(u.scale(s));
            level.sendParticles(p, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    static void wandImpact(ServerLevel level, Vec3 at, int gradeIndex) {
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.3);
        level.sendParticles(wandTrail(gradeIndex), at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.02);
    }

    /** dir 에 수직인 두 단위 벡터 (오른쪽 · 위) */
    static Vec3[] basis(Vec3 dir) {
        Vec3 r = dir.cross(new Vec3(0, 1, 0));
        Vec3 right = r.length() < 1e-3 ? new Vec3(1, 0, 0) : r.normalize();
        Vec3 up = right.cross(dir).normalize();
        return new Vec3[] {right, up};
    }
}
