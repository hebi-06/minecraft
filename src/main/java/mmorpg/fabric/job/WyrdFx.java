package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.BlockFx;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 2026-11-16 위르드식 시범 (사용자 "위르드 식으로 스킬 아무거나 하나 만들어봐" · "하나만 만들어봐" — 그림 메모리 (8 GB) 때문에 칠한 2D 그림 대신 견줄 방식):
 * 아크메이지 스타 볼텍스의 연출만 바꿈 (판정 · 피해 · 끌어당김 · 처형은 그대로 — ArcmageSkills.vortex).
 * <ul>
 *   <li>한 색 (남보라 VIOLET) + 흰 심 (BlockFx 상자의 뜨거운 겹) · 발광 외곽선 (리소스 팩 후처리가 부드러운 빛 번짐으로) — 높은 대비</li>
 *   <li>광역은 예고 → 볼륨 두 단계: 쓰는 즉시 땅에 예고 고리 둘 (바깥 = 판정 반지름 · 안) → formAt 에 소용돌이 몸 (팔 셋 × 상자 여섯이 돌며 안쪽으로 감김 · 가운데 기둥)</li>
 *   <li>맞은 적 번쩍임 (흰 외곽선 몇 틱 — 화면 흔들림 없음) · 별빛은 기둥 꼭대기에서 적에게 꽂히는 빛 줄 · 무너질 때 한 점으로 조여 들었다 폭발 · 퍼지는 고리 · 조각</li>
 *   <li>그림 (글꼴) 을 쓰지 않음 — 상자 모델 (rpg:vfx/fx_bar 등, 작은 그림) 을 물들이고 늘여 만듦 → 메모리가 거의 늘지 않음</li>
 * </ul>
 */
final class WyrdFx {
    private WyrdFx() {}

    /** 시범 켬 (끄면 칠한 그림 — 예전 연출) */
    static boolean VORTEX = true;

    /** 남보라 (한 색) · 발광 외곽선 색 */
    static final double[] VIOLET = {0.47, 0.38, 1.0};
    static final double[] DEEP = {0.28, 0.2, 0.86};
    static final int GLOW = 0x8F7CFF;

    // ------------------------------------------------------------ 맞은 적 번쩍임

    /** 번쩍임이 끝나는 틱 (겹친 번쩍임이 일찍 꺼지지 않게) */
    private static final Map<UUID, Long> FLASH = new HashMap<>();

    /** 맞은 적 번쩍임: ticks 동안 흰 외곽선 (팀이 없는 몹은 흰색 — 후처리가 빛 번짐으로). 원래 빛나던 몹은 그대로 */
    static void flash(LivingEntity e, int ticks) {
        if (e == null || e.isRemoved()) return;
        long until = Tasks.now() + ticks;
        Long prev = FLASH.get(e.getUUID());
        if (prev == null && e.hasGlowingTag()) return; // 원래 빛나던 몹 (보스 표시 등)
        FLASH.put(e.getUUID(), Math.max(until, prev == null ? 0 : prev));
        e.setGlowingTag(true);
        RuneKit.later(ticks, () -> {
            Long u = FLASH.get(e.getUUID());
            if (u == null || Tasks.now() < u) return;
            FLASH.remove(e.getUUID());
            if (!e.isRemoved()) e.setGlowingTag(false);
        });
    }

    // ------------------------------------------------------------ 스타 볼텍스

    /** 쓸 때: 완드 끝 → 가운데 빛 줄 · 땅의 예고 고리 둘 (바깥 = 판정 반지름 · 안 0.5 — hold 틱 머묾) · 가운데 번쩍 별 */
    static void vortexCast(ServerLevel level, Vec3 tip, Vec3 c, double R, int hold) {
        BlockFx.beam(level, tip, V.up(c, 0.5), 0.12, VIOLET, 7, GLOW);
        BlockFx.ringHold(level, V.up(c, 0.04), R, 40, 0.07, VIOLET, 4, hold);
        BlockFx.ringHold(level, V.up(c, 0.05), R * 0.5, 26, 0.05, DEEP, 7, Math.max(1, hold - 3));
        BlockFx.star(level, V.up(c, 0.6), 1.2, VIOLET);
    }

