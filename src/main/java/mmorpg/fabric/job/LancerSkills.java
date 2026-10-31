package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.29.0 창술사 직업 스킬 (jobs.json lancer — 금빛 · 흰빛, 특성 「창끝」): 삼연창 · 장대 도약 · 선회창 · 창벽 · 흐레스벨그의 발톱 (0.50.0 — 천 개의 창을 바꿈:
 * 궁극기도 제 창 · 몸으로 찌르고 돌진해 꿰뚫음).
 * 피해 = 위력 P × 배율 (JobKit.strike · strikeSplit). 연출 = 메이플식 2D 그림 (JobFx — 찌르기 번쩍 · 일섬 · 도는 창 · 빛의 창 · 내려찍기, 룬 문양 · 마법진은 쓰지 않음).
 * 찌르기는 1인칭에서 보는 쪽과 나란해 눕는 그림이 보이지 않으므로 카메라를 보는 번쩍 (jstab) · 세운 고리를 길 위에 둠.
 */
final class LancerSkills {
    private LancerSkills() {}

    /** 흙먼지 (누런 회색) */
    private static final double[] DUST = {0.66, 0.58, 0.46};

    static void register() {
        JobSystem.register("lancer_triple", LancerSkills::triple);
        JobSystem.register("lancer_vault", LancerSkills::vault);
        JobSystem.register("lancer_cyclone", LancerSkills::cyclone);
        JobSystem.register("lancer_wall", LancerSkills::wall);
        JobSystem.register("lancer_ult", LancerSkills::ult);
    }

    /** 가슴 높이 (찌르기가 나가는 자리) */
    private static Vec3 chest(ServerPlayer sp) {
        return V.up(sp.position(), 1.15);
    }

    /** 찌르기 한 번의 그림: 앞으로 뻗는 일섬 (3인칭) · 1인칭 손 앞 번쩍 · 창끝 번쩍 (len 칸) */
    private static void thrustFx(ServerPlayer sp, Jobs.Job j, Vec3 f, double len, double side, double scale) {
        ServerLevel level = sp.level();
        Vec3 from = V.add(V.add(chest(sp), f, 0.7), V.rightOf(f), side);
        Vec3 tip = V.add(from, f, len);
        JobFx.line(level, from, tip, j, 0.22, JobFx.hide1p(sp).with("alpha", 0.95));
        JobFx.stab(level, tip, 0.5 * scale, j, 0.24, Opt.empty());
        Vec3 look = sp.getViewVector(1F);
        Vec3 near = V.add(V.add(V.add(sp.getEyePosition(), look, 1.5), JobFx.screenUp(look), -0.3), V.rightOf(V.flat(look)), 0.15 + side * 0.4);
        JobFx.stab(level, near, 0.32 * scale, j, 0.18, JobFx.only1p(sp).with("vel", RuneKit.bodyVel(sp)));
        RuneKit.later(1, () -> {
            Vec3 look2 = sp.getViewVector(1F);
            JobFx.stab(level, V.add(V.add(sp.getEyePosition(), look2, 3.0), JobFx.screenUp(look2), -0.2), 0.42 * scale, j, 0.2, JobFx.only1p(sp));
        });
    }

    // ------------------------------------------------------------ 삼연창

