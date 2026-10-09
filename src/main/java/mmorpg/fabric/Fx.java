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
 * 연출 (fx.js · combat.js 의 파티클 · 소리). 1단계는 바닐라 파티클로 같은 자리 · 같은 색 · 같은 타이밍을 냄
 * (애드온의 전용 파티클 그림 — 화살 빛줄기 · 마법진 — 은 2단계에서 클라이언트 파티클로 옮김).
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

    /** 등급 색 (config [[r,g,b], …] 0~1, 없으면 첫 색) → 0xRRGGBB */
    static int gradeColor(Obj cfg, int gradeIndex) {
        List<Object> colors = cfg.list("color");
        Object c = gradeIndex >= 0 && gradeIndex < colors.size() ? colors.get(gradeIndex) : colors.get(0);
        List<?> rgb = (List<?>) c;
        int r = channel(rgb.get(0));
        int g = channel(rgb.get(1));
        int b = channel(rgb.get(2));
        return (r << 16) | (g << 8) | b;
    }

    private static int channel(Object v) {
        return Math.max(0, Math.min(255, Math.round(((Number) v).floatValue() * 255)));
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

    // ------------------------------------------------------------ 활 (combat.js arrowFx · arrowImpactFx)

    /** 빛줄기 한 틱 몫: 지나간 자리 (from → to) 에 등급 색 선 + 앞끝 빛 */
    static void arrowSegment(ServerLevel level, Vec3 from, Vec3 to, int gradeIndex, boolean head) {
        DustParticleOptions dust = new DustParticleOptions(gradeColor(BOW, gradeIndex), 0.45F);
        Vec3 d = to.subtract(from);
        double len = d.length();
        int n = Math.max(1, (int) Math.ceil(len / 0.15));
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.add(d.scale(i / (double) n));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        if (head) level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 1, 0, 0, 0, 0);
    }

    /** 맞은 자리: 번쩍임 + 쏜 쪽으로 튀는 불꽃 */
    static void arrowImpact(ServerLevel level, Vec3 at, Vec3 dir, int gradeIndex) {
        DustParticleOptions dust = new DustParticleOptions(gradeColor(BOW, gradeIndex), 0.8F);
        level.sendParticles(dust, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            Vec3 side = new Vec3(Math.cos(a), Math.sin(a) * 0.7, Math.sin(a)).scale(0.6).subtract(dir.scale(0.7)).normalize();
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 0, side.x, side.y, side.z, 0.5);
        }
    }

    // ------------------------------------------------------------ 완드 (combat.js startWandCast · fireWand)

    /** 마법진 한 틱: 바라보는 방향에 수직인 고리 (progress 만큼 그려짐) */
    static void magicCircle(ServerLevel level, Vec3 at, Vec3 dir, int gradeIndex, double spinDeg, double progress) {
        DustParticleOptions dust = new DustParticleOptions(gradeColor(WAND, gradeIndex), 0.28F);
        Vec3[] basis = basis(dir);
        double r = WAND.obj("circle").num("size") * gradeSize(gradeIndex);
        int total = 28;
        int n = Math.max(1, (int) Math.round(total * Math.min(1, progress)));
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(spinDeg) + i * 2 * Math.PI / total;
            Vec3 p = at.add(basis[0].scale(Math.cos(a) * r)).add(basis[1].scale(Math.sin(a) * r));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        // 가운데 별 (안쪽 고리 절반 크기, 반대로 돎)
        for (int i = 0; i < 8 && progress > 0.5; i++) {
            double a = -Math.toRadians(spinDeg) + i * 2 * Math.PI / 8;
            Vec3 p = at.add(basis[0].scale(Math.cos(a) * r * 0.5)).add(basis[1].scale(Math.sin(a) * r * 0.5));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    /** 다 그려진 마법진이 번쩍임 */
    static void circleFlash(ServerLevel level, Vec3 at, Vec3 dir, int gradeIndex) {
        DustParticleOptions dust = new DustParticleOptions(gradeColor(WAND, gradeIndex), 0.45F);
        Vec3[] basis = basis(dir);
        double r = WAND.obj("circle").num("size") * gradeSize(gradeIndex) * 1.3;
        for (int i = 0; i < 32; i++) {
            double a = i * 2 * Math.PI / 32;
            Vec3 p = at.add(basis[0].scale(Math.cos(a) * r)).add(basis[1].scale(Math.sin(a) * r));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 6, 0.08, 0.08, 0.08, 0.05);
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
