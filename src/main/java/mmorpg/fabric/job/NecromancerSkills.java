package mmorpg.fabric.job;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 — 매지션 반전 네크로맨서 스킬 (jobs.json advance.necromancer — 독초록 · 뼈색, 특성 「망자의 군대」 — JobTraits8 · NecroCrew).
 * 재설계안 2판 (2026-11-06 사용자 "다음 작업" — 롤 요릭 · 카서스 · 애니 참고): 그레이브 콜 (요릭 Q — 거대한 뼈 창이 내리꽂힘 · 표식, 무덤이 둘 넘으면 다시 눌러 무덤마다
 * 망자) · 그레이브야드 (요릭 W — 묘비 고리가 솟아 가둠) · 소울 미스트 (요릭 E — 혼령 안개 구슬 · 망자가 몰려드는 표식) · 블라이트 (카서스 E — 나를 따라가는 썩은 땅) ·
 * 팬텀 갤리온 (요릭 R · 애니 R — 망자의 배가 땅을 가르고 나아가 거대한 망자 선장을 내려놓음). 모두 마법 피해. 연출 = 2D 그림 (JobFx — 뼈 창 rfx_jbonespear · 해골
 * rfx_jskull · 갈라진 땅 rfx_jrift · 무덤 rfx_jgrave, 1차 사신의 혼불) + 망자 · 선장은 진짜 몹 (rpg:naglfar_crew) + 배는 입체 모델 (naglfar). 화면 흔들림 없음
 * (사용자 "멀미나")
 */
final class NecromancerSkills {
    private NecromancerSkills() {}

    private static final double[] FIRE = NecroCrew.FIRE;
    private static final double[] DEEP = NecroCrew.DEEP;
    private static final double[] BONE = {0.92, 0.88, 0.74};

    static void register() {
        JobSystem.register("necromancer_call", NecromancerSkills::call);
        JobSystem.register("necromancer_yard", NecromancerSkills::yard);
        JobSystem.register("necromancer_mist", NecromancerSkills::mist);
        JobSystem.register("necromancer_blight", NecromancerSkills::blight);
        JobSystem.register("necromancer_ult", NecromancerSkills::ult);
    }

    static Vec3 wandTip(ServerPlayer sp) {
        return MageSkills.wandTip(sp);
    }

    /** 녹색 무덤불 한 장 (밑동이 at) */
    static void ghostfire(ServerLevel level, Vec3 at, double size, double life, double alpha) {
        SkillFx.fx(level, "rpg:tyr_ghostfire", at, Opt.of("size", size, "life", life, "color", FIRE, "alpha", alpha));
    }