    /**
     * 삼연창: 앞으로 빠르게 세 번 찌름 (gap 틱 간격) — 첫째 · 둘째는 앞 reach 칸 (폭 width) 의 적에게 hits[0] + hits[1] (한 번에 · 숫자 두 줄, gap 틱 간격),
     * 셋째는 두 줄로 관통 (pierce 칸 · 폭 pierceWidth) hits[2]
     */
    static boolean triple(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<Double> hits = k.def().numbersOrNull("hits");
        double h0 = hits != null && hits.size() > 0 ? hits.get(0) : 2;
        double h1 = hits != null && hits.size() > 1 ? hits.get(1) : 2;
        double h2 = hits != null && hits.size() > 2 ? hits.get(2) : 3.25;
        int gap = Math.max(1, (int) k.num("gap", 5));
        double reach = k.num("reach", 6);
        double pierce = k.num("pierce", 7);
        String life = RuneKit.lifeToken(sp);
        Vec3 f = Kit.facingOf(sp);
        ServerLevel level0 = sp.level();
        // 0.50.0 동작: 찌를 때마다 앞으로 내딛음 (step 칸씩 · 세 번)
        double step = k.num("step", 0.7);
        JobMove.run(sp, JobMove.to(0, 2, JobMove.ahead(sp, step, 0), JobMove.OUT), JobMove.to(gap, gap + 2, JobMove.ahead(sp, step, 0), JobMove.OUT),
                JobMove.to(gap * 2, gap * 2 + 2, JobMove.ahead(sp, step, 0), JobMove.OUT));
        // 첫째 · 둘째: 피해는 한 번에 (같은 적을 5틱 만에 두 번 치면 무적 시간에 막힘) · 숫자 두 줄
        int[] cnt = {0};
        for (Kit.LineHit lh : Kit.lineHits(sp, chest(sp), f, reach, k.num("width", 1.2) / 2)) {
            LivingEntity e = lh.e();
            if (cnt[0] >= 6) break;
            cnt[0]++;
            JobKit.strikeSplit(sp, e, j, P, h0 + h1, 2, gap, false, false, life, 0, d -> {
                if (RuneKit.fxQuota("jobTripleHit", 6)) {
                    JobFx.hit(e, j, 0.9);
                    JobFx.stab(e.level() instanceof ServerLevel lv ? lv : level0, Kit.bodyToward(e, sp.getEyePosition(), sp.getViewVector(1F)), 0.45, j, 0.2, Opt.empty());
                }
            });
        }
        thrustFx(sp, j, f, reach, 0, 1.0);
        SkillFx.play(level0, "item.spear.attack_miss", sp.position(), 1.3, 0.9);
        RuneKit.later(gap, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            thrustFx(sp, j, Kit.facingOf(sp), reach, 0.1, 1.0);
            SkillFx.play(sp.level(), "item.spear.attack_miss", sp.position(), 1.5, 0.9);
            for (Kit.LineHit lh : Kit.lineHits(sp, chest(sp), Kit.facingOf(sp), reach, k.num("width", 1.2) / 2)) {
                if (RuneKit.fxQuota("jobTripleHit2", 6)) JobFx.stab(sp.level(), Kit.bodyToward(lh.e(), sp.getEyePosition(), sp.getViewVector(1F)), 0.45, j, 0.2,
                        Opt.empty());
            }
        });
        // 셋째: 두 줄 관통 — 길 위의 세운 고리 (1인칭에서도 보임) · 두 줄 일섬
        RuneKit.later(gap * 2, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 ff = Kit.facingOf(sp);
            Vec3 from = chest(sp);
            double half = k.num("pierceWidth", 2.4) / 2;
            thrustFx(sp, j, ff, pierce, -0.35, 1.35);
            thrustFx(sp, j, ff, pierce, 0.35, 1.35);
            for (double d = 2.0; d < pierce; d += 2.2) {
                JobFx.ringFacing(level, V.add(from, ff, d), 0.55 + d * 0.06, j, V.scale(ff, -1), 0.25, Opt.empty());
            }
            JobFx.dash(level, V.add(from, ff, 0.8), V.add(from, ff, pierce), j, 0.3, JobFx.hide1p(sp));
            SkillFx.play(level, "item.trident.riptide_1", sp.position(), 1.2, 0.8);
            SkillFx.play(level, "entity.player.attack.knockback", sp.position(), 0.8, 0.8);
            int[] n = {0};
            for (Kit.LineHit lh : Kit.lineHits(sp, from, ff, pierce, half)) {
                LivingEntity e = lh.e();
                if (n[0]++ >= 8) break;
                JobKit.strike(sp, e, j, P, h2, false, false, life, 0, x -> {
                    if (RuneKit.fxQuota("jobTriplePierce", 6)) {
                        JobFx.hit(e, j, 1.2);
                        JobFx.stab(level, Kit.bodyToward(e, sp.getEyePosition(), ff), 0.7, j, 0.25, Opt.empty());
                    }
                    if (JobKit.pushable(e)) RuneKit.knock(e, ff, 0.25, 0.05);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 장대 도약

    /** 착지 자리: 앞 dist 칸부터 한 칸씩 줄여 가며 설 수 있는 땅 (높이 차 3칸 안) — 없으면 null */
    private static Vec3 vaultSpot(ServerPlayer sp, Vec3 f, double dist) {
        ServerLevel level = sp.level();
        Vec3 s = sp.position();
        for (double d = dist; d >= 2; d -= 1) {
            Vec3 want = V.add(s, f, d);
            Vec3 p = Kit.standSpot(level, new Vec3(want.x, s.y + 2.5, want.z), 5.0);
            if (p != null && Math.abs(p.y - s.y) <= 3) return p;
        }
        return null;
    }

    /**
     * 장대 도약: 창을 땅에 짚고 (짚은 자리에 빛의 창) 높이 height 의 포물선으로 dist 칸 앞까지 ticks 틱에 뛰어 넘음 (적 위를 넘어 — 한 틱에 한 마디, 막히면 그 자리) →
     * 내려찍음 (둘레 radius 칸 slam · 땅의 금 · 창끝 번쩍 · 하늘에서 꽂히는 빛의 창). 설 땅이 없으면 쓰지 않음
     */
    static boolean vault(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 f = Kit.facingOf(sp);
        Vec3 to = vaultSpot(sp, f, k.num("dist", 7));
        if (to == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 내려설 곳이 없습니다", 30);
            return false;
        }
        Vec3 from = sp.position();
        int ticks = Math.max(4, (int) k.num("ticks", 10));
        double height = k.num("height", 3.6);
        String life = RuneKit.lifeToken(sp);
        Kit.noFall(sp, ticks + 30);
        RpgCombat.setSteadfast(sp, ticks + 6);
        // 짚음: 짚은 자리의 빛의 창 (창끝이 땅) · 흙먼지 · 소리
        JobFx.spear(level, V.up(from, 0.1), V.DOWN, 1.1, 0, 0.55, j, Opt.of("fade", 0.4));
        JobFx.stab(level, V.up(from, 0.3), 0.5, j, 0.25, JobFx.hide1p(sp));
        SkillFx.sparks(level, V.up(from, 0.2), DUST, 8, 3);
        SkillFx.play(level, "mob.breeze.jump", from, 0.9, 0.9);
        SkillFx.play(level, "item.trident.riptide_2", from, 1.3, 0.6);
        for (int s = 1; s <= ticks; s++) {
            int ss = s;
            RuneKit.later(s, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double t = (double) ss / ticks;
                Vec3 p = V.up(V.lerp(from, to, t), height * 4 * t * (1 - t));
                Vec3 prev = sp.position();
                Kit.moveTo(sp, p); // 막히면 그 자리 (다음 마디에서 다시)
                ServerLevel d = sp.level();
                if (ss % 2 == 0) JobFx.dash(d, V.up(prev, 0.9), V.up(sp.position(), 0.9), j, 0.25, JobFx.hide1p(sp).with("alpha", 0.8));
                // 0.50.0 독수리 강하 (사용자 "직업 고유 연출"): 솟구친 몸 뒤로 독수리 깃털이 흩날림 (1인칭에서는 숨김)
                if (ss % 3 == 1) JobFx.feathers(d, V.up(prev, 1.2), 2, 0.4, 0.17, j, 0.9, JobFx.hide1p(sp));
                if (t > 0.5) JobFx.speed(sp, j, 0.1, 0.7);
                if (ss == ticks) vaultSlam(sp, j, k, P, sp.position(), life);
            });
        }
        return true;
    }

    /** 내려찍음: 땅의 금 · 충격 고리 · 창끝 번쩍 · 하늘에서 꽂히는 빛의 창 · 1인칭 위에서 아래로 긋는 베기 — 둘레 radius 칸 slam (밀리는 적은 살짝 뜸) */
    private static void vaultSlam(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 at, String life) {
        ServerLevel level = sp.level();
        double r = k.num("radius", 3);
        Vec3 ground = new Vec3(at.x, RuneKit.groundAt(level, at, at.y), at.z);
        Vec3 f = Kit.facingOf(sp);
        Vec3 front = V.add(ground, f, 0.9);
        JobFx.smash(level, V.up(ground, 0.05), r, j, 0.55, Opt.empty());
        JobFx.stab(level, V.up(front, 0.4), 1.1, j, 0.3, Opt.empty());
        JobFx.spearFly(level, V.up(front, 5.5), V.up(front, 0.05), j, 60, 1.0, Opt.empty());
        JobFx.slash(sp, j, 2.2, 0.2, 1.7, 95.0, 0.2);
        RuneKit.rockBurst(level, V.up(ground, 0.2), 8, 4.5, 0.16);
        SkillFx.sparks(level, V.up(ground, 0.3), JobKit.glow(j), 10, 5);
        SkillFx.sparks(level, V.up(ground, 0.2), DUST, 8, 3);
        JobKit.shake(sp, 10, 0.3, 0.25);
        SkillFx.play(level, "mace.heavy_smash_ground", ground, 1.0, 0.9);
        SkillFx.play(level, "item.trident.hit_ground", ground, 0.8, 1.0);
        for (LivingEntity e : Kit.around(sp, ground, r, 2.5)) {
            JobKit.strike(sp, e, j, P, k.num("slam", 4.35), false, false, life, 0, x -> {
                if (JobKit.pushable(e)) Kit.knockback(e, 0, 0, 0.3);
                if (RuneKit.fxQuota("jobVaultSlam", 6)) JobFx.hit(e, j, 1.15);
            });
        }
    }

    // ------------------------------------------------------------ 선회창

    /**
     * 선회창: 창을 머리 위로 돌리며 (gap × spins 틱 — 허리 높이의 도는 창 원판 · 머리 위 작은 원판 · 1인칭 돌아가는 베기) gap 틱마다 둘레 radius 칸을 벰 (spin),
     * 마지막 바퀴는 밀쳐냄 (knock)
     */
    static boolean cyclone(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int spins = Math.max(1, (int) k.num("spins", 2));
        int gap = Math.max(1, (int) k.num("gap", 10));
        double r = k.num("radius", 5);
        int dur = spins * gap;
        String life = RuneKit.lifeToken(sp);
        // 0.50.0 동작: 뛰어올라 (hop 칸) 공중에서 창을 두 바퀴 돌리고 착지
        double ground = sp.getY();
        JobMove.run(sp, JobMove.hop(0, (int) k.num("gap", 10) + 6, from -> from, k.num("hop", 1.5)));
        // 0.47.0 입체: 허리 높이에서 크게 도는 빛의 창 (날 끝 반지름 r — 몸이 가운데) · 머리 위에서 돌리는 창 (1인칭에서는 숨김)
        double rate = 1260;
        int big = JobFx3d.spawn(sp.level(), "spear", new Vec3(0, 1.0, 0), j, Opt.of("follow", (double) sp.getId(), "dir", new Vec3(1, 0, 0), "face", V.UP, "spin",
                rate, "spinAxis", "up", "scale", r * 0.92 * 2 / JobFx3d.SPEAR_LEN, "life", dur / 20.0 + 0.1, "fadeIn", 0.06, "fadeOut", 0.15, "glowAlpha", 0.45, "shine",
                0.3, "alpha", 0.95));
        int top = JobFx3d.spawn(sp.level(), "spear", new Vec3(0, 2.45, 0), j, Opt.of("follow", (double) sp.getId(), "view", 1.0, "owner", (double) sp.getId(), "dir",
                new Vec3(0, 0, 1), "face", V.UP, "spin", rate, "spinAxis", "up", "scale", 0.82, "life", dur / 20.0 + 0.1, "fadeIn", 0.06, "fadeOut", 0.15, "glowAlpha",
                0.45, "shine", 0.3));
        JobFx3d.guard(sp, life, new int[] {big, top}, dur + 4);
        SkillFx.play(sp.level(), "mob.breeze.whirl", sp.position(), 1.1, 1.0);
        SkillFx.play(sp.level(), "item.trident.riptide_1", sp.position(), 0.9, 0.7);
        for (int t = 0; t <= dur; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = sp.position();
                Vec3 vel = RuneKit.bodyVel(sp);
                Opt o = Opt.of("vel", vel, "phase", (double) ((tt / 2) % JobFx.SPIN), "fade", 0.1, "alpha", 0.95);
                JobFx.spin(level, V.up(c, 1.0), r * 0.92, j, V.UP, 0.14, o);
                JobFx.spin(level, V.up(c, 2.45), 1.15, j, V.UP, 0.14, o.with("view", 1.0, "owner", (double) sp.getId()));
                JobFx.spin(level, V.up(c, 2.4), 1.15, j, V.DOWN, 0.14, o.with("view", 1.0, "owner", (double) sp.getId()));
                if (tt % 4 == 0) JobFx.slash(sp, j, 2.1, 0.4, 1.6, (tt * 75) % 360, 0.18);
                if (tt % 10 == 4) SkillFx.play(level, "entity.player.attack.sweep", c, 0.9 + tt * 0.01, 0.6);
            });
        }
        for (int i = 0; i < spins; i++) {
            boolean lastSpin = i == spins - 1;
            int ii = i;
            RuneKit.later(i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = sp.position();
                Vec3 cg = new Vec3(c.x, ground, c.z); // 0.50.0 뛰어오른 동안에도 고리는 땅에
                JobFx.ring(level, V.up(cg, 0.1), r, j, ii % 2 == 1, 0.35, Opt.empty());
                if (lastSpin && spins > 1) {
                    RuneKit.later(3, () -> JobFx.ring(level, V.up(cg, 0.12), r * 0.6, j, true, 0.3, Opt.of("alpha", 0.85)));
                    SkillFx.play(level, "entity.player.attack.knockback", c, 0.7, 1.0);
                }
                int[] cnt = {0};
                for (LivingEntity e : Kit.around(sp, c, r, 2.5)) {
                    JobKit.strike(sp, e, j, P, k.num("spin", 6.525), false, false, life, 0, d -> {
                        if (lastSpin && spins > 1 && JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), sp.position()), k.num("knock", 0.7), 0.3);
                        if (cnt[0]++ < 6) {
                            JobFx.hit(e, j, 1.0);
                            JobFx.stab(level, Kit.bodyPoint(e), 0.45, j, 0.2, Opt.empty());
                        }
                    });
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 창벽

    /**
     * 창벽: dur 틱 태세 — every 틱마다 앞 reach 칸 (arc°) 의 적을 자동으로 찌름 (thrust — 6명까지), 정면 front° 에서 온 피해 − guard (JobTraits3.sourceReduce),
     * 이동 느려짐 (구속 slow + 1). 그림: 앞으로 펼쳐지는 빛의 창 다섯 · 1인칭 눈앞 고리, 태세 동안 앞의 창끝 셋 (JobTraits3.stanceFx)
     */
    static boolean wall(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) k.num("dur", 160);
        int every = Math.max(2, (int) k.num("every", 10));
        JobTraits.startStance(sp, k, dur);
        RuneKit.effect(sp, "slowness", dur, (int) k.num("slow", 0));
        String life = RuneKit.lifeToken(sp);
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 base = V.up(sp.position(), 1.1);
        for (int i = -2; i <= 2; i++) {
            Vec3 d = V.turnY(f, i * 22);
            JobFx.spear(level, V.add(base, d, 2.3), d, 0.85, 0, 0.6, j, JobFx.hide1p(sp).with("fade", 0.4));
        }
        JobFx.ring(level, V.up(sp.position(), 0.06), 1.8, j, false, 0.4, JobFx.hide1p(sp));
        JobFx.eyeRing(sp, j, 2.4, 0.9, 0.45);
        // 0.47.0 입체: 태세 동안 몸 앞에 세운 빛의 창 셋 (가슴 높이 — 주인을 따라감, 1인칭에서는 숨김)
        int[] wallIds = new int[3];
        for (int i = -1; i <= 1; i++) {
            Vec3 dirL = V.unit(new Vec3(i * 0.25, 0.05, 1));
            wallIds[i + 1] = JobFx3d.spawn(level, "spear", new Vec3(i * 0.55, 1.1, 1.9 - JobFx3d.SPEAR_TIP * 0.53), j, Opt.of("follow", (double) sp.getId(), "view", 1.0,
                    "owner", (double) sp.getId(), "dir", dirL, "face", V.UP, "scale", 0.53, "life", dur / 20.0, "pop", 0.2, "fadeOut", 0.3, "glowAlpha", 0.45, "shine",
                    0.25, "alpha", 0.92));
        }
        JobFx3d.hold(sp, "lancer_wall", wallIds);
        JobFx3d.guard(sp, life, wallIds, dur);
        mmorpg.fabric.DamageNumbers.showFace(sp, "§6§l" + k.name() + " §7— 정면 피해 −" + Math.round(k.num("guard", 0.3) * 100) + "%", 2.4, 0, -0.3);
        SkillFx.play(level, "item.armor.equip_iron", sp.position(), 0.9, 1.0);
        SkillFx.play(level, "item.trident.riptide_1", sp.position(), 0.7, 0.6);
        for (int t = every; t <= dur; t += every) {
            RuneKit.later(t, () -> {
                if (RuneKit.sameLife(sp, life) && JobTraits.stanceOn(sp, k.id())) wallThrust(sp, j, k, P, life);
            });
        }
        return true;
    }

    /** 앞 부채꼴 (보는 쪽 수평 arc° · reach 칸) 의 적 — 가까워도 옆 · 뒤는 아님 (RuneKit.coneTargets 는 1.2칸 안을 방향 없이 셈), 벽 뒤 제외 · 가까운 순 max 명 */
    private static List<LivingEntity> front(ServerPlayer sp, double reach, double arc, int max) {
        Vec3 c = sp.position();
        Vec3 head = sp.getEyePosition();
        Vec3 f = Kit.facingOf(sp);
        double half = Math.toRadians(arc / 2);
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, c, reach, 3)) {
            if (Kit.bodyInCone(e, c, f, half, 0.2) && Kit.lineOfSight(sp, head, Kit.bodyAt(e, head.y))) list.add(e);
        }
        list.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
        return list.size() > max ? new ArrayList<>(list.subList(0, max)) : list;
    }

