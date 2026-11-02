package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Jobs;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 암살자 반전 료스알프(블레이드 댄서) 직업 스킬 (jobs.json advance.bladedancer — 흰 금빛 · 연분홍, 특성 「춤사위」 — JobTraits10): 빛살 베기 · 요정의 걸음 ·
 * 칼날 원무 · 눈부신 한낮 · 빛무리 춤. 1차 암살자와 같은 단검 · 같은 칸 역할 (작은 폭딜 · 기동 유틸 · 큰 딜 · 태세 · 궁극기) 을 뒤집음 — 숨어서 한 적을 노리던 그림자
 * 대신 드러내 놓고 둘레를 베는 빛의 춤 (몸을 돌리며 베기 · 옆 스텝 · 제자리 원무 · 요정 날개 · 적들 사이를 오가는 빛무리).
 * 피해 = 위력 P × 배율 (JobKit.strike — 같은 적은 10틱 넘게 띄움, 두 번 베기는 strikeSplit). 연출 = 2D 그림 (빛 꽃잎 · 빛 리본 rfx_jribbon · 빛 꽃 rfx_jlightbloom ·
 * 요정 날개 rfx_jfairy · 칼날 고리 · 엇갈린 베기 · 섬광 · 참격 판).
 */
final class BladedancerSkills {
    private BladedancerSkills() {}

    static void register() {
        JobSystem.register("bladedancer_ray", BladedancerSkills::ray);
        JobSystem.register("bladedancer_step", BladedancerSkills::step);
        JobSystem.register("bladedancer_waltz", BladedancerSkills::waltz);
        JobSystem.register("bladedancer_noon", BladedancerSkills::noon);
        JobSystem.register("bladedancer_ult", BladedancerSkills::ult);
    }

    /** 앞 cone° 안의 적인지 (몸이 가까우면 늘 — Kit.bodyInCone) */
    private static boolean inFront(LivingEntity e, Vec3 c, Vec3 f, double cone) {
        return Kit.bodyInCone(e, c, f, Math.toRadians(cone / 2), 1.2);
    }

    /** 맞힌 적 그림: 엇갈린 베기 · 섬광 · 빛 꽃잎 · 타격 그림 */
    private static void hitFx(LivingEntity e, Jobs.Job j, double scale) {
        if (!(e.level() instanceof ServerLevel lv)) return;
        Vec3 b = Kit.bodyPoint(e);
        JobFx.cross(lv, b, (0.7 + Kit.bodyPad(e) * 0.4) * scale, j, 0.26, Opt.of("vel", RuneKit.entVel(e)));
        JobFx.glint(lv, b, 0.28 * scale, j, 0.25, Opt.of("vel", RuneKit.entVel(e)));
        JobFx.petals(lv, b, 4, 2.8, 0.12, j, 0.6, Opt.empty());
        JobFx.hit(e, j, scale);
    }

    // ------------------------------------------------------------ 빛살 베기

