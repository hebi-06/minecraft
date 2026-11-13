package mmorpg.fabric.rune;

import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-14 궁수 계열 다시 만들기 (사용자가 고른 컨셉 — 폭풍 궁수 · 천벌의 사수 · 그림자 사수): 직업 스킬이 쓰는 룬의 그림 — 모드 없는 클라이언트에도 보이는 것만
 * (룬 터짐 {@link RuneBoost} · 3D 빛 화살 {@link RuneBow}). 화면 흔들림은 없음 (사용자 "멀미나" — 룬의 초월 터짐은 흔들지만 여기서는 뺌)
 */
public final class BowFx {
    private BowFx() {}

    /** 아래 등급 룬의 터짐 (common · rare · noble — 흔들림 없음) */
    public static void impact(ServerLevel level, Vec3 at, double[] glow, double[] core, double r, String grade) {
        String g = "hero".equals(grade) || "transcend".equals(grade) ? "noble" : grade;
        RuneBoost.impact(level, at, glow, core, r, g);
    }

    /** 초월 룬의 큰 터짐 (번쩍임 · 고리 둘 · 충격파 셋 · 땅 빛살 열여섯 · 불티 · 빛 알갱이) — 흔들림만 뺌 */
    public static void bigImpact(ServerLevel level, Vec3 at, double[] glow, double[] core, double r) {
        Vec3 mid = V.up(at, 1.0);
        RuneKit.skyFlare(level, V.up(at, 1.3), r * 1.05, glow, core, RuneBoost.nearOwner(level, V.up(at, 1.3), r * 1.05).with("life", 0.5, "grow", 0.8));
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
        RuneBoost.motes(level, at, glow, r, 10);
    }

    /** 부드러운 터짐 (번쩍임 · 고리 둘 · 빛 알갱이 — 흔들림 · 충격파 없음) */
    public static void bloom(ServerLevel level, Vec3 at, double[] glow, double[] core, double r) {
        RuneBoost.bloom(level, at, glow, core, r);
    }

    /** 위로 튀는 빛 알갱이 n 개 (반지름 r 안 땅에서) */
    public static void motes(ServerLevel level, Vec3 at, double[] color, double r, int n) {
        RuneBoost.motes(level, at, color, r, n);
    }

    /** 하늘에서 떨어져 꽂히는 3D 빛 화살 (촉이 땅 g 위 sky 칸 → 땅속 0.6칸, drop 틱 · stuck 틱 꽂혀 있다가 가라앉음 — 천궁과 같음) */
    public static void fallArrow(ServerLevel level, Vec3 g, double len, double sky, int drop, int stuck, double[] glow, double[] core) {
        RuneBow.fallArrow(level, g, len, sky, drop, stuck, glow, core);
    }
}
