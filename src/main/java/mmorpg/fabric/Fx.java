package mmorpg.fabric;

import java.util.List;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
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
 * 0.12.8: 영웅 · 초월 활 · 완드는 무기 디자인 색 (rules_more LOOK → SLASH 의 glow · core — 등급 색 대신): 화살 빛줄기에 흰 심지 한 줄,
 * 마법진 · 번쩍임은 그 색, 완드 궤적은 바닐라 파티클 대신 그 색의 빛줄기 (활의 지나간 자리 선 조각) + 0.4칸마다 떠오르는 빛 방울,
 * 맞은 자리는 그 색의 번쩍임 + 튀는 불꽃
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

    private static double[] rgbOf(Obj o, String key) {
        List<Object> c = o.list(key);
        return new double[] {((Number) c.get(0)).doubleValue(), ((Number) c.get(1)).doubleValue(), ((Number) c.get(2)).doubleValue()};
    }

    /**
     * 원거리 평타 색 {빛, 심지 (없으면 null)} (0.12.8): 영웅 · 초월 무기는 무기 모양 (LOOK → SLASH) 의 glow · core — 무기 디자인 색,
     * 그 밖에는 등급 색 (cfg = BOW · WAND 의 color)
     */
    static double[][] shotColors(Obj cfg, Weapon w) {
        if (lookColors != null) return lookColors; // 0.41.0 평타 강화 모습
        Obj s = w.look() == null ? null : Weapons.slashStyle(w.look());
        if (s == null || !s.has("glow")) return new double[][] {gradeRgb(cfg, w.gradeIndex()), null};
        return new double[][] {rgbOf(s, "glow"), s.has("core") ? rgbOf(s, "core") : null};
    }

    /** 0.41.0 평타 강화 모습 (평타 강화 룬이 걸린 활 · 완드 평타 — SwingLook.shotColors): 이 값이 있는 동안 shotColors 대신 이 색 {빛, 심지} */
    private static double[][] lookColors;

    /** 0.41.0 이제부터 그리는 활 · 완드 평타의 색을 cs 로 (null = 무기 · 등급 색) — 돌려준 앞 값으로 {@link #popLook} (try/finally). 서버 스레드 하나라 정적 값으로 충분 */
    static double[][] pushLook(double[][] cs) {
        double[][] prev = lookColors;
        lookColors = cs;
        return prev;
    }

    static void popLook(double[][] prev) {
        lookColors = prev;
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
    static void arrowFx(ServerLevel level, RpgCombat.Path path, RpgCombat.Shot shot, Weapon w) {
        double[][] cs = shotColors(BOW, w);
        double[] c = cs[0];
        Vec3 d = path.dir();
        float thick = (float) (SHOT_THICK * (shot.half() / SHOT_HALF));
        fx(level, path.from().add(d.scale(shot.half())), new FxOptions(FxOptions.ARROW_SHOT, -1, d, (float) shot.speed(), (float) shot.half(), thick,
                (float) shot.life(), 0F, 0F, 0F, 1F, argb(c, 1)));
        // 영웅 · 초월: 빛줄기 가운데 흰 심지 한 줄 (뒤에 보냄 — 위에 그려짐)
        if (cs[1] != null) fx(level, path.from().add(d.scale(shot.half())), new FxOptions(FxOptions.ARROW_SHOT, -1, d, (float) shot.speed(),
                (float) (shot.half() * 0.9), thick * 0.38F, (float) shot.life(), 0F, 0F, 0F, 1F, argb(cs[1], 1)));
        for (double s = SEG_GAP / 2; s <= path.length() - 0.1; s += SEG_GAP) {
            double delay = shot.speed() > 0 ? Math.max(0, (s - shot.half()) / shot.speed()) : 0;
            fx(level, path.from().add(d.scale(s)), new FxOptions(FxOptions.ARROW_TRAIL, -1, d, 0F, (float) SEG_HALF, (float) SEG_THICK,
                    (float) (delay + SEG_FADE), (float) delay, 0F, 0F, 1F, argb(c, 0.9)));
        }
    }

    /** 맞은 자리: 커지며 사라지는 번쩍임 + 쏜 쪽으로 튀는 불꽃 8개 */
    static void arrowImpact(ServerLevel level, Vec3 at, Vec3 dir, Weapon w) {
        double[][] cs = shotColors(BOW, w);
        double[] c = cs[0];
        fx(level, at, new FxOptions(FxOptions.ARROW_SPARK, -1, dir, 0F, 0.45F, 0F, 0.15F, 0F, 1.6F, 0F, 1F, argb(c, 0.95)));
        if (cs[1] != null) fx(level, at, new FxOptions(FxOptions.ARROW_SPARK, -1, dir, 0F, 0.22F, 0F, 0.12F, 0F, 1.4F, 0F, 1F, argb(cs[1], 1)));
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
    static void magicCircle(ServerLevel level, Vec3 at, Weapon w, double spinDeg, double progress) {
        Obj circle = WAND.obj("circle");
        fx(level, at, new FxOptions(FxOptions.MAGIC_CIRCLE, -1, Vec3.ZERO, 0F, (float) circleSize(w.gradeIndex()), 0F, 0.1F, 0F, 0F, (float) spinDeg,
                (float) progress, argb(shotColors(WAND, w)[0], circle.num("alpha"))));
    }

    /** 다 그려진 마법진이 1.35배로 커지며 사라짐 */
    static void circleFlash(ServerLevel level, Vec3 at, Weapon w, double spinDeg) {
        Obj circle = WAND.obj("circle");
        double[][] cs = shotColors(WAND, w);
        fx(level, at, new FxOptions(FxOptions.MAGIC_FLASH, -1, Vec3.ZERO, 0F, (float) circleSize(w.gradeIndex()), 0F, (float) circle.num("flashLife"), 0F, 0F,
                (float) spinDeg, 1F, argb(cs[0], 1)));
        // 영웅 · 초월: 가운데 심지 빛 (작게 번쩍)
        if (cs[1] != null) fx(level, at, new FxOptions(FxOptions.ARROW_SPARK, -1, Vec3.ZERO, 0F, (float) (circleSize(w.gradeIndex()) * 0.55), 0F, 0.2F, 0F, 2.2F,
                0F, 1F, argb(cs[1], 0.9)));
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

    static void wandBeam(ServerLevel level, Vec3 from, Vec3 to, Weapon w) {
        double[][] cs = shotColors(WAND, w);
        Vec3 d = to.subtract(from);
        double len = d.length();
        Vec3 u = len > 1e-6 ? d.scale(1 / len) : Vec3.ZERO;
        if (cs[1] == null) {
            ParticleOptions p = wandTrail(w.gradeIndex());
            for (double s = 0.8; s <= len; s += 0.4) {
                Vec3 at = from.add(u.scale(s));
                level.sendParticles(p, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            }
            return;
        }
        // 영웅 · 초월 (0.12.8): 무기 색 빛줄기 — 활의 지나간 자리와 같은 선 조각이 한꺼번에 켜져 가늘어지며 사라짐 (옆에서 보이는 줄기) +
        // 바닐라 궤적 자리 (0.4칸마다) 에 빛 방울 (보는 쪽을 향함 — 1인칭 앞으로 곧게 뻗어도 보임), 셋에 하나는 심지 색 · 조금씩 위로 떠오름
        Vec3[] b = basis(u);
        for (double s = 0.6; s <= len - 0.1; s += SEG_GAP) {
            fx(level, from.add(u.scale(s)), new FxOptions(FxOptions.ARROW_TRAIL, -1, u, 0F, (float) SEG_HALF, 0.08F, 0.45F, 0F, 0F, 0F, 1F, argb(cs[0], 0.85)));
        }
        int i = 0;
        for (double s = 0.8; s <= len; s += 0.4, i++) {
            Vec3 drift = b[1].scale(0.8).add(b[0].scale(i % 2 == 0 ? 0.5 : -0.5)).normalize();
            fx(level, from.add(u.scale(s)), new FxOptions(FxOptions.ARROW_SPARK, -1, drift, 0.5F, 0.15F, 0F, 0.6F, 0F, 0.3F, 0F, 1F,
                    argb(i % 3 == 2 ? cs[1] : cs[0], 0.95)));
        }
    }

    static void wandImpact(ServerLevel level, Vec3 at, Weapon w) {
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.3);
        double[][] cs = shotColors(WAND, w);
        if (cs[1] == null) {
            level.sendParticles(wandTrail(w.gradeIndex()), at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.02);
            return;
        }
        // 영웅 · 초월: 무기 색 번쩍임 (빛 + 심지) + 사방으로 튀는 불꽃 6개 (빛 · 심지 색 번갈아)
        fx(level, at, new FxOptions(FxOptions.ARROW_SPARK, -1, Vec3.ZERO, 0F, 0.45F, 0F, 0.18F, 0F, 1.6F, 0F, 1F, argb(cs[0], 0.95)));
        fx(level, at, new FxOptions(FxOptions.ARROW_SPARK, -1, Vec3.ZERO, 0F, 0.22F, 0F, 0.14F, 0F, 1.4F, 0F, 1F, argb(cs[1], 1)));
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3;
            Vec3 dir = new Vec3(Math.cos(a), k % 2 == 0 ? 0.6 : -0.2, Math.sin(a)).normalize();
            fx(level, at, new FxOptions(FxOptions.ARROW_SPARK, -1, dir, 4F, 0.1F, 0F, 0.28F, 0F, 0F, 0F, 1F, argb(k % 2 == 0 ? cs[0] : cs[1], 0.9)));
        }
    }

    /** dir 에 수직인 두 단위 벡터 (오른쪽 · 위) */
    static Vec3[] basis(Vec3 dir) {
        Vec3 r = dir.cross(new Vec3(0, 1, 0));
        Vec3 right = r.length() < 1e-3 ? new Vec3(1, 0, 0) : r.normalize();
        Vec3 up = right.cross(dir).normalize();
        return new Vec3[] {right, up};
    }
}