    /** 창벽의 자동 찌르기 한 번: 앞 부채꼴의 적마다 thrust — 찌르는 줄 · 창끝 번쩍 (1인칭은 그 적 쪽 눈앞 번쩍) */
    private static void wallThrust(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life) {
        List<LivingEntity> list = front(sp, k.num("reach", 4), k.num("arc", 100), 6);
        if (list.isEmpty()) return;
        ServerLevel level = sp.level();
        Vec3 from = chest(sp);
        for (LivingEntity e : list) {
            Vec3 at = Kit.bodyToward(e, sp.getEyePosition(), V.unit(V.sub(Kit.bodyPoint(e), sp.getEyePosition())));
            JobFx.line(level, V.add(from, V.flat(V.sub(at, from)), 0.6), at, j, 0.2, JobFx.hide1p(sp));
            JobKit.strike(sp, e, j, P, k.num("thrust", 1.1), false, false, life, 0, d -> {
                if (RuneKit.fxQuota("jobWallThrust", 6)) JobFx.stab(level, Kit.bodyPoint(e), 0.45, j, 0.2, Opt.empty());
            });
        }
        Vec3 look = sp.getViewVector(1F);
        Vec3 toE = V.unit(V.sub(Kit.bodyPoint(list.get(0)), sp.getEyePosition()));
        JobFx.stab(level, V.add(V.add(sp.getEyePosition(), V.unit(V.add(look, toE)), 1.8), JobFx.screenUp(look), -0.25), 0.3, j, 0.18, JobFx.only1p(sp));
        SkillFx.play(level, "item.spear.attack_miss", sp.position(), 1.4 + Kit.rand() * 0.2, 0.6);
    }

