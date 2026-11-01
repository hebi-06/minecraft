package mmorpg.fabric.job;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * 0.51.0 2차 직업 — 마검사 반전 투르스(헥스 블레이드) 스킬 (jobs.json advance hexblade — 먹빛 · 호박빛, 특성 「뒤집힌 룬」 — JobTraits7: 칼 평타가 저주 룬을 새기고
 * 직업 스킬이 터뜨림 · 하나당 +12% · 다섯이면 1초 묶음): 가시 룬 베기 · 뒤집힌 걸음 · 우박의 룬 · 저주의 칼 · 재앙의 룬. 같은 마검 (칼 · 대검) · 같은 칸 역할로 새 스킬
 * 다섯, 그림은 거꾸로 선 가시 룬 ᚦ (rfx_jthorn) · 땅의 거대한 ᚦ (rfx_jthurs). 피해 스킬이 맞힌 적의 룬은 JobKit 이 터뜨림 (스킬마다 따로 셈하지 않음)
 */
final class HexbladeSkills {
    private HexbladeSkills() {}

    /** 뒤집힌 걸음 잔상 몸 (먹빛) */
    private static final double[] SHADE = {0.16, 0.06, 0.12};

    static void register() {
        JobSystem.register("hexblade_thorn", HexbladeSkills::thorn);
        JobSystem.register("hexblade_backstep", HexbladeSkills::backstep);
        JobSystem.register("hexblade_hail", HexbladeSkills::hail);
        JobSystem.register("hexblade_curse", HexbladeSkills::curse);
        JobSystem.register("hexblade_ult", HexbladeSkills::ult);
    }

    // ------------------------------------------------------------ 가시 룬 베기

