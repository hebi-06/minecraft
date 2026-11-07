package mmorpg.fabric.job;

import mmorpg.core.Jobs;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 — 마검사 각성 아케인 블레이드 (2026-11-05 룬 스미스 대신 — 이명에 맞춘 대장장이 컨셉을 버림) 의 특성 (재설계안 2판 — claude/job-advance-redo.md):
 * 「마력 각인」 (쌓고 터지는 것은 마검사와 같음 — JobTraits.mark · markBonus, 최대 5): 각인이 터지면 둘레 spreadRadius 칸의 다른 적에게 마력 폭발이 번짐 (위력 ×
 * markBurst × spread × 터진 수 · 마법). 다섯이 찬 적을 칼 평타로 치면 다섯이 한꺼번에 터짐 — 하늘에서 거대한 마력 대검이 내리꽂힘 ({@link #giantSword}). 4번 칸은
 * 디멘션 슬래시 (피해 스킬 — 2026-11-05 스펠 오버드라이브 태세 대신, ArcanebladeSkills.rift). 2026-11-07 반전 소드 콜러 (스스로 싸우는 칼) 는 엘리멘탈 블레이드로
 * 바뀌어 뺌 (JobTraits17)
 */
public final class JobTraits7 {
    private JobTraits7() {}

    static final String AB = "arcaneblade";
    /** 각인 다섯 — 거대한 마력 대검이 떨어지는 틱 */
    static final int FULL_FALL = 2;
    private static final double[] WHITE = RuneKit.WHITE;

    static Jobs.Job jobIf(ServerPlayer sp, String id) {
        Jobs.Job j = JobSystem.job(sp);
        return j != null && id.equals(j.id()) ? j : null;
    }

    static double trait(Jobs.Job j, String key, double fallback) {
        return j == null || j.trait() == null ? fallback : j.trait().def().numOr(key, fallback);
    }

    // ------------------------------------------------------------ 아케인 블레이드: 각인이 터지면 번짐 · 다섯이면 거대한 마력 대검

    /**
     * 각인 n 개가 터짐 (victim 에서): delay 틱 뒤 둘레 spreadRadius 칸의 다른 적에게 마력 폭발이 번짐 (위력 × markBurst × spread × n — 마법). 그림: 둘레로 퍼지는 룬
     * 고리 · 땅 충격파 · 번진 적마다 X 검흔
     */
    static void spread(ServerPlayer sp, LivingEntity victim, Jobs.Job j, int n, int delay) {
        if (j == null || !AB.equals(j.id()) || victim == null || n <= 0) return;
        if (delay > 0) {
            String life = RuneKit.lifeToken(sp);
            RuneKit.later(delay, () -> {
                if (RuneKit.sameLife(sp, life)) spread(sp, victim, j, n, 0);
            });
            return;
        }
        if (!(victim.level() instanceof ServerLevel level)) return;
        double r = trait(j, "spreadRadius", 3.0);
        double mult = trait(j, "markBurst", 1.0) * trait(j, "spread", 0.5) * n;
        Vec3 at = Kit.bodyPoint(victim);
        if (RuneKit.fxQuota("jobSpread", 6)) {
            JobFx.runeRing(level, V.up(victim.position(), 0.12), r * 0.9, j, 8, Kit.rand() * 45, 0.35, Opt.of("alpha", 0.85));
            SkillFx.shock(level, V.up(victim.position(), 0.1), JobFx.outer(j), r, 0.35);
            SkillFx.sparks(level, at, JobFx.inner(j), 8, 4.0);
            SkillFx.play(level, "block.amethyst_block.resonate", at, 1.7, 0.6);
        }
        String life = RuneKit.lifeToken(sp);
        double P = JobSystem.power(sp, j);
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, victim.position(), r, 2.5)) {
            if (e == victim) continue;
            JobKit.hitLater(sp, e, life, Tasks.now(), () -> {
                if (JobKit.hit(sp, e, j, P, mult, true, false) > 0 && cnt[0]++ < 6 && e.level() instanceof ServerLevel lv) {
                    JobFx.cross(lv, Kit.bodyPoint(e), 0.6 + Kit.bodyPad(e) * 0.4, j, 0.25, Opt.empty());
                    SkillFx.sparks(lv, Kit.bodyPoint(e), JobFx.inner(j), 5, 3.0);
                }
            });
        }
    }

    /**
     * 각인 다섯이 한꺼번에 터짐 (평타 — JobTraits.onWeaponHit, 롤 가렌 R): 적 머리 위 7칸에서 거대한 마력 대검이 칼끝부터 내리꽂힘 (FULL_FALL 틱) → 꽂히는 순간 X 큰
     * 검기 · 터짐 · 룬 고리 · 충격 고리 · 돌 · 맞은 자리 큰 타격 (멈칫). 피해는 평타에 더한 몫 (JobTraits.markBonus — 위력 × markBurst × 다섯 · 큰 숫자), 둘레로 번지는
     * 것은 꽂히는 틱에 (spread)
     */
    static void giantSword(ServerPlayer sp, LivingEntity victim, Jobs.Job j) {
        if (!(victim.level() instanceof ServerLevel level)) return;
        Vec3 b = Kit.bodyPoint(victim);
        Vec3 top = V.up(b, 7.0);
        JobFx3d.fly(level, "phantom_sword", top, V.up(victim.position(), 0.2), JobFx3d.SWORD_TIP, 2.6, 0.1, 0.4, j, Opt.of("shine", 0.6, "fadeOut", 0.25));
        JobFx.line(level, top, b, j, 0.2, Opt.of("alpha", 0.6));
        SkillFx.play(level, "item.trident.thunder", b, 1.6, 0.6);
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(FULL_FALL, () -> {
            if (!RuneKit.sameLife(sp, life) || !(victim.level() instanceof ServerLevel lv)) return;
            Vec3 at = victim.isRemoved() ? b : Kit.bodyPoint(victim);
            double s = 1.5 + Kit.bodyPad(victim) * 0.6;
            Vec3 view = V.unit(V.sub(at, sp.getEyePosition()));
            JobFx.bigWave(lv, null, at, view, 0, s * 1.4, 45, 0.38, j, Opt.empty());
            JobFx.bigWave(lv, null, at, view, 0, s * 1.4, -45, 0.38, j, Opt.empty());
            JobFx.burst(lv, at, s, j, 0.35, Opt.empty());
            JobFx.runeRing(lv, V.up(victim.position(), 0.1), 2.4, j, 9, Kit.rand() * 40, 0.6, Opt.of("alpha", 0.9));
            SkillFx.shock(lv, V.up(victim.position(), 0.1), JobFx.outer(j), 2.6, 0.45);
            RuneKit.rockBurst(lv, V.up(victim.position(), 0.3), 6, 5.0, 0.15);
            SkillFx.sparks(lv, at, JobFx.inner(j), 18, 7.0);
            if (!victim.isRemoved()) JobImpact.hit(sp, victim, j, 1.8);
            SkillFx.play(lv, "random.explode", at, 1.2, 0.6);
            SkillFx.play(lv, "block.amethyst_block.resonate", at, 0.7, 1.0);
        });
    }
}