    // ------------------------------------------------------------ 흐레스벨그의 발톱 (0.50.0 — 천 개의 창을 바꿈)

    /** 돌진 잔상 (짙은 금 — 찌르는 그림자 분신) */
    private static final double[] SHADE = {0.5, 0.36, 0.08};
    /** 꿰뚫음의 충격이 앞으로 뻗는 빠르기 (칸/초) */
    private static final double PIERCE_SPEED = 40;

    /**
     * 흐레스벨그의 발톱 (0.50.0 — 사용자 "창술사 궁 바꾸자": 창을 하늘로 던져 수백 자루로 갈라지던 천 개의 창 대신 제 창 · 몸으로 — 검사 라그나로크와 같은 기준):
     * 앞 (100°) 의 가장 가까운 보이는 적을 바라보고 (뛰는 중이면 발밑 땅에 내려서서) 그 자리에 버팀 → first 틱부터 gap 틱마다 (waves 번) 앞 reach 칸 (arc°) 에
     * 찌르기 폭풍 — 적마다 thrust × thrusts (한 번에 · 숫자 thrusts 줄, 2틱 간격) → 뒤로 짧게 뛰며 (back 칸) 창을 깊이 당김 → dashTicks 틱에 앞으로 돌진 (보는 쪽
     * 줄 위 첫 적의 1.3칸 앞 — 없으면 dash 칸, 벽 · 낭떠러지 앞까지) → 마지막 물결 finalDelay 틱 뒤 온 힘으로 꿰뚫음 ({@link #pierce}).
     * 그림: 매 틱 몸에서 앞 부채꼴로 뻗는 찌르기 한두 번 (일섬 · 창끝 번쩍 · 잠깐 남는 창 잔상 — 반쯤은 맞는 적의 몸으로), 1인칭 눈앞 창끝 번쩍, 몸 앞의 세운 바람 고리,
     * 독수리 깃털, 돌진의 찌르는 그림자 분신 · 줄기 · 흙먼지. 던지는 창 · 하늘에서 떨어지는 창은 없음. 같은 적은 10틱 넘게 띄움 (gap · finalDelay — JobsTest)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        int waves = Math.max(1, (int) k.num("waves", 4));
        int thrusts = Math.max(1, (int) k.num("thrusts", 4));
        double thrust = k.num("thrust", 1.75);
        int gap = Math.max(1, (int) k.num("gap", 10));
        int first = Math.max(1, (int) k.num("first", 6));
        int lastWave = first + (waves - 1) * gap;
        int finalDelay = Math.max(8, (int) k.num("finalDelay", 14));
        int finalAt = lastWave + finalDelay;
        int dashTicks = Math.max(2, Math.min(finalDelay - 6, (int) k.num("dashTicks", 5)));
        int dash0 = finalAt - dashTicks;
        int coil0 = Math.max(lastWave + 2, dash0 - 4);
        double reach = k.num("reach", 7);
        double arc = k.num("arc", 70);
        String life = RuneKit.lifeToken(sp);
        Vec3 c0 = sp.position();
        double[] glow = JobKit.glow(j);
        // 겨눔: 앞 (100°) reach + 1 칸 안의 가장 가까운 보이는 적 쪽으로 (폭풍이 그 적을 가운데로)
        List<LivingEntity> aim = front(sp, reach + 1, 100, 4);
        if (!aim.isEmpty()) RuneKit.moveTo(sp, c0, V.up(aim.get(0).position(), aim.get(0).getBbHeight() * 0.6));
        boolean held = Status.isRooted(sp) || Kit.ridingAny(sp);
        // 뛰는 중에 쓰면 발밑 땅 (6칸 안) 에 내려서서 (땅에 버티고 찌르는 궁극기 — 공중에 떠서 찌르지 않게)
        Double gy0 = RuneKit.solidGround(level0, V.up(c0, 0.1), 6);
        Vec3 base = gy0 != null && c0.y - gy0 > 0.05 ? new Vec3(c0.x, gy0, c0.z) : c0;
        Ult.sky(level0, c0, "rpg:ult_gold", finalAt + 25);
        RpgCombat.setSteadfast(sp, finalAt + 10);
        Kit.noFall(sp, finalAt + 30);
        // 몸: 그 자리에 버팀 (찌르기 폭풍) → 뒤로 짧게 뜀 (창을 당김) → 앞으로 돌진 (마지막 틱에 꿰뚫음)
        double back = k.num("back", 1.5);
        double dash = k.num("dash", 6);
        if (held) JobMove.run(sp, JobMove.hold(0, finalAt));
        else JobMove.run(sp, JobMove.to(0, 3, JobMove.at(base), JobMove.OUT), JobMove.hold(3, coil0), JobMove.hop(coil0, dash0, from -> backStep(sp, from, back), 0.35),
                JobMove.to(dash0, finalAt, from -> chargeStop(sp, from, dash), JobMove.OUT));
        // 발동: 발을 구름 (땅의 바람 고리 · 흙먼지) · 짧게 솟는 투지 (몸을 가리지 않게 0.4초) · 1인칭 눈앞 고리 · 깃털
        JobFx.wind(level0, V.up(base, 0.08), 2.2, j, 0.45, Opt.empty());
        SkillFx.sparks(level0, V.up(base, 0.2), DUST, 10, 3.5);
        JobFx.aura(sp, j, 2.4, 0.4, 0.6);
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.4);
        JobFx.feathers(level0, V.up(base, 1.4), 5, 0.9, 0.2, j, 1.1, JobFx.hide1p(sp));
        SkillFx.play(level0, "item.trident.riptide_1", c0, 1.0, 0.9);
        SkillFx.play(level0, "mob.breeze.whirl", c0, 1.3, 0.7);
        SkillFx.play(level0, "item.armor.equip_iron", c0, 0.8, 0.8);
        // 찌르기 폭풍 (그림 — 매 틱) · 물결마다 피해 (적마다 thrust × thrusts — 한 번에 · 숫자 thrusts 줄)
        List<LivingEntity> cur = new ArrayList<>();
        for (int t = first - 1; t < coil0; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (RuneKit.sameLife(sp, life)) storm(sp, j, tt, reach, arc, cur);
            });
        }
        for (int w = 0; w < waves; w++) {
            int ww = w;
            RuneKit.later(first + w * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                List<LivingEntity> list = front(sp, reach, arc, 10);
                cur.clear();
                cur.addAll(list);
                for (LivingEntity e : list) {
                    JobKit.strikeSplit(sp, e, j, P, thrust * thrusts, thrusts, 2, false, false, life, 0, x -> stormHitFx(sp, e, j, thrusts));
                }
                Vec3 f = Kit.facingOf(sp);
                JobFx.ringFacing(d, V.add(chest(sp), f, 2.6), 1.3, j, V.scale(f, -1), 0.3, Opt.of("alpha", 0.9));
                JobFx.wind(d, V.up(sp.position(), 0.08), 1.8, j, 0.35, Opt.of("alpha", 0.7));
                SkillFx.sparks(d, V.up(sp.position(), 0.15), DUST, 4, 2.2);
                SkillFx.play(d, "item.trident.hit", sp.position(), 1.1 + ww * 0.08, 0.7);
                SkillFx.play(d, "entity.player.attack.sweep", sp.position(), 1.4 + ww * 0.05, 0.45);
                JobKit.shake(sp, 8, 0.08 + 0.02 * ww, 0.15);
            });
        }
        // 창을 깊이 당김: 창끝 반짝임 · 짧게 솟는 투지 · 모여드는 깃털 · 발밑 바람 · 뛰는 소리
        RuneKit.later(coil0, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 f = Kit.facingOf(sp);
            JobFx.aura(sp, j, 2.4, 0.35, 0.55);
            RuneKit.rfx(d, "prop", V.add(V.add(chest(sp), f, 1.1), V.rightOf(f), 0.15), Opt.of("size", 0.3, "life", 0.4, "cell", (double) RuneKit.RFX.get("glint")[0], "spin",
                    20.0, "spinRate", 240.0, "color", JobFx.WHITE, "grow", 0.6, "fade", 0.5));
            JobFx.feathers(d, V.up(sp.position(), 1.2), 4, 1.2, 0.18, j, 0.8, JobFx.hide1p(sp));
            JobFx.wind(d, V.up(sp.position(), 0.08), 1.5, j, 0.3, Opt.of("alpha", 0.75));
            SkillFx.sparks(d, V.up(sp.position(), 0.2), DUST, 6, 2.5);
            SkillFx.play(d, "item.trident.riptide_2", sp.position(), 1.2, 0.7);
            SkillFx.play(d, "mob.breeze.jump", sp.position(), 1.0, 0.6);
        });
        // 돌진: 지나온 자리마다 찌르는 그림자 분신 · 줄기 (1인칭에서는 숨김) · 발밑 흙먼지 · 깃털 · 1인칭 빠른 줄
        Vec3[] last = {null};
        for (int t = dash0 + 1; t <= finalAt; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 now = sp.position();
                Vec3 f = Kit.facingOf(sp);
                if (last[0] != null && now.distanceToSqr(last[0]) > 0.04) {
                    if (tt % 2 == 0) RuneKit.shadeClone(sp, d, last[0], f, 3, SHADE, glow, Opt.of("life", 0.35, "fade", 0.6, "alpha", 0.7));
                    JobFx.dash(d, V.up(last[0], 1.0), V.up(now, 1.0), j, 0.25, JobFx.hide1p(sp));
                    SkillFx.sparks(d, V.up(now, 0.12), DUST, 3, 2.2);
                    if (tt % 2 == 1) JobFx.feathers(d, V.up(last[0], 1.2), 1, 0.3, 0.16, j, 0.7, JobFx.hide1p(sp));
                    JobFx.speed(sp, j, tt == finalAt ? 0.22 : 0.1, 0.8);
                }
                last[0] = now;
            });
        }
        RuneKit.later(dash0, () -> {
            if (RuneKit.sameLife(sp, life)) {
                last[0] = sp.position();
                SkillFx.play(sp.level(), "item.trident.riptide_3", sp.position(), 1.3, 0.6);
            }
        });
        RuneKit.later(finalAt, () -> {
            if (RuneKit.sameLife(sp, life)) pierce(sp, j, k, P, life);
        });
        return true;
    }

    /** 뒤로 짧게 뛸 곳: 보는 쪽 반대로 back 칸 (벽 · 낭떠러지 · 물 앞까지 — 못 가면 그 자리) */
    private static Vec3 backStep(ServerPlayer sp, Vec3 from, double back) {
        Vec3 b = V.scale(Kit.facingOf(sp), -1);
        double can = RuneKit.dashDistance(sp, b, back, 0.3, 1.5);
        List<Kit.Spot> spots = can > 0.3 ? Kit.dashSpots(sp.level(), from, b, can) : List.of();
        return spots.isEmpty() ? from : spots.get(spots.size() - 1).p();
    }