    /**
     * 가시 룬 베기: 2틱에 아래에서 위로 올려 벰 (upper — 앞 reach 칸 · arc 도 부채꼴, 칼) → gap 틱 뒤 머리 위에서 내려 벰 (lower — 마법) · 내려 벤 적마다 가시 룬
     * thorns 개를 박음 (JobTraits7.addRunes — 피해가 들어가 앞 룬을 터뜨린 뒤라 새 룬은 남음). 그림: 눈앞 큰 베기 둘 · 맞은 적에 날아가 박히는 ᚦ 둘
     */
    static boolean thorn(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        String life = RuneKit.lifeToken(sp);
        int gap = Math.max(10, (int) k.num("gap", 10));
        double reach = k.num("reach", 4.0);
        double arc = k.num("arc", 110);
        int thorns = Math.max(0, (int) k.num("thorns", 2));
        JobMove.run(sp, JobMove.to(0, 3, JobMove.ahead(sp, 0.6, 0), JobMove.OUT));
        SkillFx.play(sp.level(), "item.firecharge.use", sp.position(), 1.6, 0.35);
        RuneKit.later(2, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 f = Kit.facingOf(sp);
            JobFx.slash(sp, j, 2.2, 0.35, 2.0, 200.0, 0.24);
            JobFx.wave(sp, j, V.up(V.add(sp.getEyePosition(), f, 2.6), -0.3), f, 6.0, 0.24, Opt.of("size", 1.5, "grow", 0.3, "spin", 200.0, "alpha", 0.8, "fade", 0.3));
            SkillFx.play(lv, "item.spear.attack_miss", sp.getEyePosition(), 1.0, 0.9);
            int[] cnt = {0};
            for (LivingEntity e : RuneKit.coneTargets(sp, reach, arc, 10)) {
                JobKit.strike(sp, e, j, P, k.num("upper", 3.0), false, false, life, 0, d -> {
                    if (cnt[0]++ < 8) JobFx.hit(e, j, 1.0);
                });
            }
        });
        JobKit.motionLater(sp, "hexblade_thorn:lower", 2 + gap - 3, life);
        RuneKit.later(2 + gap, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 f = Kit.facingOf(sp);
            JobFx.slash(sp, j, 2.2, 0.3, 2.1, 62.0, 0.26); // 왼쪽 위 → 오른쪽 아래 (내려 벰)
            SkillFx.play(lv, "item.spear.attack_miss", sp.getEyePosition(), 0.8, 1.0);
            SkillFx.play(lv, "block.amethyst_cluster.hit", sp.getEyePosition(), 0.7, 0.8);
            int[] cnt = {0};
            for (LivingEntity e : RuneKit.coneTargets(sp, reach, arc, 10)) {
                JobKit.strike(sp, e, j, P, k.num("lower", 5.5), true, false, life, 0, d -> {
                    if (cnt[0]++ < 8) JobFx.hit(e, j, 1.15);
                    if (thorns <= 0 || e.isRemoved() || !e.isAlive()) return;
                    // 가시 룬 박힘: 칼끝 쪽에서 날아가 몸에 꽂히는 ᚦ 둘 (2틱 뒤 룬 +thorns)
                    if (cnt[0] <= 6 && e.level() instanceof ServerLevel l2) {
                        Vec3 b = Kit.bodyPoint(e);
                        Vec3 side = V.rightOf(V.flat(V.sub(b, sp.position())));
                        int shown = Math.min(thorns, 3);
                        for (int t = 0; t < shown; t++) {
                            Vec3 at = V.up(V.add(b, side, (t - (shown - 1) / 2.0) * 0.45), 0.15 * t);
                            JobFx.thorn(l2, at, 0.42 + Kit.bodyPad(e) * 0.15, 0, 2, 0.5, false, Opt.of("vel", RuneKit.entVel(e), "spin", (t % 2 == 0 ? -1 : 1) * 14.0), j);
                        }
                        SkillFx.play(l2, "block.pointed_dripstone.land", b, 1.3, 0.7);
                    }
                    JobTraits7.addRunes(sp, e, j, thorns);
                });
            }
            JobKit.shake(sp, 6, 0.12, 0.15);
            JobFx.speed(sp, j, 0.2, 0.6, RuneKit.bodyVel(sp));
            JobFx.glint(lv, V.up(V.add(sp.position(), f, 1.2), 1.2), 0.45, j, 0.3, Opt.empty());
        });
        return true;
    }

    // ------------------------------------------------------------ 뒤집힌 걸음

    /**
     * 뒤집힌 걸음: 앞을 본 채 뒤로 dist 칸 순간 이동 (벽 앞까지 · 땅 높이) — 떠난 자리에 저주 룬 덫 (가운데 큰 ᚦ · 발밑 룬 띠가 조여듦) → armAt 틱에 터짐 (trap — 둘레
     * radius 칸, 마법) · 맞은 적 rootFor 틱 묶음 (보스는 둔화). 묶였거나 뒤가 막혀 못 가면 쓰지 않음
     */
    static boolean backstep(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 back = V.scale(f, -1);
        double dist = RuneKit.dashDistance(sp, back, k.num("dist", 6), 1.1, 1.8);
        Vec3 end = null;
        float yaw = sp.getYRot();
        float pitch = sp.getXRot();
        if (dist > 0.4) {
            Vec3 b = V.add(start, back, dist);
            Vec3 at = new Vec3(b.x, RuneKit.groundAt(level, b, start.y), b.z);
            if (Kit.moveTo(sp, at, yaw, pitch)) end = at;
            else {
                List<Kit.Spot> spots = Kit.dashSpots(level, start, back, dist);
                for (int i = spots.size() - 1; i >= 0 && end == null; i--) if (Kit.moveTo(sp, spots.get(i).p(), yaw, pitch)) end = spots.get(i).p();
            }
        }
        if (end == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 뒤가 막혔습니다", 30);
            return false;
        }
        String life = RuneKit.lifeToken(sp);
        int armAt = Math.max(4, (int) k.num("armAt", 10));
        double radius = k.num("radius", 3);
        Kit.noFall(sp, 30);
        // 떠난 자리: 앞을 본 잔상 (먹빛) · 검은 연기 · 길의 호박빛 번개 (뒤로)
        RuneKit.shadeClone(sp, level, start, f, 1, SHADE, JobFx.inner(j), Opt.of("life", 0.5, "fade", 0.6, "alpha", 0.85));
        JobFx.smoke(level, V.up(start, 0.9), 0.9, j, 0.6, Opt.of("vel", new Vec3(0, 0.4, 0)));
        RuneKit.zap(level, V.up(start, 0.6), V.up(V.add(end, f, 0.8), 0.6), JobFx.outer(j), JobFx.inner(j), 0.14, 0.3);
        JobFx.speed(sp, j, 0.3, 0.8);
        SkillFx.play(level, "mob.endermen.portal", start, 0.8, 0.7);
        SkillFx.play(level, "entity.wither.ambient", start, 1.8, 0.25);
        // 덫: 가운데 거꾸로 선 큰 ᚦ (박힘 → 맥동) · 발밑 룬 띠 · 조여드는 둥근 줄
        JobFx.thorn(level, V.up(start, 1.0), 0.8, 0, 2, 0.15, false, Opt.empty(), j);
        RuneKit.later(3, () -> JobFx.thorn(level, V.up(start, 1.0), 0.8, 2, 2, Math.max(0.1, (armAt - 3) / 20.0), true, Opt.empty(), j));
        JobFx.runeRing(level, V.up(start, 0.12), radius * 0.92, j, 10, Kit.rand() * 36, armAt / 20.0 + 0.05, Opt.of("alpha", 0.95));
        JobFx.ring(level, V.up(start, 0.06), radius, j, true, armAt / 20.0 + 0.1, Opt.of("alpha", 0.8));
        RuneKit.later(armAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            JobFx.thorn(d, V.up(start, 1.0), 1.1, 4, 2, 0.32, false, Opt.empty(), j);
            JobFx.smash(d, V.up(start, 0.05), radius, j, 0.45, Opt.empty());
            JobFx.burst(d, V.up(start, 1.0), radius * 0.55, j, 0.38, Opt.empty());
            JobFx.smoke(d, V.up(start, 0.5), 1.4, j, 0.7, Opt.of("vel", new Vec3(0, 0.6, 0)));
            SkillFx.sparks(d, V.up(start, 0.8), JobFx.inner(j), 10, 5);
            SkillFx.play(d, "random.explode", start, 1.3, 0.6);
            SkillFx.play(d, "block.chain.break", start, 0.6, 1.0);
            int rootFor = (int) k.num("rootFor", 20);
            int[] cnt = {0};
            for (LivingEntity e : Kit.around(sp, start, radius, 2.5)) {
                JobKit.strike(sp, e, j, P, k.num("trap", 5.1), true, false, life, 0, x -> {
                    boolean boss = mmorpg.fabric.boss.Bosses.isBossEntity(e);
                    RuneKit.effect(e, "slowness", rootFor, boss ? 1 : 10);
                    if (cnt[0]++ < 6 && e.level() instanceof ServerLevel l2) JobFx.runeRing(l2, V.up(e.position(), 0.1), 0.6 + Kit.bodyPad(e), j, 6, Kit.rand() * 60,
                            rootFor / 20.0, Opt.of("alpha", 0.9, "vel", RuneKit.entVel(e)));
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 우박의 룬

    /**
     * 우박의 룬: 칼을 앞 땅에 꽂아 (그 자리에 남음) 둘레 radius 칸에 검은 우박 룬 — first 틱부터 gap 틱마다 (hails) 둘레의 적마다 하늘에서 검은 우박 조각이 떨어져
     * 꽂힘 (마법) · 맞은 적 vulnFor 틱 받는 피해 + vuln (보스 + vulnBoss — JobTraitsMore 방어 깨짐) → shatterAt 틱에 칼을 뽑으면 룬 원이 깨짐 (shatter — 둘레 radius
     * 칸, 마법). 그림: 발밑 룬 띠 (먹빛) · 꽂힌 칼 · 떨어지는 조각 (적마다 · 빈 곳에도 몇) · 깨질 때 큰 ᚦ 번쩍 · 솟는 조각
     */
    static boolean hail(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        double r = k.num("radius", 5);
        List<Double> hails = SwordsmanSkills.numbers(k, "hails", List.of(2.2, 2.2, 2.2, 2.2));
        int first = Math.max(2, (int) k.num("first", 4));
        int gap = Math.max(10, (int) k.num("gap", 10));
        int shatterAt = Math.max(first + (hails.size() - 1) * gap + 10, (int) k.num("shatterAt", 44));
        String life = RuneKit.lifeToken(sp);
        // 꽂힌 칼 (몸 앞 0.7칸 — 칼끝 아래, 뽑을 때까지) · 발밑 룬 띠 (먹빛) · 둥근 줄
        Vec3 plant = V.add(c, f0, 0.7);
        Vec3 plantGround = new Vec3(plant.x, RuneKit.groundAt(level, plant, c.y), plant.z);
        JobFx3d.stuck(level, "phantom_sword", V.up(plantGround, 0.05), V.DOWN, JobFx3d.SWORD_TIP, 0.9, shatterAt / 20.0, j, Opt.of("fade", 0.15));
        for (int t = 0; t < shatterAt; t += 10) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                JobFx.runeRing(sp.level(), V.up(c, 0.12), r * 0.97, j, 12, -tt * 4.0, 0.55, Opt.of("alpha", 0.9));
            });
        }
        JobFx.ring(level, V.up(c, 0.06), r, j, false, 0.5, Opt.of("alpha", 0.8));
        JobFx.thorn(level, V.up(plantGround, 1.7), 0.55, 0, 2, 0.2, false, Opt.empty(), j);
        JobFx.eyeRing(sp, j, 2.4, 0.9, 0.45);
        SkillFx.play(level, "item.trident.hit_ground", plantGround, 0.7, 1.0);
        SkillFx.play(level, "entity.wither.ambient", c, 1.6, 0.3);
        double vuln = k.num("vuln", 0.1);
        double vulnBoss = k.num("vulnBoss", 0.05);
        int vulnFor = (int) k.num("vulnFor", 100);
        for (int i = 0; i < hails.size(); i++) {
            int ii = i;
            RuneKit.later(first + i * gap - 3, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                // 빈 곳에 떨어지는 조각 (분위기 — 원 안 아무 데나 셋)
                for (int s = 0; s < 3; s++) {
                    double a = Kit.rand() * Math.PI * 2;
                    double rr = Math.sqrt(Kit.rand()) * r * 0.9;
                    Vec3 g = new Vec3(c.x + Math.cos(a) * rr, c.y, c.z + Math.sin(a) * rr);
                    Vec3 gg = new Vec3(g.x, RuneKit.groundAt(lv, g, c.y), g.z);
                    hailShard(lv, V.up(gg, 0.1), j, 0.15);
                }
            });
            RuneKit.later(first + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                SkillFx.play(lv, "block.glass.break", c, 0.6 + 0.1 * ii, 0.7);
                int[] cnt = {0};
                for (LivingEntity e : Kit.around(sp, c, r, 3.5)) {
                    if (cnt[0] < 8) hailShard(lv, Kit.bodyPoint(e), j, 0.12);
                    cnt[0]++;
                    JobKit.strike(sp, e, j, P, hails.get(ii), true, false, life, 0, d -> {
                        JobTraitsMore.applyBreak(e, JobKit.isBoss(e) ? vulnBoss : vuln, vulnFor);
                        if (RuneKit.fxQuota("jobHail", 6)) JobFx.thorn(lv, V.up(Kit.bodyPoint(e), 0.4), 0.28 + Kit.bodyPad(e) * 0.15, 4, 2, 0.25, false,
                                Opt.of("vel", RuneKit.entVel(e)), j);
                    });
                }
            });
        }
        JobKit.motionLater(sp, "hexblade_hail:pull", shatterAt - 4, life);
        RuneKit.later(shatterAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            // 칼을 뽑음 → 룬 원이 깨짐: 큰 ᚦ 번쩍 · 땅의 터짐 · 솟는 조각 · 먹빛 연기
            JobFx.thorn(lv, V.up(plantGround, 1.6), 1.3, 4, 2, 0.36, false, Opt.empty(), j);
            JobFx.smash(lv, V.up(c, 0.05), r, j, 0.5, Opt.empty());
            JobFx.runeRing(lv, V.up(c, 0.16), r, j, 14, Kit.rand() * 30, 0.45, Opt.of("alpha", 1.0));
            JobFx.smoke(lv, V.up(c, 0.4), 2.0, j, 0.8, Opt.of("vel", new Vec3(0, 0.7, 0), "alpha", 0.8));
            for (int s = 0; s < 6; s++) {
                double a = Math.toRadians(s * 60 + Kit.rand() * 20);
                Vec3 g = new Vec3(c.x + Math.cos(a) * r * 0.6, c.y + 0.2, c.z + Math.sin(a) * r * 0.6);
                JobFx.spell(lv, 1, g, V.up(g, 2.4 + Kit.rand()), 0.35, 0.22, JobFx.outer(j), JobFx.inner(j));
            }
            SkillFx.sparks(lv, V.up(c, 0.6), JobFx.inner(j), 16, 6);
            SkillFx.play(lv, "block.glass.break", c, 0.5, 1.0);
            SkillFx.play(lv, "random.explode", c, 1.1, 0.7);
            JobKit.shake(sp, 10, 0.25, 0.3);
            for (LivingEntity e : Kit.around(sp, c, r, 3.5)) {
                JobKit.strike(sp, e, j, P, k.num("shatter", 6.5), true, false, life, 0, d -> {
                    if (RuneKit.fxQuota("jobHailEnd", 6)) JobFx.hit(e, j, 1.2);
                });
            }
        });
        return true;
    }

    /** 검은 우박 조각 하나: at 위 5칸에서 떨어져 꽂힘 (얼음 창 그림을 먹빛 · 호박빛으로) */
    private static void hailShard(ServerLevel level, Vec3 at, Jobs.Job j, double life) {
        Vec3 from = V.add(V.up(at, 5.0), new Vec3((Kit.rand() - 0.5) * 1.2, 0, (Kit.rand() - 0.5) * 1.2));
        JobFx.spell(level, 1, from, at, 0.32, life, JobFx.outer(j), JobFx.inner(j));
    }

    // ------------------------------------------------------------ 저주의 칼

    /**
     * 저주의 칼: dur 틱 태세 — 칼 평타에 저주 불꽃 (위력 × flame — JobTraits7) · 저주 룬을 runes 개씩 새김. 그림: 칼을 쓸어 먹빛 불꽃이 붙음 (손의 연기 · 불티) · 칼날 위
     * 번쩍이는 ᚦ · 발밑 먹빛 고리 · 몸의 기운 · 위더 소리
     */
    static boolean curse(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) k.num("dur", 200);
        JobTraits.startStance(sp, k, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        String life = RuneKit.lifeToken(sp);
        JobFx.ring(level, V.up(c, 0.06), 2.2, j, true, 0.5, Opt.of("alpha", 0.85));
        JobFx.runeRing(level, V.up(c, 0.12), 1.4, j, 6, 0, 0.8, Opt.of("alpha", 0.9));
        for (int t = 0; t < 12; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                RuneKit.Hand h = RuneKit.handOf(sp);
                double q = (tt + 2) / 12.0;
                Vec3 blade = V.up(V.add(h.hand(), h.f(), 0.2 + 0.4 * q), 0.25 + 0.3 * q);
                JobFx.smoke(sp.level(), blade, 0.25 + 0.15 * q, j, 0.4, Opt.of("vel", V.up(RuneKit.bodyVel(sp), 0.9), "alpha", 0.85));
                SkillFx.fx(sp.level(), "rpg:arrow_spark", blade, Opt.of("dir", V.UP, "speed", 2.0, "size", 0.14, "grow", 0.8, "life", 0.4, "color", JobFx.inner(j), "alpha",
                        1.0));
            });
        }
        RuneKit.later(10, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            RuneKit.Hand h = RuneKit.handOf(sp);
            JobFx.thorn(lv, V.up(V.add(h.hand(), h.f(), 0.7), 0.6), 0.45, 0, 4, 0.5, false, Opt.of("vel", RuneKit.bodyVel(sp)), j);
            JobFx.aura(sp, j, 2.6, 0.8, 0.9);
            SkillFx.play(lv, "item.firecharge.use", sp.position(), 0.6, 0.8);
            Ult.flash(lv, sp.position(), JobFx.inner(j), Opt.of("radius", 4.0, "time", new double[] {0.03, 0.05, 0.3}), sp);
        });
        JobFx.eyeRing(sp, j, 2.4, 0.95, 0.5);
        SkillFx.play(level, "entity.wither.ambient", c, 1.4, 0.35);
        SkillFx.play(level, "mob.evocation_illager.prepare_attack", c, 0.8, 0.6);
        return true;
    }

    // ------------------------------------------------------------ 재앙의 룬

    /**
     * 재앙의 룬: 먹빛 하늘 → 앞 땅에 거대한 ᚦ 를 세 획으로 새김 (first 틱부터 gap 틱마다 — 칼을 휘두르면 땅을 긁는 빛이 획을 그음, strokes — 획 위 (폭 width) 의 적) ·
     * 줄기 = 앞 1.5 ~ 1.5 + length 칸, 가시 = 줄기 0.3 ~ 0.7 에서 오른쪽 thorn 칸 → cleaveAt 틱 전에 뛰어올라 룬을 세로로 가름 (cleave — 줄기 위) → eruptAt 틱에
     * 안의 저주가 한꺼번에 터짐 (erupt — 룬 가운데 둘레 eruptRadius 칸 · 큰 숫자, 저주 룬은 JobKit 이 모두 터뜨림). 쓴 자리 · 방향으로 새김
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<Double> strokes = SwordsmanSkills.numbers(k, "strokes", List.of(10.0, 10.0, 10.0));
        int first = Math.max(4, (int) k.num("first", 6));
        int gap = Math.max(10, (int) k.num("gap", 10));
        int cleaveAt = Math.max(first + (strokes.size() - 1) * gap + 10, (int) k.num("cleaveAt", 46));
        int eruptAt = Math.max(cleaveAt + 10, (int) k.num("eruptAt", 56));
        double length = k.num("length", 9);
        double thornLen = k.num("thorn", 3.0);
        double half = k.num("width", 2.0) / 2;
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        Vec3 r0 = V.rightOf(f0);
        double gy = RuneKit.groundAt(level0, c0, c0.y);
        // 룬 꼴 (땅 높이): 줄기 S0 → S1, 가시 A (줄기 0.3) → B (줄기 0.5 + 오른쪽 thorn) → C (줄기 0.7)
        Vec3 s0 = new Vec3(c0.x, gy, c0.z).add(f0.scale(1.5));
        Vec3 s1 = V.add(s0, f0, length);
        Vec3 a = V.add(s0, f0, length * 0.3);
        Vec3 cc = V.add(s0, f0, length * 0.7);
        Vec3 b = V.add(V.add(s0, f0, length * 0.5), r0, thornLen);
        Vec3 mid = V.add(s0, f0, length * 0.5);
        Vec3 center = V.add(mid, r0, thornLen * 0.25);
        Vec3[][] segs = {{s0, s1}, {cc, b}, {b, a}};
        double size = length / 1.6;
        Ult.sky(level0, c0, "rpg:ult_hex", eruptAt + 30);
        RpgCombat.setSteadfast(sp, eruptAt + 10);
        Kit.noFall(sp, eruptAt + 40);
        JobFx.ring(level0, V.up(c0, 0.06), 3.0, j, true, 0.6, Opt.of("alpha", 0.85));
        JobFx.eyeRing(sp, j, 2.6, 1.1, 0.5);
        JobFx.smoke(level0, V.up(c0, 0.5), 1.6, j, 0.9, Opt.of("vel", new Vec3(0, 0.5, 0), "alpha", 0.8));
        SkillFx.play(level0, "entity.wither.spawn", c0, 1.4, 0.45);
        SkillFx.play(level0, "mob.evocation_illager.prepare_attack", c0, 0.7, 1.0);
        // 획 셋: 칼을 휘둘러 땅을 긁는 빛이 획을 따라 4틱에 그어지고 (불티 · 갈라짐) → 그 틱에 획 위의 적 · 땅의 룬 그림이 한 획씩 늘어남
        for (int i = 0; i < strokes.size() && i < 3; i++) {
            int ii = i;
            int at = first + i * gap;
            JobKit.motionLater(sp, "hexblade_ult:stroke" + i, at - 3, life);
            for (int s = 0; s < 4; s++) {
                int ss = s;
                RuneKit.later(at - 3 + s, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    ServerLevel lv = sp.level();
                    Vec3 p0 = V.lerp(segs[ii][0], segs[ii][1], ss / 4.0);
                    Vec3 p1 = V.lerp(segs[ii][0], segs[ii][1], (ss + 1) / 4.0);
                    JobFx.line(lv, V.up(p0, 0.12), V.up(p1, 0.12), j, 0.5, Opt.empty());
                    SkillFx.sparks(lv, V.up(p1, 0.2), JobFx.inner(j), 3, 2.5);
                    if (ss == 0) SkillFx.play(lv, "item.trident.hit_ground", p0, 0.8 + ii * 0.1, 0.8);
                });
            }
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                JobFx.thurs(lv, V.up(center, 0.06), f0, size, ii, 1, (ii < 2 ? gap : 8) / 20.0 + 0.05, false, Opt.of("alpha", 0.95), j); // 다 새긴 뒤 8틱부터 맥동
                JobFx.slash(sp, j, 2.4, 0.6, 2.4, ii == 0 ? 80.0 : ii == 1 ? 140.0 : 40.0, 0.24);
                SkillFx.play(lv, "item.spear.attack_miss", sp.getEyePosition(), 0.9, 1.0);
                SkillFx.play(lv, "block.basalt.break", segs[ii][1], 0.6, 1.0);
                double len = V.hdist(segs[ii][0], segs[ii][1]); // V.flat 은 단위 방향 — 획 길이는 hdist (CI 41306605712: 획이 1칸으로 재어져 줄기 위 적을 못 벴음)
                if (len < 0.3) return;
                int[] cnt = {0};
                for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(segs[ii][0], 1.0), V.flat(V.sub(segs[ii][1], segs[ii][0])), len, half)) {
                    LivingEntity e = h.e();
                    JobKit.strike(sp, e, j, P, strokes.get(ii), true, false, life, 0, x -> {
                        if (cnt[0]++ < 8) JobFx.hit(e, j, 1.2);
                    });
                }
            });
        }
        // 다 새긴 룬이 맥동 (마지막 획 뒤 ~ 가르기 전)
        RuneKit.later(first + 2 * gap + 8, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.thurs(sp.level(), V.up(center, 0.07), f0, size, 3, 2, Math.max(0.1, (cleaveAt - first - 2 * gap - 8) / 20.0), true,
                    Opt.of("alpha", 0.95), j);
        });
        // 가르기: 뛰어올라 (rise 칸 · 8틱) 버팀 → 줄기를 따라 내려 가름 (cleaveAt 에 땅)
        int up0 = cleaveAt - 12;
        RuneKit.later(up0, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            double ground = sp.getY();
            JobMove.run(sp, JobMove.to(0, 8, JobMove.up(sp, k.num("rise", 3)), JobMove.OUT), JobMove.hold(8, 9), JobMove.to(9, 12, JobMove.down(sp, ground), JobMove.IN));
            SkillFx.play(sp.level(), "mob.breeze.jump", sp.position(), 0.7, 0.9);
        });
        JobKit.motionLater(sp, "hexblade_ult:cleave", up0, life);
        RuneKit.later(cleaveAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            JobFx.thurs(lv, V.up(center, 0.08), f0, size, 5, 1, (eruptAt - cleaveAt) / 20.0 + 0.05, false, Opt.of("alpha", 1.0), j);
            JobFx.slash(sp, j, 3.0, 0.3, 3.2, 90.0, 0.3);
            Vec3 eye = sp.getEyePosition();
            SkillFx.fx(lv, "rpg:light_cut", V.up(V.add(eye, f0, 3.4), -0.4), Opt.of("dir", V.UP, "size", 4.4, "thick", 0.3, "life", 0.4, "color", JobFx.outer(j), "alpha",
                    1.0));
            SkillFx.fx(lv, "rpg:light_cut", V.up(V.add(eye, f0, 3.35), -0.4), Opt.of("dir", V.UP, "size", 4.1, "thick", 0.1, "life", 0.36, "color", JobFx.inner(j),
                    "alpha", 1.0));
            JobFx.line(lv, V.up(s0, 0.15), V.up(s1, 0.15), j, 0.6, Opt.empty());
            RuneKit.rockBurst(lv, V.up(mid, 0.3), 10, 4.0, 0.2);
            RuneKit.shakeAround(lv, mid, 14, 0.3, 0.3);
            SkillFx.play(lv, "item.trident.thunder", mid, 0.9, 0.8);
            SkillFx.play(lv, "random.explode", mid, 1.4, 0.6);
            int[] cnt = {0};
            for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(s0, 1.0), f0, length, half + 0.2)) {
                LivingEntity e = h.e();
                JobKit.strike(sp, e, j, P, k.num("cleave", 12), false, false, life, 0, x -> {
                    if (cnt[0]++ < 8) JobFx.hit(e, j, 1.3);
                });
            }
        });
        // 터지기 직전 (6틱): 저주 룬이 남은 적의 몸에서 거꾸로 선 ᚦ 가 떠오름 (터질 것이 보이게)
        RuneKit.later(eruptAt - 6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            int n = 0;
            for (LivingEntity e : Kit.around(sp, center, k.num("eruptRadius", 7), 4.0)) {
                if (JobTraits7.runes(sp, e) <= 0 || n++ >= 8) continue;
                JobFx.thorn(lv, V.up(Kit.bodyPoint(e), 0.6), 0.4 + Kit.bodyPad(e) * 0.2, 0, 4, 0.32, false, Opt.of("vel", RuneKit.entVel(e)), j);
            }
            SkillFx.play(lv, "block.respawn_anchor.charge", center, 0.6, 0.9);
        });
        RuneKit.later(eruptAt, () -> {
            if (RuneKit.sameLife(sp, life)) erupt(sp, j, k, P, center, f0, size, life);
        });
        return true;
    }

    /** 안의 저주가 한꺼번에 터짐: 룬 그림 6 ~ 7 · 솟는 먹빛 · 호박빛 기둥 · 땅의 터짐 · 둘레 eruptRadius 칸 (erupt — 마법 · 큰 숫자, 저주 룬은 JobKit 이 터뜨림) */
    private static void erupt(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 center, Vec3 f0, double size, String life) {
        ServerLevel level = sp.level();
        double r = k.num("eruptRadius", 7);
        JobFx.thurs(level, V.up(center, 0.09), f0, size, 6, 2, 0.7, false, Opt.of("alpha", 1.0), j);
        JobFx.smash(level, V.up(center, 0.05), r, j, 0.6, Opt.empty());
        RuneKit.godRays(sp, level, center, r * 0.6, 7, JobFx.outer(j), JobFx.inner(j), Opt.of("h", 14.0, "life", 0.9, "alpha", 0.85, "skipNear", true));
        JobFx.smoke(level, V.up(center, 0.6), 3.0, j, 1.0, Opt.of("vel", new Vec3(0, 1.0, 0), "alpha", 0.85));
        RuneKit.rockBurst(level, V.up(center, 0.3), 14, 6.0, 0.22);
        SkillFx.sparks(level, V.up(center, 0.5), JobFx.inner(j), 22, 7);
        Ult.flash(level, center, JobFx.inner(j), Opt.empty(), sp);
        RuneKit.shakeAround(level, center, 16, 0.5, 0.45);
        SkillFx.play(level, "random.explode", center, 0.7, 1.0);
        SkillFx.play(level, "entity.wither.break_block", center, 0.8, 0.8);
        SkillFx.play(level, "item.trident.thunder", center, 0.8, 0.9);
        Map<UUID, LivingEntity> hit = new LinkedHashMap<>();
        for (LivingEntity e : Kit.around(sp, center, r, 4.0)) hit.putIfAbsent(e.getUUID(), e);
        Set<UUID> fx = new HashSet<>();
        for (LivingEntity e : hit.values()) {
            JobKit.strike(sp, e, j, P, k.num("erupt", 26), true, true, life, 0, d -> {
                if (fx.add(e.getUUID()) && fx.size() <= 8) JobFx.hit(e, j, 1.5);
            });
        }
    }
}