    /**
     * 소용돌이 몸 (formAt 부터 life 틱): 팔 셋 × 상자 여섯 — 바깥에서 안쪽으로 감긴 자리 (안쪽일수록 빨리 돎, 2틱마다 다음 자리로 보간) · 가운데 기둥 (돌며 맥박) ·
     * 끝 squeeze 틱 동안 모두 한 점으로 조여 들며 작아짐. 반환 = 효과 (기둥 맥박은 {@link #vortexBeat})
     */
    static Vfx.Fx vortexBody(ServerLevel level, Vec3 c, double R, int life, int squeeze, double spin0, double rate) {
        Vfx.Fx fx = RuneProps.fx(level, c, life + 6);
        int arms = 3;
        int per = 6;
        Vfx.Prop[][] cube = new Vfx.Prop[arms][per];
        double[][] f = new double[arms][per];
        for (int a = 0; a < arms; a++) {
            for (int k = 0; k < per; k++) {
                f[a][k] = 0.95 - 0.13 * k;
                double[] col = k % 2 == 0 ? VIOLET : DEEP;
                Vec3 p = spiral(c, R, f[a][k], a, k, 0, 1, spin0, rate);
                Vfx.Prop b = BlockFx.box(fx, p, spiralRot(c, p), 0.02, 0.02, 0.02, col);
                b.outline(GLOW);
                cube[a][k] = b;
                double len = R * 0.34 * f[a][k] + 0.18;
                double w = 0.1 + 0.08 * f[a][k];
                fx.on(1 + k, () -> b.stretch(w, len, w * 0.8, 4));
            }
        }
        // 가운데 기둥 (세운 상자 — 흰 심 겹) · 바닥 원반 (눕힌 납작한 상자 둘 — 엇갈려 돎)
        Vfx.Prop pillar = BlockFx.box(fx, V.up(c, 1.3), new Quaternionf(), 0.02, 0.02, 0.02, VIOLET);
        pillar.outline(GLOW);
        fx.on(2, () -> pillar.stretch(0.32, 2.6, 0.32, 5));
        Vfx.Prop disc1 = BlockFx.box(fx, V.up(c, 0.08), flat(spin0), 0.02, 0.02, 0.02, DEEP);
        Vfx.Prop disc2 = BlockFx.box(fx, V.up(c, 0.09), flat(spin0 + 45), 0.02, 0.02, 0.02, VIOLET);
        fx.on(3, () -> {
            disc1.stretch(1.1, 1.1, 0.06, 4);
            disc2.stretch(0.8, 0.8, 0.07, 4);
        });
        int squeezeAt = Math.max(8, life - squeeze);
        fx.each(2, life, 2, age -> {
            boolean sq = age >= squeezeAt;
            double shrink = sq ? Math.max(0, 1 - (age + 2 - squeezeAt) / (double) squeeze) : 1;
            for (int a = 0; a < arms; a++) {
                for (int k = 0; k < per; k++) {
                    Vec3 p = spiral(c, R, f[a][k], a, k, age + 2, shrink, spin0, sq ? rate * 2.5 : rate);
                    Vfx.Prop b = cube[a][k];
                    b.to(p, spiralRot(c, p), -1, 2);
                    if (sq) {
                        double len = (R * 0.34 * f[a][k] + 0.18) * Math.max(0.15, shrink);
                        double w = (0.1 + 0.08 * f[a][k]) * Math.max(0.3, shrink);
                        b.stretch(w, len, w * 0.8, 2);
                    }
                }
            }
            double ang = spin0 + rate * 1.6 * (age + 2) / 20.0;
            pillar.turn(new Quaternionf().rotateY((float) Math.toRadians(ang)), 2);
            disc1.turn(flat(ang), 2);
            disc2.turn(flat(-ang * 1.3 + 45), 2);
            if (sq) {
                double s = Math.max(0.05, shrink);
                pillar.stretch(0.32 * s + 0.08, 2.6 * s + 0.3, 0.32 * s + 0.08, 2);
                disc1.stretch(1.1 * s, 1.1 * s, 0.06, 2);
                disc2.stretch(0.8 * s, 0.8 * s, 0.07, 2);
            }
        });
        fx.on(life + 1, fx::end);
        return fx;
    }