    /**
     * 빛살 베기: 몸을 돌리며 보는 쪽으로 dash 칸 미끄러짐 (6틱 — 앞 적의 1.2칸 앞까지) · 1틱에 둘레 radius 칸을 벰 (spin — 몸 둘레 빛 리본 · 칼날 고리) → slashAt
     * 틱에 내려서며 앞 (240°) radius 칸을 크게 벰 (slash — 누운 참격 판 · 빛 꽃) · 빛 꽃잎을 흩뿌림. 묶였으면 제자리에서
     */
    static boolean ray(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double radius = k.num("radius", 3);
        int slashAt = Math.max(11, (int) k.num("slashAt", 11));
        double go = k.num("dash", 2.5);
        for (LivingEntity e : RuneKit.coneTargets(sp, go + 2.5, 60, 4)) {
            double d = Math.hypot(e.getX() - start.x, e.getZ() - start.z) - 1.2 - Kit.bodyPad(e);
            go = Math.min(go, Math.max(0, d));
        }
        boolean free = !Status.isRooted(sp) && !Kit.ridingAny(sp);
        if (free && go > 0.3) {
            Vec3 to = V.add(start, f, RuneKit.dashDistance(sp, f, go, 0.3, 1.5));
            JobMove.run(sp, JobMove.to(0, 6, JobMove.at(to), JobMove.OUT));
        }
        RpgCombat.setSteadfast(sp, slashAt + 2);
        // 돌며 벰 (1틱): 쓴 자리 조금 앞 둘레
        Vec3 c1 = V.add(start, f, Math.min(go, 1.0));
        RuneKit.later(1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            for (LivingEntity e : Kit.around(sp, c1, radius, 2.5)) JobKit.strike(sp, e, j, P, k.num("spin", 2.0), false, false, life, 0, d -> hitFx(e, j, 1.0));
            JobFx.ribbon(lv, V.up(sp.position(), 0.95), radius * 0.75, j, 0.4, 30, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
            JobFx.bladeRing(lv, V.up(c1, 0.1), radius * 0.85, j, 0.32, 30, Opt.of("alpha", 0.85));
            JobFx.slash(sp, j, 1.8, 0.25, 1.35, 5.0, 0.2);
            SkillFx.play(lv, "entity.player.attack.sweep", c1, 1.5, 0.8);
            SkillFx.play(lv, "block.amethyst_block.chime", c1, 1.7, 0.5);
        });
        // 미끄러지는 동안 빛 꽃잎이 뒤로 흩날림
        for (int t = 2; t <= 6; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                JobFx.petals(sp.level(), V.up(sp.position(), 1.0), 3, 1.8, 0.12, j, 0.6, JobFx.hide1p(sp));
                JobFx.speed(sp, j, 0.1, 0.6);
            });
        }
        // 내려서며 앞을 크게 벰
        JobKit.motionLater(sp, "bladedancer_ray:slash", slashAt - 3, life);
        RuneKit.later(slashAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 ff = Kit.facingOf(sp);
            for (LivingEntity e : Kit.around(sp, c, radius, 2.5)) {
                if (!inFront(e, c, ff, 240)) continue;
                JobKit.strike(sp, e, j, P, k.num("slash", 2.75), false, false, life, 0, d -> hitFx(e, j, 1.15));
            }
            Vec3 at = V.up(V.add(c, ff, 1.4), 1.0);
            JobFx.slashCard(lv, at, ff, 0, 2.4, 0.3, Vec3.ZERO, j, JobFx.hide1p(sp));
            JobFx.slash(sp, j, 2.0, 0.2, 1.9, 180.0, 0.24);
            JobFx.lightBloom(lv, V.up(V.add(c, ff, 2.2), 1.0), 0.9, j, 0.45, Opt.empty());
            JobFx.petals(lv, at, 10, 3.6, 0.14, j, 0.75, Opt.empty());
            SkillFx.play(lv, "entity.player.attack.sweep", at, 1.2, 1.0);
            SkillFx.play(lv, "block.amethyst_cluster.break", at, 1.4, 0.7);
        });
        return true;
    }

    // ------------------------------------------------------------ 요정의 걸음

    /**
     * 요정의 걸음: every 틱마다 옆으로 steps 번 스텝 (side 칸 — 왼쪽 · 오른쪽 · 가운데, 4틱 · 벽 앞까지) — 스텝마다 지나는 자리 둘레 reach 칸을 벰 (cut) · 떠난 자리 빛
     * 잔상 · 발밑 빛 리본. 스텝을 밟는 guardFor 틱 동안 받는 피해 − guard (JobTraits10). 묶였으면 제자리에서 벰
     */
    static boolean step(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int steps = Math.max(1, (int) k.num("steps", 3));
        int every = Math.max(10, (int) k.num("every", 10));
        double side = k.num("side", 2);
        double reach = k.num("reach", 3);
        double cut = k.num("cut", 0.95);
        String life = RuneKit.lifeToken(sp);
        Vec3 start = sp.position();
        Vec3 r = V.rightOf(Kit.facingOf(sp));
        JobTraits10.guardFor(sp, (int) k.num("guardFor", 30), JobUpgrades.num(sp, k, "guard", 0.3));
        double[] offs = {-side, side, 0};
        Kit.noFall(sp, steps * every + 20);
        for (int i = 0; i < steps; i++) {
            int ii = i;
            double off = offs[i % offs.length];
            RuneKit.later(i * every, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 from = sp.position();
                Vec3 want = V.add(start, r, off);
                Vec3 to = from;
                boolean free = !Status.isRooted(sp) && !Kit.ridingAny(sp);
                double len = Math.hypot(want.x - from.x, want.z - from.z);
                if (free && len > 0.2) {
                    Vec3 d = V.flat(V.sub(want, from));
                    to = V.add(from, d, RuneKit.dashDistance(sp, d, len, 0.3, 1.5));
                    JobMove.run(sp, JobMove.to(0, 4, JobMove.at(to), JobMove.OUT));
                }
                Vec3 mid = V.scale(V.add(from, to), 0.5);
                for (LivingEntity e : Kit.around(sp, mid, reach, 2.5)) JobKit.strike(sp, e, j, P, cut, false, false, life, 0, dd -> hitFx(e, j, 0.95));
                // 떠난 자리 빛 잔상 · 지나는 길 빛 줄 · 발밑 빛 리본 · 눈앞 베기 (번갈아 기운)
                RuneKit.shadeClone(sp, lv, from, Kit.facingOf(sp), 2, JobFx.outer(j), JobFx.inner(j), Opt.of("life", 0.4, "fade", 0.6, "alpha", 0.75));
                if (to != from) JobFx.line(lv, V.up(from, 0.9), V.up(to, 0.9), j, 0.25, JobFx.hide1p(sp));
                JobFx.ribbon(lv, V.up(to, 0.2), 1.2, j, 0.35, 26, Opt.of("alpha", 0.85));
                JobFx.petals(lv, V.up(from, 0.9), 5, 2.2, 0.12, j, 0.6, Opt.empty());
                JobFx.slash(sp, j, 1.8, 0.3, 1.25, ii % 2 == 0 ? 160.0 : 340.0, 0.18);
                SkillFx.play(lv, "entity.player.attack.sweep", mid, 1.5 + ii * 0.1, 0.7);
                SkillFx.play(lv, "block.amethyst_block.step", mid, 1.6, 0.8);
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 칼날 원무

    /**
     * 칼날 원무: 제자리에서 돌며 first 틱부터 every 틱마다 rounds 번 — 둘레 radius 칸을 두 번씩 벰 (round — 한 번에 넣고 숫자 두 줄) · 땅의 칼날 고리 · 몸 둘레 빛 리본 ·
     * 빛 고리. 마지막에 발밑에 빛 꽃이 핌
     */
    static boolean waltz(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int rounds = Math.max(1, (int) k.num("rounds", 3));
        int first = Math.max(1, (int) k.num("first", 1));
        int every = Math.max(10, (int) k.num("every", 10));
        double radius = k.num("radius", 3);
        double round = k.num("round", 2.85);
        String life = RuneKit.lifeToken(sp);
        RpgCombat.setSteadfast(sp, first + rounds * every);
        for (int i = 0; i < rounds; i++) {
            int ii = i;
            RuneKit.later(first + i * every, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 c = sp.position();
                for (LivingEntity e : Kit.around(sp, c, radius, 2.5)) JobKit.strikeSplit(sp, e, j, P, round, 2, 3, false, false, life, 0, d -> hitFx(e, j, 1.0));
                JobFx.bladeRing(lv, V.up(c, 0.1), radius * 0.9, j, 0.45, 34, Opt.of("alpha", 0.9, "spin", ii * 40.0));
                JobFx.ribbon(lv, V.up(c, 0.95), radius * 0.7, j, 0.45, 32, JobFx.hide1p(sp).with("spin", ii * 60.0));
                JobFx.ring(lv, V.up(c, 0.08), radius, j, ii % 2 == 1, 0.4, Opt.of("alpha", 0.8));
                JobFx.petals(lv, V.up(c, 1.0), 6, 3.2, 0.13, j, 0.65, Opt.empty());
                JobFx.slash(sp, j, 1.8, 0.25, 1.4, ii % 2 == 0 ? 0.0 : 180.0, 0.18);
                RuneKit.later(3, () -> {
                    if (RuneKit.sameLife(sp, life)) JobFx.slash(sp, j, 1.8, 0.25, 1.4, ii % 2 == 0 ? 180.0 : 0.0, 0.18);
                });
                SkillFx.play(lv, "entity.player.attack.sweep", c, 1.3 + ii * 0.15, 0.8);
                SkillFx.play(lv, "entity.player.attack.sweep", c, 1.5 + ii * 0.15, 0.6);
                if (ii == rounds - 1) {
                    JobFx.lightBloom(lv, V.up(c, 0.6), 1.2, j, 0.55, Opt.empty());
                    SkillFx.play(lv, "block.amethyst_block.chime", c, 1.2, 0.8);
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 눈부신 한낮

    /** 눈부신 한낮: dur 틱 태세 (JobTraits10 — 단검 평타 두 번 · 춤사위가 줄지 않음). 그림: 등 뒤 요정 날개가 펼쳐짐 · 가슴의 빛 꽃 · 섬광 · 빛 꽃잎 비 · 1인칭 눈앞 고리 */
    static boolean noon(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", (int) k.num("dur", 160));
        JobTraits.startStance(sp, k, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        JobFx.fairy(level, JobTraits10.wingAt(sp), f, 1.2, j, 0.7, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "grow", 0.35, "alpha", 0.9, "solo", 61.0));
        JobFx.lightBloom(level, V.up(V.add(c, f, 0.4), 1.2), 0.8, j, 0.5, JobFx.hide1p(sp));
        JobFx.glint(level, V.up(c, 2.3), 0.5, j, 0.45, JobFx.hide1p(sp));
        JobFx.aura(sp, j, 3.0, 1.0, 0.9);
        JobFx.ring(level, V.up(c, 0.06), 2.4, j, false, 0.5, Opt.empty());
        for (int i = 0; i < 10; i++) {
            double a = Kit.rand() * Math.PI * 2;
            double rr = 0.4 + Kit.rand() * 1.6;
            JobFx.petal(level, new Vec3(c.x + Math.cos(a) * rr, c.y + 2.6 + Kit.rand(), c.z + Math.sin(a) * rr), V.unit(new Vec3(0, -1, 0)), 1.2, 0.13, j, 1.0,
                    Opt.empty());
        }
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.5);
        SkillFx.play(level, "block.beacon.activate", c, 1.8, 0.6);
        SkillFx.play(level, "block.amethyst_block.chime", c, 1.0, 0.8);
        return true;
    }

    // ------------------------------------------------------------ 빛무리 춤

    /**
     * 빛무리 춤: 하늘이 연보라 황혼빛 (rpg:ult_alfheim) — every 틱마다 slashes 번 앞 (arc°) radius 칸의 보이는 적 (가까운 targets 명) 곁으로 차례로 순간 이동하며 벰
     * (slash — 같은 적은 10틱 넘게 띄움: 적이 하나면 두 번씩 한꺼번에 · 숫자 두 줄) · 빛 줄 · 빛 잔상 → 마지막 베기 뒤 처음 자리로 → finalDelay 틱 (마지막 베기부터)
     * 에 벤 적마다 빛 꽃이 크게 피어 터짐 (final — 큰 숫자). 앞에 적이 없으면 쓰지 않음
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        double radius = k.num("radius", 12);
        int maxN = Math.max(1, (int) k.num("targets", 6));
        List<LivingEntity> targets = new ArrayList<>(RuneKit.coneTargets(sp, radius, k.num("arc", 160), maxN));
        if (targets.isEmpty()) {
            Notify.notify(sp, "§c" + k.name() + " §7— 앞에 적이 없습니다", 30);
            return false;
        }
        int slashes = Math.max(1, (int) k.num("slashes", 8));
        int every = Math.max(1, (int) k.num("every", 5));
        int finalDelay = Math.max(10, (int) k.num("finalDelay", 15));
        int lastSlash = (slashes - 1) * every;
        int finalAt = lastSlash + finalDelay;
        double slash = k.num("slash", 2.5);
        String life = RuneKit.lifeToken(sp);
        Vec3 home = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        Ult.sky(level0, home, "rpg:ult_alfheim", finalAt + 20);
        Kit.noFall(sp, finalAt + 30);
        RpgCombat.setSteadfast(sp, finalAt + 10);
        // 시작: 요정 날개 · 빛 꽃 · 빛 리본 · 밝은 번쩍임 · 1인칭 눈앞 고리
        JobFx.fairy(level0, JobTraits10.wingAt(sp), f0, 1.3, j, 0.6, JobFx.hide1p(sp).with("grow", 0.3, "alpha", 0.9, "solo", 61.0));
        JobFx.ribbon(level0, V.up(home, 0.9), 1.4, j, 0.5, 30, JobFx.hide1p(sp));
        JobFx.lightBloom(level0, V.up(home, 1.3), 1.0, j, 0.5, JobFx.hide1p(sp));
        JobFx.petals(level0, V.up(home, 1.2), 14, 3.0, 0.14, j, 0.9, Opt.empty());
        JobFx.eyeRing(sp, j, 2.4, 1.1, 0.5);
        Ult.flash(level0, home, JobFx.inner(j), Opt.of("radius", 6.0, "time", new double[] {0.03, 0.08, 0.4}), sp);
        SkillFx.play(level0, "block.beacon.activate", home, 1.6, 0.8);
        SkillFx.play(level0, "block.amethyst_block.chime", home, 0.8, 1.0);
        int n = targets.size();
        for (int i = 0; i < slashes; i++) {
            int ii = i;
            LivingEntity e = targets.get(i % n);
            RuneKit.later(i * every, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !e.isAlive()) return;
                danceTo(sp, j, e, ii);
                if (ii > 0) JobKit.motion(sp, "bladedancer_ult:slash" + (ii % 2));
            });
        }
        if (n == 1) {
            LivingEntity e = targets.get(0);
            for (int i = 0; i < slashes; i += 2) {
                int c = Math.min(2, slashes - i);
                JobKit.strikeSplit(sp, e, j, P, slash * c, c, every, false, false, life, i * every, d -> hitFx(e, j, 1.0));
            }
        } else {
            for (int i = 0; i < slashes; i++) {
                LivingEntity e = targets.get(i % n);
                JobKit.strike(sp, e, j, P, slash, false, false, life, i * every, d -> hitFx(e, j, 1.0));
            }
        }
        // 처음 자리로 돌아옴 (벤 적들을 바라봄)
        RuneKit.later(lastSlash + 5, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 sum = Vec3.ZERO;
            int m = 0;
            for (LivingEntity e : targets) {
                if (e.isRemoved()) continue;
                sum = V.add(sum, Kit.bodyPoint(e));
                m++;
            }
            Vec3 prev = sp.position();
            Vec3 focus = m > 0 ? V.scale(sum, 1.0 / m) : V.add(home, f0, 4);
            float[] rot = Kit.lookRotation(V.up(home, sp.getEyeHeight()), focus);
            if (!Kit.moveTo(sp, home, rot[0], Mth.clamp(rot[1], -20F, 40F))) Kit.moveTo(sp, home);
            ServerLevel d = sp.level();
            RuneKit.shadeClone(sp, d, prev, Kit.facingOf(sp), 1, JobFx.outer(j), JobFx.inner(j), Opt.of("life", 0.45, "fade", 0.6, "alpha", 0.75));
            JobFx.line(d, V.up(prev, 1.0), V.up(home, 1.0), j, 0.25, JobFx.hide1p(sp));
            JobFx.ribbon(d, V.up(home, 0.3), 1.3, j, 0.45, 28, Opt.of("alpha", 0.85));
            SkillFx.play(d, "block.amethyst_block.chime", home, 1.5, 0.6);
            JobKit.motion(sp, "bladedancer_ult:home");
        });
        JobKit.motionLater(sp, "bladedancer_ult:final", finalAt - 3, life);
        // 벤 적마다 빛 꽃이 크게 피어 터짐
        for (LivingEntity e : targets) {
            RuneKit.later(finalAt - 6, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !(e.level() instanceof ServerLevel lv)) return;
                JobFx.lightBloom(lv, Kit.bodyPoint(e), 0.9 + Kit.bodyPad(e) * 0.5, j, 0.3, Opt.of("vel", RuneKit.entVel(e), "count", 3.0));
            });
        }
        RuneKit.later(finalAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Ult.flash(d, sp.position(), JobFx.inner(j), Opt.empty(), sp);
            RuneKit.shakeAround(d, sp.position(), 16, 0.3, 0.35);
            SkillFx.play(d, "entity.player.attack.crit", sp.position(), 0.7, 1.0);
            SkillFx.play(d, "block.amethyst_cluster.break", sp.position(), 0.9, 1.0);
            SkillFx.play(d, "block.beacon.power_select", sp.position(), 1.6, 0.8);
            Vec3 look = sp.getViewVector(1F);
            JobFx.lightBloom(d, V.add(V.add(sp.getEyePosition(), look, 3.0), JobFx.screenUp(look), -0.2), 1.0, j, 0.5, JobFx.only1p(sp));
            for (LivingEntity e : targets) {
                if (e.isRemoved()) continue;
                Vec3 at = Kit.bodyPoint(e);
                double s = 1.3 + Kit.bodyPad(e) * 0.6;
                JobFx.lightBloom(d, at, s, j, 0.55, Opt.empty());
                JobFx.burst(d, at, s, j, 0.4, Opt.empty());
                JobFx.glint(d, at, 0.6, j, 0.35, Opt.empty());
                JobFx.petals(d, at, 12, 4.5, 0.15, j, 0.85, Opt.empty());
                JobKit.strike(sp, e, j, P, k.num("final", 18), false, true, life, 0, null);
            }
        });
        return true;
    }

    /** 빛무리 춤의 한 번: 적 곁 (지금 쪽에서 ±130° 돌아간 자리 — 1.8칸 + 몸) 으로 순간 이동해 적을 봄 · 떠난 자리 빛 잔상 · 빛 줄 · 엇갈린 베기 · 눈앞 베기 · 빛 꽃잎 */
    private static void danceTo(ServerPlayer sp, Jobs.Job j, LivingEntity e, int i) {
        ServerLevel level = sp.level();
        Vec3 from = sp.position();
        Vec3 away = Math.hypot(from.x - e.getX(), from.z - e.getZ()) < 0.1 ? V.scale(Kit.facingOf(sp), -1) : V.flat(V.sub(from, e.position()));
        Vec3 spot = null;
        for (double turn : new double[] {i % 2 == 0 ? 130 : -130, i % 2 == 0 ? -130 : 130, 90, -90, 0}) {
            Vec3 dir = V.turnY(away, turn);
            Vec3 want = V.add(e.position(), dir, 1.8 + Kit.bodyPad(e));
            Vec3 s = Kit.standSpot(level, new Vec3(want.x, Math.max(want.y, e.getY()) + 0.5, want.z), 2.5);
            if (s != null && Math.abs(s.y - e.getY()) < 2.5) {
                spot = s;
                break;
            }
        }
        Vec3 focus = V.up(e.position(), e.getBbHeight() * 0.55);
        if (spot != null) {
            float[] rot = Kit.lookRotation(V.up(spot, sp.getEyeHeight()), focus);
            if (!Kit.moveTo(sp, spot, rot[0], Mth.clamp(rot[1], -25F, 45F))) spot = null;
        }
        Vec3 now = sp.position();
        RuneKit.shadeClone(sp, level, from, V.flat(V.sub(e.position(), from)), 1 + i % 3, JobFx.outer(j), JobFx.inner(j), Opt.of("life", 0.4, "fade", 0.6, "alpha",
                0.75));
        if (spot != null) JobFx.line(level, V.up(from, 1.0), V.up(now, 1.0), j, 0.24, JobFx.hide1p(sp));
        Vec3 at = Kit.bodyPoint(e);
        JobFx.ribbon(level, V.up(now, 0.9), 1.0, j, 0.3, 32, JobFx.hide1p(sp).with("spin", i * 45.0));
        JobFx.slash(sp, j, 1.9, 0.25, 1.35, i % 2 == 0 ? 25.0 : 205.0, 0.2);
        JobFx.petals(level, at, 4, 3.0, 0.13, j, 0.6, Opt.empty());
        JobFx.speed(sp, j, 0.12, 0.7);
        SkillFx.play(level, "entity.player.attack.sweep", at, 1.4 + i * 0.05, 0.8);
        SkillFx.play(level, "block.amethyst_block.chime", now, 1.6 + i * 0.04, 0.45);
    }
}