    /** 돌진할 곳: 보는 쪽 (수평) 줄 위 첫 적의 1.3칸 앞 (몸 가장자리 기준 — 없으면 dash 칸), 벽 · 낭떠러지 · 물 앞까지 */
    private static Vec3 chargeStop(ServerPlayer sp, Vec3 from, double dash) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        double want = dash;
        List<Kit.LineHit> line = Kit.lineHits(sp, V.up(from, 1.15), f, dash + 2.5, 0.9);
        if (!line.isEmpty()) want = Math.min(dash, Math.max(0, line.get(0).t() - 1.3));
        double free = want < 0.3 ? 0 : Kit.reachFlat(level, from, f, want);
        List<Kit.Spot> spots = free > 0.3 ? Kit.dashSpots(level, from, f, free) : List.of();
        return spots.isEmpty() ? from : spots.get(spots.size() - 1).p();
    }

    /** 찌르기 폭풍 한 틱 (그림): 찌르기 한두 번 (짝수 틱 둘) — 앞 부채꼴 (arc°) 의 흩어진 곳 · 반쯤은 맞는 적 (cur) 의 몸으로, 1인칭 눈앞 창끝 번쩍 · 바람 고리 · 깃털 */
    private static void storm(ServerPlayer sp, Jobs.Job j, int tick, double reach, double arc, List<LivingEntity> cur) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 chest = chest(sp);
        int n = tick % 2 == 0 ? 2 : 1;
        for (int i = 0; i < n; i++) {
            Vec3 from = V.add(V.add(chest, f, 0.6), V.rightOf(f), (Kit.rand() - 0.5) * 0.5);
            LivingEntity e = cur.isEmpty() || Kit.rand() < 0.5 ? null : cur.get((int) (Kit.rand() * cur.size()));
            Vec3 tip;
            if (e != null && !e.isRemoved() && e.isAlive()) {
                tip = V.add(Kit.bodyPoint(e), new Vec3((Kit.rand() - 0.5) * 0.5, (Kit.rand() - 0.5) * 0.6, (Kit.rand() - 0.5) * 0.5));
            } else {
                Vec3 d = V.unit(V.add(V.turnY(f, (Kit.rand() * 2 - 1) * arc / 2), new Vec3(0, (Kit.rand() - 0.4) * 0.35, 0)));
                tip = V.add(from, d, reach * (0.55 + Kit.rand() * 0.45));
            }
            Vec3 dir = V.unit(V.sub(tip, from));
            JobFx.line(level, from, tip, j, 0.14, JobFx.hide1p(sp).with("alpha", 0.85));
            JobFx.stab(level, tip, 0.32 + Kit.rand() * 0.18, j, 0.15, Opt.empty());
            if (i == 0 && tick % 2 == 0) JobFx.spear(level, V.add(from, dir, 2.2 + Kit.rand()), dir, 0.95, 0, 0.12, j, JobFx.hide1p(sp).with("fade", 0.6, "alpha", 0.6));
        }
        Vec3 look = sp.getViewVector(1F);
        Vec3 near = V.add(V.add(V.add(sp.getEyePosition(), look, 1.8 + Kit.rand() * 1.4), V.rightOf(V.flat(look)), (Kit.rand() - 0.5) * 1.2), JobFx.screenUp(look),
                -0.35 + Kit.rand() * 0.5);
        JobFx.stab(level, near, 0.3 + Kit.rand() * 0.12, j, 0.14, JobFx.only1p(sp).with("vel", RuneKit.bodyVel(sp)));
        if (tick % 4 == 0) JobFx.ringFacing(level, V.add(chest, f, 1.6 + Kit.rand() * 0.6), 0.75 + Kit.rand() * 0.3, j, V.scale(f, -1), 0.22, Opt.of("alpha", 0.8));
        if (tick % 6 == 3) JobFx.feathers(level, V.up(sp.position(), 1.3), 2, 0.6, 0.17, j, 0.9, JobFx.hide1p(sp));
        if (tick % 2 == 0) SkillFx.play(level, "item.spear.attack_miss", sp.position(), 1.25 + Kit.rand() * 0.5, 0.55);
    }

    /** 찌르기 폭풍에 맞은 적: 타격 그림 · 숫자 줄마다 (2틱 간격) 몸의 창끝 번쩍 (한 틱에 8명까지) */
    private static void stormHitFx(ServerPlayer sp, LivingEntity e, Jobs.Job j, int n) {
        if (!RuneKit.fxQuota("jobClawHit", 8)) return;
        JobFx.hit(e, j, 0.95);
        for (int i = 0; i < n; i++) {
            RuneKit.later(i * 2, () -> {
                if (e.isRemoved() || !(e.level() instanceof ServerLevel lv)) return;
                Vec3 eye = sp.getEyePosition();
                Vec3 at = Kit.bodyToward(e, eye, V.unit(V.sub(Kit.bodyPoint(e), eye)));
                JobFx.stab(lv, V.add(at, new Vec3((Kit.rand() - 0.5) * 0.4, (Kit.rand() - 0.5) * 0.5, (Kit.rand() - 0.5) * 0.4)), 0.42, j, 0.18, Opt.empty());
            });
        }
    }

    /**
     * 흐레스벨그의 발톱 마지막: 온 힘으로 꿰뚫음 — 앞 pierce 칸 · 폭 pierceWidth (충격이 PIERCE_SPEED 로 뻗어 닿는 때) + 앞 near 칸 (nearArc°) 의 적마다 한 번 final
     * (큰 숫자 · 밀쳐냄 — 보스 · 허수아비 제외). 그림: 큰 찌르기 (일섬 · 창끝 번쩍 · 1인칭 번쩍) · 큰 창 잔상 · 줄기 · 길 위로 퍼지는 세운 고리 (원뿔 충격) · 창끝의 터짐 ·
     * 깃털 · 발밑 바람 고리 · 튀는 돌 · 흙먼지 · 번쩍임 · 화면 흔들림
     */
    private static void pierce(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 from = chest(sp);
        double[] glow = JobKit.glow(j);
        double range = k.num("pierce", 8);
        double len = Math.max(2, RuneKit.rayLength(level, from, f, range));
        thrustFx(sp, j, f, len, 0, 1.8);
        JobFx.spear(level, V.add(from, f, 3.2), f, 1.5, 0, 0.3, j, JobFx.hide1p(sp).with("fade", 0.5, "alpha", 0.85));
        JobFx.dash(level, V.add(from, f, 0.8), V.add(from, f, len), j, 0.35, JobFx.hide1p(sp));
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.35);
        for (double s = 1.4; s < len; s += 1.5) {
            double ss = s;
            RuneKit.later((int) Math.floor(s / PIERCE_SPEED * 20), () -> JobFx.ringFacing(level, V.add(from, f, ss), 0.6 + ss * 0.09, j, V.scale(f, -1), 0.3,
                    Opt.empty()));
        }
        RuneKit.later((int) Math.floor(len / PIERCE_SPEED * 20), () -> {
            Vec3 tip = V.add(from, f, len);
            JobFx.burst(level, tip, 1.3, j, 0.35, Opt.empty());
            JobFx.feathers(level, tip, 6, 1.0, 0.2, j, 1.2, Opt.empty());
        });
        JobFx.wind(level, V.up(c, 0.08), 2.6, j, 0.45, Opt.empty());
        RuneKit.rockBurst(level, V.up(c, 0.2), 6, 3.5, 0.15);
        SkillFx.sparks(level, V.up(c, 0.2), DUST, 10, 4);
        Ult.flash(level, V.up(c, 1.0), glow, Opt.empty(), sp);
        RuneKit.shakeAround(level, c, 16, 0.4, 0.45);
        SkillFx.play(level, "item.trident.riptide_3", c, 0.9, 1.0);
        SkillFx.play(level, "entity.player.attack.crit", c, 0.7, 1.0);
        SkillFx.play(level, "item.trident.hit", c, 0.6, 1.0);
        SkillFx.play(level, "mace.heavy_smash_ground", c, 1.3, 0.5);
        // 피해: 꿰뚫는 줄 위는 충격이 닿는 때 · 앞 가까운 부채꼴은 지금 (적마다 한 번)
        double mult = k.num("final", 30);
        double knock = k.num("knock", 0.9);
        Set<UUID> done = new HashSet<>();
        int[] cnt = {0};
        for (Kit.LineHit h : Kit.lineHits(sp, from, f, range, k.num("pierceWidth", 2.6) / 2)) {
            LivingEntity e = h.e();
            if (!done.add(e.getUUID())) continue;
            JobKit.strike(sp, e, j, P, mult, false, true, life, (int) Math.floor(h.t() / PIERCE_SPEED * 20), d -> pierceHitFx(sp, e, j, c, f, knock, cnt));
        }
        for (LivingEntity e : front(sp, k.num("near", 5), k.num("nearArc", 60), 8)) {
            if (!done.add(e.getUUID())) continue;
            JobKit.strike(sp, e, j, P, mult, false, true, life, 0, d -> pierceHitFx(sp, e, j, c, f, knock, cnt));
        }
    }

    /** 꿰뚫음에 맞은 적: 밀쳐냄 (찌른 쪽과 바깥쪽 사이로 — 보스 · 허수아비 제외) · 타격 그림 · 터짐 · 창끝 번쩍 (8명까지) */
    private static void pierceHitFx(ServerPlayer sp, LivingEntity e, Jobs.Job j, Vec3 c, Vec3 f, double knock, int[] cnt) {
        if (JobKit.pushable(e)) RuneKit.knock(e, V.flat(V.add(f, V.flat(V.sub(e.position(), c)))), knock, 0.35); // 0.50.0 밀쳐냄 (유틸 −5%)
        if (cnt[0]++ >= 8 || !(e.level() instanceof ServerLevel lv)) return;
        JobFx.hit(e, j, 1.6);
        JobFx.burst(lv, Kit.bodyPoint(e), 1.0 + Kit.bodyPad(e) * 0.5, j, 0.32, Opt.empty());
        JobFx.stab(lv, Kit.bodyToward(e, sp.getEyePosition(), f), 0.9, j, 0.25, Opt.empty());
    }
}