    /** 별빛 한 번의 땅 맥박: 안쪽에서 판정 반지름 가까이로 퍼지는 고리 */
    static void vortexBeat(ServerLevel level, Vec3 c, double R) {
        BlockFx.ring(level, V.up(c, 0.06), R * 0.25, R * 0.9, 30, 0.06, VIOLET, 7);
    }

    /** 별빛 한 번 (한 적): 기둥 꼭대기 → 적의 가슴으로 빛 줄 · 번쩍 별 · 조각 넷 · 흰 번쩍임 */
    static void vortexHit(ServerLevel level, Vec3 top, LivingEntity e) {
        Vec3 to = Kit.bodyPoint(e);
        BlockFx.beam(level, top, to, 0.14, VIOLET, 5, GLOW);
        BlockFx.star(level, to, 0.9, VIOLET);
        BlockFx.shards(level, to, 4, 0.9, 0.14, VIOLET, 7);
        flash(e, 3);
    }

    /** 무너짐 (한 점): 폭발 (흰 심) · 바깥으로 퍼지는 고리 둘 · 조각 열넷 · 큰 번쩍 별 */
    static void vortexCollapse(ServerLevel level, Vec3 c, double R) {
        Vec3 m = V.up(c, 0.9);
        BlockFx.burst(level, m, R * 0.6, VIOLET, BlockFx.WHITE, 11);
        BlockFx.ring(level, V.up(c, 0.06), 0.4, R * 1.2, 40, 0.1, VIOLET, 9);
        BlockFx.ring(level, V.up(c, 0.07), 0.3, R * 0.75, 28, 0.07, DEEP, 7);
        BlockFx.shards(level, m, 14, R * 0.95, 0.2, VIOLET, 11);
        BlockFx.star(level, m, 2.2, VIOLET);
    }

    /** 끌려 들어감 (처형): 적 → 가운데 빛 줄 · 번쩍임 */
    static void vortexDrain(ServerLevel level, LivingEntity e, Vec3 m) {
        BlockFx.beam(level, Kit.bodyPoint(e), m, 0.16, VIOLET, 6, GLOW);
        flash(e, 4);
    }

    // ------------------------------------------------------------ 자리 계산

    /** 팔 a 의 k 번째 상자 자리 (나이 age 틱 · 몸 크기 몫 shrink): 안쪽일수록 빨리 돌고 조금 높음 */
    private static Vec3 spiral(Vec3 c, double R, double f, int a, int k, int age, double shrink, double spin0, double rate) {
        double omega = rate * (1 + 1.3 * (1 - f)); // 도/초
        double ang = Math.toRadians(spin0 + a * 120 + k * 34 + omega * age / 20.0);
        double r = R * f * shrink;
        double y = 0.14 + 0.09 * k * shrink + 0.05 * Math.sin(age * 0.35 + a * 2.1 + k);
        return new Vec3(c.x + Math.cos(ang) * r, c.y + y, c.z + Math.sin(ang) * r);
    }

    /** 상자 방향: 긴 축 (모델 y) = 도는 쪽 (접선) · x = 바깥 · z = 아래 (오른손 — BlockFx.ring 과 같음) */
    private static Quaternionf spiralRot(Vec3 c, Vec3 p) {
        Vec3 radial = V.flat(V.sub(p, c));
        if (V.len(radial) < 1e-4) radial = new Vec3(1, 0, 0);
        radial = V.unit(radial);
        Vec3 tan = new Vec3(-radial.z, 0, radial.x);
        return Vfx.basis(radial, tan, V.DOWN);
    }

    /** 눕힌 판 (모델 z = 위 — x · y 가 땅에 평행) · 각 deg 만큼 돈 것 */
    private static Quaternionf flat(double deg) {
        return new Quaternionf().rotateY((float) Math.toRadians(deg)).rotateX((float) (-Math.PI / 2));
    }
}
