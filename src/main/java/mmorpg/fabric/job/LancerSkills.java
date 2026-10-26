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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.29.0 창술사 직업 스킬 (jobs.json lancer — 금빛 · 흰빛, 특성 「창끝」): 삼연창 · 장대 도약 · 선회창 · 창벽 · 천창유성.
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
                JobFx.ring(level, V.up(c, 0.1), r, j, ii % 2 == 1, 0.35, Opt.empty());
                if (lastSpin && spins > 1) {
                    RuneKit.later(3, () -> JobFx.ring(level, V.up(c, 0.12), r * 0.6, j, true, 0.3, Opt.of("alpha", 0.85)));
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
        mmorpg.fabric.DamageNumbers.showFace(sp, "§6§l창벽 §7— 정면 피해 −" + Math.round(k.num("guard", 0.3) * 100) + "%", 2.4, 0, -0.3);
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

    // ------------------------------------------------------------ 천창유성

    /** 부채꼴 (c 에서 수평 f 쪽 arc° · range 칸) 안의 적 (12명까지 — 하늘에서 떨어지는 창이라 벽은 따지지 않음) */
    private static List<LivingEntity> sector(ServerPlayer sp, Vec3 c, Vec3 f, double range, double arc) {
        double half = Math.toRadians(arc / 2);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, c, range, 6)) if (Kit.bodyInCone(e, c, f, half, 1.2)) out.add(e);
        out.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
        return out.size() > 12 ? new ArrayList<>(out.subList(0, 12)) : out;
    }

    /** 하늘에서 떨어지는 빛의 창 하나 (그림): 땅 (또는 몸) at 에 delay 틱 뒤 닿음 — 닿은 자리 번쩍 */
    private static void spearDrop(ServerLevel level, Jobs.Job j, Vec3 at, Vec3 back, int fall, int delay, boolean impact) {
        RuneKit.later(Math.max(0, delay - fall), () -> {
            Vec3 from = V.add(V.up(at, 9 + Kit.rand() * 3), back, 2.5 + Kit.rand() * 1.5);
            double len = V.len(V.sub(at, from));
            JobFx.spearFly(level, from, at, j, len / (fall / 20.0), 0.75 + Kit.rand() * 0.2, Opt.of("alpha", 0.95));
        });
        if (impact) {
            RuneKit.later(delay, () -> {
                if (!RuneKit.fxQuota("jobMeteorImpact", 10)) return;
                JobFx.stab(level, V.up(at, 0.25), 0.4, j, 0.2, Opt.empty());
                SkillFx.sparks(level, V.up(at, 0.15), DUST, 3, 2);
            });
        }
    }

    /**
     * 천창유성: 창을 하늘로 던짐 → 하늘에서 수백 개로 갈라져 (10틱) first 틱부터 gap 틱마다 (waves 번) 앞 range 칸 부채꼴 (arc°) 에 쏟아짐 — 물결마다 적마다
     * spears ÷ waves 자루 (spear × 자루 — 한 번에 · 숫자 그만큼, 빈 자리에도 꽂힘) → 마지막 물결 finalDelay 틱 뒤 진짜 창이 조준한 곳에 떨어져 폭발 (둘레 finalRadius 칸
     * final — 큰 숫자 · 띄움)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        double range = k.num("range", 18);
        double arc = k.num("arc", 90);
        int waves = Math.max(1, (int) k.num("waves", 4));
        int per = Math.max(1, (int) Math.round(k.num("spears", 20) / waves));
        int gap = Math.max(1, (int) k.num("gap", 10));
        int first = Math.max(12, (int) k.num("first", 20));
        int lastWave = first + (waves - 1) * gap;
        int finalAt = lastWave + (int) k.num("finalDelay", 15);
        double spear = k.num("spear", 2);
        double fr = k.num("finalRadius", 5);
        String life = RuneKit.lifeToken(sp);
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 aim = Kit.aimPoint(sp, range);
        Vec3 ground = new Vec3(aim.x, RuneKit.groundAt(level0, aim, sp.getY()), aim.z);
        Vec3 back = V.scale(f, -1);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Ult.sky(level0, c0, "rpg:ult_gold", finalAt + 25);
        RpgCombat.setSteadfast(sp, 12);
        // 던짐: 손에서 하늘로 솟는 빛의 창 · 1인칭 위로 긋는 베기 · 몸의 빛
        Vec3 hand = RuneKit.handOf(sp).hand();
        Vec3 sky = V.up(V.add(c0, f, 4), 15);
        JobFx.spearFly(level0, hand, sky, j, 45, 0.9, Opt.empty());
        JobFx.slash(sp, j, 2.0, 0.1, 1.5, 275.0, 0.22);
        JobFx.aura(sp, j, 3.0, 0.8, 0.9);
        JobFx.eyeRing(sp, j, 2.4, 1.1, 0.5);
        SkillFx.play(level0, "item.trident.throw", c0, 0.7, 1.0);
        SkillFx.play(level0, "item.trident.riptide_3", c0, 1.2, 0.7);
        // 하늘에서 갈라짐: 큰 터짐 · 아래로 쏟아지는 빛줄기 · 번쩍
        RuneKit.later(10, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            JobFx.burst(d, sky, 3.2, j, 0.5, Opt.empty());
            JobFx.ringFacing(d, sky, 2.6, j, V.DOWN, 0.45, Opt.empty());
            Ult.flash(d, sky, glow, Opt.of("radius", 8.0, "time", new double[] {0.02, 0.06, 0.4}), sp);
            SkillFx.play(d, "item.trident.thunder", c0, 1.6, 0.5);
            SkillFx.play(d, "firework.large_blast", c0, 0.8, 0.8);
        });
        double half = Math.toRadians(arc / 2);
        int fall = 6;
        for (int w = 0; w < waves; w++) {
            int at = first + w * gap;
            int ww = w;
            // 그림: 물결이 닿기 fall 틱 전부터 쏟아짐 (적마다 per 자루 + 빈 자리 10 자루)
            RuneKit.later(at - fall, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                for (LivingEntity e : sector(sp, c0, f, range, arc)) {
                    for (int s = 0; s < per; s++) {
                        Vec3 p = V.add(Kit.bodyPoint(e), new Vec3((Kit.rand() - 0.5) * 0.8, (Kit.rand() - 0.5) * 0.6, (Kit.rand() - 0.5) * 0.8));
                        spearDrop(d, j, p, back, fall, fall + s, false);
                    }
                }
                for (int s = 0; s < 10; s++) {
                    double a = (Kit.rand() * 2 - 1) * half;
                    double rr = 2.5 + Math.sqrt(Kit.rand()) * (range - 2.5);
                    Vec3 p = V.add(c0, V.turnY(f, Math.toDegrees(a)), rr);
                    Vec3 g = new Vec3(p.x, RuneKit.groundAt(d, p, c0.y), p.z);
                    spearDrop(d, j, g, back, fall, fall + (s % 4), true);
                }
                SkillFx.play(d, "item.trident.throw", V.add(c0, f, 6), 1.4 + ww * 0.1, 0.6);
            });
            JobKit.motionLater(sp, "lancer_ult:wave", at - fall - 1, life); // 0.47.0 빈손을 휘둘러 창비를 쏟아지게 하는 동작
            // 피해: 닿는 틱에 적마다 per 자루 (한 번에 · 숫자 per 줄)
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                for (LivingEntity e : sector(sp, c0, f, range, arc)) {
                    JobKit.strikeSplit(sp, e, j, P, spear * per, per, 1, false, false, life, 0, x -> {
                        if (RuneKit.fxQuota("jobMeteorHit", 8)) JobFx.hit(e, j, 1.0);
                    });
                }
                SkillFx.play(d, "item.trident.hit", V.add(c0, f, 6), 1.0 + ww * 0.08, 0.9);
                SkillFx.play(d, "item.trident.hit_ground", V.add(c0, f, 7), 0.9, 0.8);
            });
        }
        // 진짜 창: 큰 빛의 창이 조준한 곳으로 내리꽂힘 → 폭발 (0.47.0 동작: 빈손을 들었다가 꽂히는 순간 주먹을 쥐어 당김 — 창이 손으로 돌아옴)
        JobKit.motionLater(sp, "lancer_ult:final", finalAt - 7, life);
        RuneKit.later(finalAt - 7, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            JobFx.spearFly(d, V.add(V.up(ground, 16), back, 3), V.up(ground, 0.3), j, 50, 2.6, Opt.of("stick", 0.8));
            JobFx.ring(d, V.up(ground, 0.08), fr, j, false, 0.35, Opt.of("alpha", 0.7));
            SkillFx.play(d, "item.trident.riptide_3", ground, 0.7, 1.0);
        });
        RuneKit.later(finalAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            JobFx.smash(d, V.up(ground, 0.05), fr, j, 0.9, Opt.empty());
            JobFx.ring(d, V.up(ground, 0.1), fr, j, false, 0.5, Opt.empty());
            RuneKit.later(3, () -> JobFx.ring(d, V.up(ground, 0.12), fr * 0.6, j, true, 0.45, Opt.empty()));
            for (int i = 0; i < 3; i++) {
                int ii = i;
                RuneKit.later(i * 2, () -> JobFx.burst(d, V.up(ground, 0.8 + ii * 0.6), 1.8 + ii * 0.6, j, 0.4, Opt.empty()));
            }
            JobFx.stab(d, V.up(ground, 1.2), 2.4, j, 0.35, Opt.empty());
            RuneKit.godRays(sp, d, ground, fr * 0.7, 6, glow, core, Opt.of("h", 12.0, "life", 0.9, "alpha", 0.8, "skipNear", true));
            RuneKit.rockBurst(d, V.up(ground, 0.3), 12, 6.0, 0.2);
            SkillFx.sparks(d, V.up(ground, 0.6), glow, 18, 7);
            Ult.flash(d, ground, glow, Opt.empty(), sp);
            RuneKit.shakeAround(d, ground, 18, 0.45, 0.4);
            SkillFx.play(d, "random.explode", ground, 0.8, 1.0);
            SkillFx.play(d, "item.trident.thunder", ground, 1.2, 0.6);
            SkillFx.play(d, "mace.heavy_smash_ground", ground, 0.7, 1.0);
            for (LivingEntity e : Kit.around(sp, ground, fr, 3.0)) {
                JobKit.strike(sp, e, j, P, k.num("final", 18), false, true, life, 0, x -> {
                    if (JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), ground), 0.4, 0.5);
                    if (RuneKit.fxQuota("jobMeteorFinal", 6)) JobFx.hit(e, j, 1.6);
                });
            }
        });
        return true;
    }
}