    /** 녹색으로 갈라진 땅 (rpg:ground_crack) */
    static void crack(ServerLevel level, Vec3 at, double size, double life) {
        SkillFx.fx(level, "rpg:ground_crack", V.up(at, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", size, "life", life, "color", DEEP, "alpha", 0.9));
    }

    /** 조준한 곳의 땅 (겨눈 적이 있으면 그 발밑 — range 칸) */
    private static Vec3 groundAim(ServerPlayer sp, double range) {
        LivingEntity t = Kit.pickTarget(sp, range, 10);
        if (t != null) return t.position();
        Vec3 a = Kit.aimPoint(sp, range);
        return new Vec3(a.x, RuneKit.groundAt(sp.level(), V.up(a, 1.0), sp.getY()), a.z);
    }

    // ------------------------------------------------------------ 그레이브 콜

    /**
     * 그레이브 콜: 겨눈 적 (range 칸 · 없으면 쓰지 않음) 위 하늘에 거대한 뼈 창 (rfx_jbonespear — 창끝이 아래) 이 맺혀 smashAt 틱에 내리꽂힘 (smash · 갈라진 땅 · 녹색 혼불 기둥 ·
     * 비명 해골) — 그 적에 markFor 틱 표식 (그 안에 쓰러지면 무덤 없이 바로 망자: JobTraits8.mark). 쓸 때 내 둘레 graveRange 칸에 무덤이 graves 개 넘게 있으면 window 틱
     * 안에 다시 눌러 (gap 틱 뒤부터) 그 무덤마다 망자 선원이 일어남 (최대 망자 수까지 — 가까운 무덤부터, 동작 :raise)
     */
    static boolean call(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (JobRecast.stage(sp, k.id()) >= 1) return raiseGraves(sp, j, k);
        LivingEntity t = Kit.pickTarget(sp, k.num("range", 16), 12);
        if (t == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        int smashAt = Math.max(3, (int) k.num("smashAt", 6));
        String life = RuneKit.lifeToken(sp);
        // 영상 1: 머리 위 6.5칸은 3인칭 화면 밖 · 뼈 창이 작았음 → 4칸 · 크게 / 영상 2: 2.7칸 앞 과녁이면 4칸도 맺히는 동안 화면 위 끝 밖 → 3칸 · 해골을 창 끝 가까이
        double lift = 3.0 + t.getBbHeight();
        Vec3 top = V.up(t.position(), lift);
        // 하늘에 녹색 혼불이 모여 뼈 창이 맺힘 (0 ~ smashAt − 3) → 내리꽂힘 (smashAt − 3 ~ smashAt)
        JobFx.skull(level0, V.up(top, 0.8), 0.75, 0, 3, (smashAt - 2) / 20.0, false, Opt.empty(), j);
        JobFx.boneSpearAt(level0, V.up(top, -1.0), V.DOWN, 2.3, (smashAt - 3) / 20.0 + 0.05, j, Opt.of("alpha", 0.95));
        SkillFx.sparks(level0, top, FIRE, 6, 3);
        SkillFx.play(level0, "entity.zombie_villager.cure", top, 1.6, 0.5);
        SkillFx.play(level0, "particle.soul_escape", sp.position(), 0.8, 1.0);
        RuneKit.later(smashAt - 3, () -> {
            if (!RuneKit.sameLife(sp, life) || t.isRemoved()) return;
            ServerLevel d = sp.level();
            Vec3 g = t.position();
            JobFx.boneSpear(d, V.up(g, lift), V.up(g, 0.4), j, (lift - 0.4) / 0.15, 2.3, Opt.empty());
            SkillFx.play(d, "entity.arrow.shoot", g, 0.5, 1.0);
        });
        RuneKit.later(smashAt - 1, () -> {
            if (RuneKit.sameLife(sp, life) && !t.isRemoved() && t.isAlive()) JobTraits8.mark(sp, t, (int) k.num("markFor", 60), false);
        });
        RuneKit.later(smashAt, () -> {
            if (!RuneKit.sameLife(sp, life) || t.isRemoved()) return;
            ServerLevel d = sp.level();
            Vec3 g = t.position();
            crack(d, g, 1.8, 1.2);
            JobFx.rift(d, g, Kit.facingOf(sp), 0.55, 0, 3, 0.4, false, Opt.empty(), j);
            ghostfire(d, g, 2.6, 0.55, 0.95);
            JobFx.skull(d, V.up(g, t.getBbHeight() + 0.9), 0.7, 4, 2, 0.4, false, Opt.empty(), j);
            SkillFx.shock(d, V.up(g, 0.1), FIRE, 1.6, 0.35);
            SkillFx.sparks(d, V.up(g, 0.4), BONE, 10, 4);
            SkillFx.play(d, "mace.heavy_smash_ground", g, 0.8, 1.0);
            SkillFx.play(d, "entity.skeleton.hurt", g, 0.6, 1.0);
        });
        JobKit.strike(sp, t, j, P, k.num("smash", 7), true, false, life, smashAt, x -> {
            if (RuneKit.fxQuota("jobGraveCall", 6)) JobFx.hit(t, j, 1.2);
        });
        // 무덤이 graves 개 넘게 있으면 다시 누를 창
        List<JobTraits8.Grave> gs = JobTraits8.gravesNear(level0, sp.position(), k.num("graveRange", 14));
        if (gs.size() >= (int) k.num("graves", 2)) {
            JobRecast.open(sp, k, 1, (int) k.num("window", 60), (int) k.num("gap", 8), null);
            Notify.notify(sp, "§a" + k.name() + " §7— 다시 눌러 무덤 " + gs.size() + "개에서 망자를 일으킴", 40);
            for (JobTraits8.Grave g : gs) {
                JobFx.soul(level0, V.up(g.at(), 1.2), 0.28, j, 0.6, Opt.of("dir", V.UP, "speed", 0.6));
                SkillFx.sparks(level0, V.up(g.at(), 0.8), FIRE, 3, 2);
            }
        }
        return true;
    }

    /** 그레이브 콜 다시 누름: 내 둘레 graveRange 칸의 무덤 (가까운 순 · 최대 망자 수까지) 마다 망자 선원이 솟음 — 완드 끝에서 무덤으로 녹색 혼불 줄기 · 묘비가 무너짐 */
    private static boolean raiseGraves(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        JobRecast.close(sp, k.id());
        JobRecast.motion(sp, k.id() + ":raise");
        ServerLevel level = sp.level();
        List<JobTraits8.Grave> gs = JobTraits8.gravesNear(level, sp.position(), k.num("graveRange", 14));
        int max = Math.max(1, j.trait() == null ? 4 : j.trait().max());
        int crewFor = (int) JobTraits8.trait(j, "crewFor", 400);
        String life = RuneKit.lifeToken(sp);
        SkillFx.play(level, "entity.zombie_villager.converted", sp.position(), 0.8, 1.0);
        SkillFx.play(level, "particle.soul_escape", sp.position(), 0.6, 1.2);
        for (int i = 0; i < gs.size() && i < max; i++) {
            JobTraits8.Grave g = gs.get(i);
            JobTraits8.takeGrave(g);
            int ii = i;
            RuneKit.streak(level, wandTip(sp), V.up(g.at(), 1.0), FIRE, 0.12, 0.35, 0.9, 0.05 * i);
            RuneKit.later(2 + i * 2, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                JobFx.grave(d, g.at(), 0.62, 6, 2, 0.45, false, Opt.of("fade", 0.3), j);
                NecroCrew.raise(sp, j, g.at(), g.yaw(), crewFor, NecroCrew.TRAIT);
                if (ii == 0) SkillFx.play(d, "entity.zombie.ambient", g.at(), 0.6, 1.0);
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 그레이브야드

    /**
     * 그레이브야드: 조준한 곳 (range 칸 — 겨눈 적의 발밑) 둘레 radius 칸에 녹색 고리 · 갈라진 땅 → riseAt 틱에 묘비 stones 개가 둥글게 솟음 (rfx_jgrave — 안의 적 rise) →
     * dur 틱 가둠 (안의 적은 나가지 못함 · 느려짐 — 보스 제외, 망자가 먼저 노림: JobTraits8.addYard) → 묘비가 무너짐 (crumble — 안의 적)
     */
    static boolean yard(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 c = groundAim(sp, k.num("range", 14));
        double R = k.num("radius", 3.5);
        int riseAt = (int) k.num("riseAt", 6);
        int dur = (int) k.num("dur", 60);
        int stones = Math.max(4, (int) k.num("stones", 10));
        int endAt = riseAt + dur;
        String life = RuneKit.lifeToken(sp);
        double rot = Kit.rand() * Math.PI * 2;
        Vec3[] spots = new Vec3[stones];
        for (int i = 0; i < stones; i++) {
            double a = rot + i * Math.PI * 2 / stones;
            Vec3 p = V.add(c, new Vec3(Math.cos(a), 0, Math.sin(a)), R);
            spots[i] = new Vec3(p.x, RuneKit.groundAt(level0, V.up(p, 1.5), c.y), p.z);
        }
        // 0 ~ riseAt: 땅이 갈라지며 녹색 빛 · 묘비 자리에 혼불
        crack(level0, c, R * 0.9, (riseAt + 6) / 20.0);
        SkillFx.shock(level0, V.up(c, 0.08), FIRE, R, riseAt / 20.0 + 0.1);
        for (int i = 0; i < stones; i++) ghostfire(level0, spots[i], 0.9, riseAt / 20.0 + 0.1, 0.8);
        JobFx.skull(level0, V.up(c, 2.6), 0.6, 0, 3, riseAt / 20.0 + 0.15, false, Opt.empty(), j);
        SkillFx.play(level0, "block.rooted_dirt.break", c, 0.6, 1.0);
        SkillFx.play(level0, "ambient.soul_sand_valley.mood", c, 1.0, 0.8);
        // riseAt: 묘비가 솟음 (0 ~ 1, 5틱) → 서 있음 (2 ~ 5 이어 돌림) → endAt 무너짐 (6 ~ 7)
        RuneKit.later(riseAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            for (int i = 0; i < stones; i++) {
                JobFx.grave(d, spots[i], 0.72, 0, 2, 0.3, false, Opt.of("fade", 0.0), j);
                SkillFx.sparks(d, V.up(spots[i], 0.3), BONE, 3, 2.5);
            }
            SkillFx.play(d, "block.deepslate_bricks.place", c, 0.6, 1.2);
            SkillFx.play(d, "entity.wither_skeleton.ambient", c, 0.7, 0.9);
            JobTraits8.addYard(sp, c, R, Tasks.now() + dur);
            for (LivingEntity e : Kit.around(sp, c, R, 3)) {
                JobKit.strike(sp, e, j, P, k.num("rise", 2.1), true, false, life, 0, x -> {
                    if (RuneKit.fxQuota("jobYardRise", 6)) JobFx.hit(e, j, 1.0);
                });
            }
        });
        RuneKit.later(riseAt + 5, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            for (int i = 0; i < stones; i++) {
                JobFx.grave(d, spots[i], 0.72, 2, 4, (endAt - riseAt - 4) / 20.0, true, Opt.of("fade", 0.0, "phase", (double) (i % 4)), j);
            }
        });
        // 가둠 · 안개 (riseAt ~ endAt)
        confine(sp, c, R, riseAt, endAt, life);
        for (int t = riseAt + 8; t < endAt; t += 10) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                JobFx.smoke(d, V.up(c, 0.5), R * 0.8, DEEP, FIRE, 0.7, Opt.of("alpha", 0.45));
                int i = (int) (Kit.rand() * stones);
                JobFx.soul(d, V.up(spots[i], 1.9), 0.22, j, 0.6, Opt.of("dir", V.UP, "speed", 0.7));
            });
        }
        RuneKit.later(endAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            for (int i = 0; i < stones; i++) {
                JobFx.grave(d, spots[i], 0.72, 6, 2, 0.5, false, Opt.of("fade", 0.4), j);
                SkillFx.sparks(d, V.up(spots[i], 0.6), BONE, 4, 3);
            }
            ghostfire(d, c, 2.2, 0.5, 0.85);
            SkillFx.shock(d, V.up(c, 0.08), FIRE, R, 0.35);
            SkillFx.play(d, "block.deepslate_bricks.break", c, 0.6, 1.2);
            SkillFx.play(d, "entity.skeleton.death", c, 0.6, 0.8);
            for (LivingEntity e : Kit.around(sp, c, R + 0.5, 3)) {
                JobKit.strike(sp, e, j, P, k.num("crumble", 2.1), true, false, life, 0, x -> {
                    if (RuneKit.fxQuota("jobYardEnd", 6)) JobFx.hit(e, j, 1.0);
                });
            }
        });
        return true;
    }

