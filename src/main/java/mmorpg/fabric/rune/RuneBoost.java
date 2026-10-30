package mmorpg.fabric.rune;

import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * 0.48.2 초월 룬 2D 강화 (사용자 "초월 31종 먼저" → 입체 · 블록 · 2D 세 판을 견준 뒤 "하나로 통일하고 싶은데 걍 2d로 하고 2d 이펙트를 더 강화하는 느낌으로" →
 * "2D 만 강화"): 초월 룬의 2D 연출은 그대로 두고 그 위에 2D 그림만 더함 (입체 모델 · 블록 표시 없음).
 * <ul>
 *   <li>후광 (aura · rfx2a): 핵심 그림 (검 · 달 · 창 · 눈 · 블랙홀 · 날개) 마다 같은 그림의 빛깔 겹을 1.35배 크기 · 옅게 한 장 더 — 같은 길로 움직임</li>
 *   <li>터짐 (impact): 큰 번쩍임 · 고리 둘 (빠르게 크게 → 3틱 뒤 흰 고리) · 충격파 셋 · 땅에 퍼지는 빛살 열여섯 · 불티 · 위로 튀는 빛 알갱이 · 더 센 흔들림</li>
 *   <li>부드러운 터짐 (bloom): 버프 · 회복 룬 — 흔들림 · 충격파 없이 번쩍임 · 고리 · 빛 알갱이만</li>
 * </ul>
 */
final class RuneBoost {
    private RuneBoost() {}

    /** 후광: rfx 그림 name 의 빛깔 겹 (첫 칸 + o 의 cell) 을 k 배 크기 · alpha 로 (o 의 자리 · 방향 · 움직임 · 삶 그대로) */
    static void aura(ServerLevel level, String name, Vec3 at, Opt o, double[] glow, double k, double alpha) {
        int base = RuneKit.RFX.getOrDefault(name, new int[] {0, 1})[0];
        rfx(level, name, at, o, glow, base, k, alpha);
    }

    /** 후광 (빛깔 칸을 직접 — 칸 묶음이 따로인 그림: 창 · 번개) */
    static void auraCell(ServerLevel level, String name, Vec3 at, Opt o, double[] glow, int glowCell, double k, double alpha) {
        rfx(level, name, at, o, glow, glowCell, k, alpha);
    }

    private static void rfx(ServerLevel level, String name, Vec3 at, Opt o, double[] glow, int cell0, double k, double alpha) {
        RuneKit.rfx(level, name, at, o.with("size", o.num("size", 1) * k, "color", glow, "cell", (double) (cell0 + (int) o.num("cell", 0)), "alpha",
                alpha * o.num("alpha", 1), "solo", null));
    }

    /** rfx2 (빛깔 + 심) 에 후광을 더함 */
    static void rfx2a(ServerLevel level, String name, Vec3 at, Opt o, double[] glow, double[] core) {
        aura(level, name, at, o, glow, 1.35, 0.42);
        RuneKit.rfx2(level, name, at, o, glow, core);
    }

    /** 터짐 (반지름 r — 룬의 영역 크기): 번쩍임 · 고리 둘 · 충격파 셋 · 땅 빛살 열여섯 · 불티 · 빛 알갱이 · 흔들림 */
    static void impact(ServerLevel level, Vec3 at, double[] glow, double[] core, double r) {
        Vec3 mid = V.up(at, 1.0);
        RuneKit.skyFlare(level, V.up(at, 1.3), r * 1.05, glow, core, Opt.of("life", 0.5, "grow", 0.8));
        RuneKit.novaRing(level, at, r * 0.3, r * 0.95, glow, core, Opt.of("life", 0.45));
        RuneKit.later(3, () -> RuneKit.novaRing(level, at, r * 0.2, r * 0.6, core, RuneKit.WHITE, Opt.of("life", 0.7)));
        for (int i = 0; i < 3; i++) {
            final int ii = i;
            RuneKit.later(i * 2, () -> SkillFx.shock(level, at, ii % 2 == 0 ? glow : core, r * (0.6 + 0.35 * ii), 0.5 + 0.1 * ii));
        }
        for (int i = 0; i < 16; i++) {
            Vec3 d = RuneKit.turn(new Vec3(1, 0, 0), i * 22.5 + Kit.rand() * 8);
            SkillFx.fx(level, "rpg:light_cut", V.add(V.up(at, 0.1), d, r * 0.55), Opt.of("dir", d, "size", r * 0.5, "thick", 0.35, "life", 0.6, "color",
                    i % 2 == 0 ? glow : core, "alpha", 0.95));
        }
        SkillFx.sparks(level, mid, core, 24, 10);
        motes(level, at, glow, r, 10);
        RuneKit.shakeAround(level, at, 24, 0.6, 0.5);
    }

    /** 부드러운 터짐 (버프 · 회복): 번쩍임 · 고리 둘 · 빛 알갱이 — 흔들림 · 충격파 없음 */
    static void bloom(ServerLevel level, Vec3 at, double[] glow, double[] core, double r) {
        RuneKit.skyFlare(level, V.up(at, 1.3), r * 0.8, glow, core, Opt.of("life", 0.45, "grow", 0.6));
        RuneKit.novaRing(level, at, r * 0.25, r * 0.8, glow, core, Opt.of("life", 0.5));
        RuneKit.later(3, () -> RuneKit.novaRing(level, at, r * 0.15, r * 0.5, core, RuneKit.WHITE, Opt.of("life", 0.7)));
        motes(level, at, glow, r, 14);
    }

    /** 위로 튀는 빛 알갱이 n 개 (반지름 r 안 땅에서) */
    static void motes(ServerLevel level, Vec3 at, double[] color, double r, int n) {
        for (int i = 0; i < n; i++) {
            double a = Kit.rand() * Math.PI * 2;
            double rr = Math.sqrt(Kit.rand()) * r * 0.7;
            Vec3 p = new Vec3(at.x + Math.cos(a) * rr, at.y + 0.2, at.z + Math.sin(a) * rr);
            SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.UP, "speed", 3.5 + Kit.rand() * 4, "size", 0.28, "grow", 1.0, "life", 0.9, "color", color,
                    "alpha", 1.0));
        }
    }
}