    /** 묘비 고리 (from ~ to 틱): 안의 적은 나가지 못함 (밀 수 있는 적은 고리 안으로 되돌림 · 녹색 불티) · 느려짐 (보스 제외) */
    private static void confine(ServerPlayer sp, Vec3 cg, double R, int from, int to, String life) {
        Set<UUID> in = new HashSet<>();
        for (int t = from; t <= to; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (LivingEntity e : Kit.around(sp, V.up(cg, 1.0), R + 1.5, 3.5)) {
                    double dx = e.getX() - cg.x;
                    double dz = e.getZ() - cg.z;
                    double d = Math.hypot(dx, dz);
                    if (tt == from && d < R + 0.2) in.add(e.getUUID());
                    else if (d < R - 0.4) in.add(e.getUUID());
                    if (!in.contains(e.getUUID())) continue;
                    if ((tt - from) % 10 == 0 && !JobKit.isBoss(e)) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 14, 1, false, false));
                    if (d > R - 0.45 && JobKit.pushable(e) && d > 1e-3) {
                        double kk = (R - 0.5) / d;
                        e.teleportTo(cg.x + dx * kk, e.getY(), cg.z + dz * kk);
                        Vec3 v = e.getDeltaMovement();
                        e.setDeltaMovement(-dx / d * 0.12, v.y, -dz / d * 0.12);
                        if (e.level() instanceof ServerLevel lv && RuneKit.fxQuota("graveWall", 4)) SkillFx.sparks(lv, V.up(e.position(), 0.6), FIRE, 4, 2.4);
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------ 소울 미스트

    /**
     * 소울 미스트: throwAt 틱에 혼령 안개 구슬 (녹색 혼불 · 해골) 을 조준한 곳 (range 칸 — 겨눈 적의 발밑) 으로 던짐 (flight 틱) → 터짐 (burst — 둘레 radius 칸 · 비명 해골 ·
     * 녹색 안개) · 가운데에 가장 가까운 적에 markFor 틱 소울 미스트 표식 (망자가 모두 그 적을 노리고 crewBonus 더 셈 · 쓰러지면 바로 망자) → 안개가 남아 first 틱부터 every
     * 틱마다 pulses 번 (mist · 둔화 slow 틱)
     */
    static boolean mist(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 c = groundAim(sp, k.num("range", 14));
        double R = k.num("radius", 3.5);
        int throwAt = (int) k.num("throwAt", 4);
        int flight = Math.max(1, (int) k.num("flight", 4));
        int burstAt = throwAt + flight;
        int first = (int) k.num("first", 18);
        int every = Math.max(1, (int) k.num("every", 10));
        int pulses = Math.max(1, (int) k.num("pulses", 3));
        int lastAt = first + every * (pulses - 1);
        int slow = (int) k.num("slow", 30);
        String life = RuneKit.lifeToken(sp);
        // 완드 끝에 구슬이 맺힘 (0 ~ throwAt)
        JobFx.soul(level0, wandTip(sp), 0.32, j, throwAt / 20.0 + 0.05, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
        JobFx.skull(level0, wandTip(sp), 0.2, 0, 2, throwAt / 20.0 + 0.05, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), j);
        SkillFx.play(level0, "entity.vex.ambient", sp.position(), 0.7, 0.8);
        RuneKit.later(throwAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 from = wandTip(sp);
            Vec3 to = V.up(c, 1.0);
            double len = V.len(V.sub(to, from));
            JobFx.soulFly(d, from, to, len / (flight / 20.0), 0.85, j, Opt.empty());
            JobFx.skull(d, from, 0.42, 1, 3, flight / 20.0, true, Opt.of("dir", V.unit(V.sub(to, from)), "speed", len / (flight / 20.0)), j);
            SkillFx.play(d, "entity.allay.item_thrown", from, 0.6, 1.0);
        });
        RuneKit.later(burstAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            JobFx.skull(d, V.up(c, 1.7), 0.9, 4, 2, 0.45, false, Opt.empty(), j);
            JobFx.smoke(d, V.up(c, 0.8), R * 0.95, DEEP, FIRE, 0.9, Opt.of("alpha", 0.85));
            JobFx.burst(d, V.up(c, 0.9), R * 0.55, j, 0.35, Opt.empty());
            SkillFx.shock(d, V.up(c, 0.08), FIRE, R, 0.4);
            SkillFx.sparks(d, V.up(c, 1.0), FIRE, 14, 5);
            SkillFx.play(d, "entity.vex.death", c, 0.6, 1.0);
            SkillFx.play(d, "particle.soul_escape", c, 0.7, 1.2);
            LivingEntity mark = null;
            double bd = Double.MAX_VALUE;
            for (LivingEntity e : Kit.around(sp, c, R, 3)) {
                double hd = V.hdist(e.position(), c);
                if (hd < bd) {
                    bd = hd;
                    mark = e;
                }
                JobKit.strike(sp, e, j, P, k.num("burst", 6.3), true, false, life, 0, x -> {
                    if (RuneKit.fxQuota("jobMistBurst", 6)) JobFx.hit(e, j, 1.1);
                });
            }
            if (mark != null) JobTraits8.mark(sp, mark, (int) k.num("markFor", 100), true);
        });
        // 안개 (burstAt ~ lastAt): 8틱마다 옅은 녹색 안개 · 떠도는 혼불
        for (int t = burstAt + 6; t <= lastAt; t += 8) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                double a = Kit.rand() * Math.PI * 2;
                Vec3 p = V.add(V.up(c, 0.5), new Vec3(Math.cos(a), 0, Math.sin(a)), R * 0.35 * Kit.rand());
                JobFx.smoke(d, p, R * 0.85, DEEP, FIRE, 0.75, Opt.of("alpha", 0.6));
                JobFx.soul(d, V.add(V.up(c, 0.4), new Vec3(Math.cos(a + 2), 0, Math.sin(a + 2)), R * 0.6), 0.2, j, 0.7, Opt.of("dir", V.UP, "speed", 0.8));
            });
        }
        for (int i = 0; i < pulses; i++) {
            RuneKit.later(first + every * i, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                SkillFx.shock(d, V.up(c, 0.08), FIRE, R * 0.9, 0.3);
                SkillFx.play(d, "entity.vex.hurt", c, 0.5, 0.6);
                for (LivingEntity e : Kit.around(sp, c, R, 3)) {
                    JobKit.strike(sp, e, j, P, k.num("mist", 2.1), true, false, life, 0, x -> {
                        if (!JobKit.isBoss(e)) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slow, 1, false, false));
                        if (RuneKit.fxQuota("jobMist", 6)) JobFx.hit(e, j, 0.8);
                    });
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 블라이트

    /**
     * 블라이트: every × ticks 틱 동안 내 둘레 radius 칸 땅이 썩어 듦 (나를 따라감 — 녹색으로 갈라진 땅 · 가장자리 무덤불 · 오르는 혼불) — every 틱마다 안의 적 (tick).
     * 그 안에서 쓰러진 적은 무덤 없이 바로 망자 선원 (JobTraits8.startBlight). 태세 칸 몫의 피해 스킬 (태세는 아님)
     */
    static boolean blight(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int every = Math.max(1, (int) k.num("every", 10));
        int ticks = Math.max(1, (int) k.num("ticks", 20));
        double R = k.num("radius", 4);
        int total = every * ticks;
        String life = RuneKit.lifeToken(sp);
        JobTraits8.startBlight(sp, R, total + 2);
        ServerLevel level0 = sp.level();
        crack(level0, sp.position(), R * 0.95, 0.7);
        SkillFx.shock(level0, V.up(sp.position(), 0.08), FIRE, R, 0.45);
        JobFx.aura(sp, j, 2.4, 0.8, 0.8);
        SkillFx.play(level0, "block.sculk.spread", sp.position(), 0.6, 1.2);
        SkillFx.play(level0, "entity.zombie_villager.cure", sp.position(), 0.5, 0.5);
        // 4틱마다: 둘레의 무덤불 · 혼불 (10틱마다 갈라진 땅 · 가장자리 불길 고리)
        for (int t = 0; t <= total; t += 4) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life) || !JobTraits8.blightOn(sp)) return; // 직업을 바꾸면 (JobTraits8.reset) 그침
                ServerLevel d = sp.level();
                Vec3 c = sp.position();
                // 영상 1: 갈라진 땅 · 작은 불만으로는 범위가 보이지 않았음 → 가장자리 녹색 빛 고리 · 낮은 독 안개 · 큰 무덤불
                if (tt % 20 == 0) crack(d, c, R * 0.95, 1.1);
                if (tt % 8 == 0) {
                    int n = 8;
                    double rot = tt * 0.09;
                    for (int i = 0; i < n; i++) {
                        double a = rot + i * Math.PI * 2 / n;
                        Vec3 p = V.add(c, new Vec3(Math.cos(a), 0, Math.sin(a)), R - 0.3);
                        ghostfire(d, new Vec3(p.x, RuneKit.groundAt(d, V.up(p, 1.2), c.y), p.z), 1.15, 0.5, 0.85);
                    }
                    JobFx.halo(d, V.up(c, 0.08), R, j, 0.45, Opt.of("vel", RuneKit.bodyVel(sp), "alpha", 0.85));
                    JobFx.smoke(d, V.up(c, 0.35), R * 0.8, DEEP, FIRE, 0.6, Opt.of("alpha", 0.4, "vel", RuneKit.bodyVel(sp)));
                }
                for (int i = 0; i < 2; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 p = V.add(V.up(c, 0.2), new Vec3(Math.cos(a), 0, Math.sin(a)), R * 0.85 * Math.sqrt(Kit.rand()));
                    JobFx.soul(d, p, 0.24, j, 0.6, Opt.of("dir", V.UP, "speed", 1.2, "alpha", 0.85));
                }
                if (tt % 40 == 0) SkillFx.play(d, "block.sculk.charge", c, 0.6, 0.6);
            });
        }
        for (int i = 1; i <= ticks; i++) {
            RuneKit.later(every * i, () -> {
                if (!RuneKit.sameLife(sp, life) || !JobTraits8.blightOn(sp)) return;
                ServerLevel d = sp.level();
                Vec3 c = sp.position();
                SkillFx.shock(d, V.up(c, 0.06), FIRE, R, 0.3);
                for (LivingEntity e : Kit.around(sp, c, R, 3)) {
                    JobKit.strike(sp, e, j, P, k.num("tick", 0.84), true, false, life, 0, x -> {
                        if (RuneKit.fxQuota("jobBlight", 6)) JobFx.hit(e, j, 0.7);
                    });
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 팬텀 갤리온

    /**
     * 팬텀 갤리온: 짙은 녹색 안개 (rpg:ult_naglfar) · 보는 쪽 start 칸부터 length 칸에 망자의 배 (입체 naglfar) 가 땅을 가르고 솟음 (rise 틱 — emergeAt 틱에 배 밑의 적
     * ram · 띄움) → sail 틱 동안 travel 칸 나아감 (뱃머리가 닿는 적 ram — 적마다 한 번 · 폭 width) → dropAt − 10 틱 갑판에서 거대한 망자 선장 (NecroCrew 선장 —
     * captainScale) 이 뛰어내려 dropAt 틱 겨눈 적 (range 칸 — 없으면 길 위의 가까운 적 · 그것도 없으면 앞) 곁에 내려앉음 (slam — 둘레 slamRadius 칸) → 배가 땅속으로
     * 가라앉고 finalAt 틱 갈라진 땅 (길 전체 — 폭 width) 에서 녹색 혼불이 치솟음 (final — 큰 숫자 · 띄움). 선장은 captainFor 틱 함께 싸움 (captainEvery 틱마다 위력 ×
     * captainHit — 친 적에게 망자가 몰려듦)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 r = V.rightOf(f);
        int rise = (int) k.num("rise", 14);
        int emergeAt = (int) k.num("emergeAt", 10);
        int sail = (int) k.num("sail", 20);
        int dropAt = (int) k.num("dropAt", 40);
        int jumpAt = dropAt - 10;
        int finalAt = (int) k.num("finalAt", 56);
        double start = k.num("start", 1);
        double length = k.num("length", 8);
        double travel = k.num("travel", 10);
        double half = k.num("width", 5) / 2;
        double slamR = k.num("slamRadius", 4);
        LivingEntity aimed = Kit.pickTarget(sp, k.num("range", 16), 12);
        String life = RuneKit.lifeToken(sp);
        Ult.sky(level0, c0, "rpg:ult_naglfar", finalAt + 40);
        // 배 자리: 가운데 (뱃머리 · 고물의 가운데) 가 앞 start + length/2 · 땅 높이 (배 밑은 땅 아래) — 아래 4칸에서 솟음
        Vec3 shipC = V.add(c0, f, start + length / 2);
        Vec3 shipLow = V.up(shipC, -4.2);
        Vec3 sailEnd = V.add(shipC, f, travel);
        Vec3 sinkEnd = V.up(V.add(sailEnd, f, 2.0), -5.0);
        int shipId = JobFx3d.spawn(level0, "naglfar", shipLow, BONE, FIRE, Opt.of("to", shipC, "ctrl", V.up(shipC, -1.5), "travel", rise / 20.0, "ease", "out3",
                "dir", f, "face", V.UP, "scale", 1.0, "life", (finalAt - 2) / 20.0, "fadeIn", 0.15, "fadeOut", 0.35, "glowAlpha", 0.55, "shine", 0.2));
        JobFx3d.guard(sp, life, new int[] {shipId}, finalAt);
        // 솟음: 배 둘레 땅이 갈라지고 녹색 불길 · 안개 · 소리
        for (int i = 0; i < 4; i++) {
            Vec3 p = V.add(c0, f, start + 1 + i * 2.2);
            JobFx.rift(level0, p, f, 0.7, 0, 3, rise / 20.0, false, Opt.empty(), j);
            RuneKit.later(rise - 1, () -> JobFx.rift(level0, p, f, 0.7, 3, 2, (finalAt - rise) / 20.0, true, Opt.empty(), j));
        }
        for (int i = 0; i < 6; i++) {
            double s = start + length * (i + 0.5) / 6;
            for (int side = -1; side <= 1; side += 2) {
                Vec3 p = V.add(V.add(c0, f, s), r, side * (half - 0.4));
                RuneKit.later(i, () -> ghostfire(level0, p, 1.6, 0.6, 0.85));
            }
        }
        JobFx.smoke(level0, V.up(V.add(c0, f, start + length / 2), 1.0), 3.2, DEEP, FIRE, rise / 20.0 + 0.6, Opt.of("alpha", 0.7));
        SkillFx.play(level0, "mob.warden.emerge", shipC, 0.6, 1.4);
        SkillFx.play(level0, "entity.elder_guardian.curse", c0, 0.6, 1.0);
        SkillFx.play(level0, "ambient.cave", shipC, 0.6, 1.0);
        Set<UUID> rammed = new HashSet<>();
        // 솟을 때 배 밑의 적 (emergeAt 틱 — 고물 start ~ 뱃머리 start + length)
        RuneKit.later(emergeAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            SkillFx.play(d, "entity.wither.break_block", shipC, 0.6, 1.0);
            SkillFx.play(d, "mace.heavy_smash_ground", shipC, 0.7, 1.0);
            for (LivingEntity e : Kit.around(sp, shipC, length / 2 + half, 4)) {
                double s = V.dot(V.sub(e.position(), c0), f);
                double lat = Math.abs(V.dot(V.sub(e.position(), c0), r));
                if (s < start - 0.5 || s > start + length + 0.5 || lat > half + Kit.bodyPad(e)) continue;
                ram(sp, j, k, P, e, f, life, rammed, true);
            }
        });
        // 나아감 (rise ~ rise + sail): 뱃머리가 닿는 적 (틱마다 — 적마다 한 번)
        RuneKit.later(rise, () -> {
            if (RuneKit.sameLife(sp, life)) mmorpg.fabric.Fx3d.move(sp.level(), shipId, sailEnd, sail / 20.0, "inout");
        });
        for (int t = rise + 1; t <= rise + sail; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                double u = JobMove.ease((tt - rise) / (double) sail, JobMove.INOUT);
                double bow = start + length + travel * u;
                Vec3 bowAt = V.add(c0, f, bow);
                if ((tt - rise) % 3 == 0) {
                    JobFx.rift(d, V.add(bowAt, f, -1.6), f, 0.7, 0, 3, 0.3, false, Opt.empty(), j);
                    RuneKit.later(5, () -> JobFx.rift(d, V.add(bowAt, f, -1.6), f, 0.7, 3, 2, Math.max(0.2, (finalAt - tt - 5) / 20.0), true, Opt.empty(), j));
                    ghostfire(d, V.add(bowAt, r, half - 0.3), 1.2, 0.4, 0.8);
                    ghostfire(d, V.add(bowAt, r, -half + 0.3), 1.2, 0.4, 0.8);
                    SkillFx.sparks(d, V.up(bowAt, 0.3), BONE, 4, 3);
                }
                if ((tt - rise) % 5 == 0) SkillFx.play(d, "block.deepslate_bricks.break", bowAt, 0.6, 1.0);
                for (LivingEntity e : Kit.around(sp, bowAt, half + 1.5, 4)) {
                    if (rammed.contains(e.getUUID())) continue;
                    double s = V.dot(V.sub(e.position(), c0), f);
                    double lat = Math.abs(V.dot(V.sub(e.position(), c0), r));
                    if (s > bow + 0.5 || lat > half + Kit.bodyPad(e)) continue;
                    ram(sp, j, k, P, e, f, life, rammed, false);
                }
            });
        }
        // 선장이 뛰어내림 (jumpAt): 갑판 (배의 그때 자리) 에서 겨눈 적 곁으로 (dropAt 틱에 내려앉음)
        NecroCrew.Member[] cap = new NecroCrew.Member[1];
        Vec3[] slamAt = new Vec3[1];
        RuneKit.later(jumpAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            double u = JobMove.ease(Math.min(1, (jumpAt - rise) / (double) sail), JobMove.INOUT);
            Vec3 deck = V.up(V.add(shipC, f, travel * u), 1.6);
            LivingEntity t = aimed != null && !aimed.isRemoved() && aimed.isAlive() && aimed.level() == d ? aimed : null;
            if (t == null) {
                double endS = start + length + travel;
                double bd = Double.MAX_VALUE;
                for (LivingEntity e : Kit.around(sp, V.add(c0, f, endS / 2), endS / 2 + 3, 4)) {
                    double hd = V.hdist(e.position(), deck);
                    if (hd < bd) {
                        bd = hd;
                        t = e;
                    }
                }
            }
            Vec3 land;
            float yaw;
            if (t != null) {
                Vec3 back = Math.hypot(t.getX() - c0.x, t.getZ() - c0.z) > 0.3 ? V.flat(V.sub(c0, t.position())) : V.scale(f, -1);
                land = NecroCrew.standAt(d, V.add(t.position(), V.turnY(back, 25), Kit.bodyPad(t) + 1.3));
                slamAt[0] = t.position();
                yaw = (float) Math.toDegrees(Math.atan2(-(t.getX() - land.x), t.getZ() - land.z));
            } else {
                land = NecroCrew.standAt(d, V.add(c0, f, start + length));
                slamAt[0] = V.add(land, f, 1.5);
                yaw = sp.getYRot();
            }
            NecroCrew.Member m = NecroCrew.captainAt(sp, deck, yaw, (int) k.num("captainFor", 300) + (finalAt - jumpAt), k.num("captainScale", 2.2),
                    (int) k.num("captainEvery", 24), k.num("captainHit", 1.2));
            if (m == null) return;
            cap[0] = m;
            ghostfire(d, deck, 2.4, 0.5, 0.9);
            JobFx.skull(d, V.up(deck, 4.0), 0.8, 0, 4, 0.5, false, Opt.empty(), j);
            SkillFx.play(d, "entity.wither_skeleton.ambient", deck, 0.5, 1.4);
            SkillFx.play(d, "entity.ravager.roar", deck, 0.7, 0.9);
            int flight = Math.max(4, dropAt - jumpAt);
            Vec3 from = deck;
            Vec3 to = land;
            for (int tt = 1; tt <= flight; tt++) {
                double q = tt / (double) flight;
                RuneKit.later(tt, () -> {
                    if (m.x.isRemoved()) return;
                    Vec3 p = RuneKit.leapPoint(from, to, 3.2, q);
                    m.x.setPos(p.x, p.y, p.z);
                });
            }
            RuneKit.later(flight - mmorpg.fabric.mob.TyrfingWarrior.STRIKE_AT, () -> {
                if (!m.x.isRemoved()) m.x.swingAxe();
            });
        });
        JobKit.motionLater(sp, "necromancer_ult:drop", jumpAt - 2, life);
        // 내려앉음 (dropAt): 녹색 충격 고리 · 갈라진 땅 · 혼불 → slam (둘레 slamRadius 칸) → 선장을 놓아 줌 (captainFor 틱)
        RuneKit.later(dropAt, () -> {
            NecroCrew.Member m = cap[0];
            if (!RuneKit.sameLife(sp, life)) {
                if (m != null) NecroCrew.crumble(m, false);
                return;
            }
            ServerLevel d = sp.level();
            Vec3 g = m != null && !m.x.isRemoved() ? m.x.position() : slamAt[0];
            if (g == null) return;
            Vec3 hit = slamAt[0] != null ? slamAt[0] : g;
            crack(d, hit, 2.6, 1.4);
            JobFx.rift(d, hit, f, 0.8, 0, 3, 0.4, false, Opt.empty(), j);
            SkillFx.shock(d, V.up(hit, 0.1), FIRE, slamR, 0.45);
            ghostfire(d, hit, 3.2, 0.55, 0.95);
            JobFx.burst(d, V.up(hit, 1.0), 2.2, j, 0.4, Opt.empty());
            SkillFx.sparks(d, V.up(hit, 0.5), BONE, 14, 6);
            SkillFx.play(d, "mace.heavy_smash_ground", hit, 0.6, 1.4);
            SkillFx.play(d, "entity.zombie.break_wooden_door", hit, 0.6, 1.0);
            for (LivingEntity e : Kit.around(sp, hit, slamR, 4)) {
                JobKit.strike(sp, e, j, P, k.num("slam", 18), true, false, life, 0, x -> {
                    if (JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), hit), 0.4, 0.35);
                    if (RuneKit.fxQuota("jobCaptainSlam", 6)) JobFx.hit(e, j, 1.4);
                });
            }
            if (m != null && !m.x.isRemoved()) {
                NecroCrew.release(m, g, (int) k.num("captainFor", 300));
                LivingEntity t = aimed != null && aimed.isAlive() && !aimed.isRemoved() ? aimed : null;
                if (t != null) JobTraits8.setFocus(sp, t);
            }
        });
        // 배가 가라앉음 (rise + sail ~ finalAt − 4): 앞으로 조금 더 가며 땅속으로
        RuneKit.later(rise + sail, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            mmorpg.fabric.Fx3d.move(sp.level(), shipId, sinkEnd, Math.max(0.2, (finalAt - 4 - rise - sail) / 20.0), "in");
            SkillFx.play(sp.level(), "entity.warden.dig", sailEnd, 0.6, 1.2);
            JobFx.smoke(sp.level(), V.up(sailEnd, 0.8), 3.0, DEEP, FIRE, 1.0, Opt.of("alpha", 0.75));
        });
        // 마지막: 갈라진 땅 (길 전체) 에서 녹색 혼불이 치솟음 · 비명 해골 → final (큰 숫자 · 띄움)
        JobKit.motionLater(sp, "necromancer_ult:final", finalAt - 4, life);
        RuneKit.later(finalAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            double endS = start + length + travel;
            int cols = (int) Math.ceil(endS / 2.0);
            for (int i = 0; i <= cols; i++) {
                double s = Math.min(endS, i * 2.0 + 0.5);
                Vec3 p = V.add(c0, f, s);
                int ii = i;
                RuneKit.later(i / 2, () -> {
                    ghostfire(d, p, 3.0 + (ii % 2) * 0.6, 0.6, 0.95);
                    if (ii % 2 == 0) JobFx.skull(d, V.up(p, 2.4), 0.7, 4, 2, 0.4, false, Opt.empty(), j);
                    SkillFx.sparks(d, V.up(p, 1.0), FIRE, 5, 4);
                });
            }
            JobFx.burst(d, V.up(V.add(c0, f, endS / 2), 1.0), 3.0, j, 0.45, Opt.empty());
            Ult.flash(d, V.add(c0, f, endS / 2), FIRE, Opt.empty(), sp);
            SkillFx.play(d, "random.explode", V.add(c0, f, 4), 0.7, 1.2);
            SkillFx.play(d, "mob.wither.shoot", V.add(c0, f, 4), 0.6, 1.0);
            SkillFx.play(d, "particle.soul_escape", V.add(c0, f, 4), 0.6, 1.2);
            for (LivingEntity e : Kit.around(sp, V.add(c0, f, endS / 2), endS / 2 + half, 4)) {
                double s = V.dot(V.sub(e.position(), c0), f);
                double lat = Math.abs(V.dot(V.sub(e.position(), c0), r));
                if (s < -0.5 || s > endS + 0.5 || lat > half + 0.5 + Kit.bodyPad(e)) continue;
                JobKit.strike(sp, e, j, P, k.num("final", 24), true, true, life, 0, x -> {
                    if (JobKit.pushable(e)) Kit.knockback(e, 0, 0, 0.55);
                    if (RuneKit.fxQuota("jobGalleon", 6)) JobFx.hit(e, j, 1.5);
                });
            }
        });
        return true;
    }

    /** 배에 부딪힘 (적마다 한 번): ram · 띄움 (솟을 때 위로 · 나아갈 때 옆 · 앞으로) · 뼈 조각 · 녹색 불티 */
    private static void ram(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, LivingEntity e, Vec3 f, String life, Set<UUID> rammed, boolean emerge) {
        if (!rammed.add(e.getUUID())) return;
        JobKit.strike(sp, e, j, P, k.num("ram", 14), true, false, life, 0, x -> {
            if (JobKit.pushable(e)) {
                Vec3 side = V.rightOf(f);
                double s = V.dot(V.sub(e.position(), sp.position()), side) >= 0 ? 1 : -1;
                if (emerge) Kit.knockback(e, f.x * 0.1, f.z * 0.1, 0.55);
                else Kit.knockback(e, side.x * s * 0.45 + f.x * 0.2, side.z * s * 0.45 + f.z * 0.2, 0.4);
            }
            if (e.level() instanceof ServerLevel lv) {
                SkillFx.sparks(lv, Kit.bodyPoint(e), BONE, 6, 4);
                if (RuneKit.fxQuota("jobGalleonRam", 6)) JobFx.hit(e, j, 1.3);
            }
        });
    }
}
