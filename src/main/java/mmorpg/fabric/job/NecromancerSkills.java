package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.mob.TyrfingWarrior;
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
 * 0.51.0 2차 직업 — 매지션 반전 나글파르(네크로맨서) 스킬 (jobs.json advance.necromancer — 독초록 · 뼈색, 특성 「망자의 군대」 — JobTraits8 · NecroCrew): 같은 완드 ·
 * 같은 칸 역할의 새 스킬 다섯 — 뼈 창 (꿰뚫음 · 1.2초에 몸에서 뼈 가시 · 5초 안에 쓰러지면 망자로) · 망자와 자리 바꾸기 (떠난 자리에 뼈 방벽) · 시체 폭발 (시체 · 망자 · 땅 속
 * 망자가 부풀어 터짐 · 독 뼈 구름) · 망자 일으키기 (선원 둘 · 망자 공격 +30%) · 나글파르의 출항 (안개 속 손발톱 배가 땅을 가르며 지나가고 선원 여섯이 뛰어내림). 모두 마법 피해.
 * 연출 = 2D 그림 (JobFx — 뼈 창 rfx_jbonespear · 뼈 방벽 rfx_jbonewall · 해골 rfx_jskull · 갈라진 땅 rfx_jrift, 1차 사신의 혼불 · 망자의 손) + 망자는 진짜 몹
 * (rpg:naglfar_crew) + 배는 입체 모델 (naglfar — 사신 궁의 명계의 문처럼 궁극기의 큰 물체).
 */
final class NecromancerSkills {
    private NecromancerSkills() {}

    private static final double[] FIRE = NecroCrew.FIRE;
    private static final double[] DEEP = NecroCrew.DEEP;
    private static final double[] BONE = {0.92, 0.88, 0.74};
    private static final double[] BONE_CORE = {1.0, 0.98, 0.9};

    static void register() {
        JobSystem.register("necromancer_spear", NecromancerSkills::spear);
        JobSystem.register("necromancer_swap", NecromancerSkills::swap);
        JobSystem.register("necromancer_burst", NecromancerSkills::burst);
        JobSystem.register("necromancer_raise", NecromancerSkills::raise);
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

    // ------------------------------------------------------------ 뼈 창

    /**
     * 뼈 창: 완드 끝 위에 뼈가 모여 창이 됨 (throwAt 틱 — 녹색 혼불) → 겨눈 적 쪽 (없으면 보는 쪽) 으로 던짐 (speed 칸/초 — 벽 앞까지 range 칸): 길 폭 width 안의 적을 모두
     * 꿰뚫음 (spear — 창끝이 닿는 틱) · 맞은 적에 표식 (markFor 틱 — 그 안에 무엇에든 쓰러지면 망자로) → burstAt 틱에 맞은 적의 몸에서 뼈 가시가 터져 나옴 (burst)
     */
    static boolean spear(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        int throwAt = (int) k.num("throwAt", 6);
        int burstAt = (int) k.num("burstAt", 24);
        double range = k.num("range", 16);
        double width = k.num("width", 1.3);
        double speed = k.num("speed", 40);
        String life = RuneKit.lifeToken(sp);
        long t0 = Tasks.now();
        // 빚기: 완드 끝 위 (앞 · 위를 가리킴) 에 뼈 창이 모여 생김 · 녹색 혼불 둘이 감돎
        Vec3 f0 = Kit.facingOf(sp);
        Vec3 tip0 = V.up(V.add(wandTip(sp), f0, 0.35), 0.35);
        JobFx.boneSpearAt(level0, tip0, V.unit(V.add(f0, V.UP, 0.35)), 0.5, throwAt / 20.0 + 0.05, j, Opt.of("vel", RuneKit.bodyVel(sp), "alpha", 0.9));
        JobFx.soul(level0, V.add(tip0, f0, -0.4), 0.2, j, throwAt / 20.0, Opt.of("vel", RuneKit.bodyVel(sp)));
        SkillFx.play(level0, "block.bone_block.place", tip0, 0.8, 1.0);
        SkillFx.play(level0, "entity.skeleton.ambient", tip0, 0.7, 0.6);
        RuneKit.later(throwAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 from = wandTip(sp);
            LivingEntity t = Kit.pickTarget(sp, range, 8);
            Vec3 aim = t != null ? Kit.bodyPoint(t) : V.add(sp.getEyePosition(), sp.getViewVector(1F), range);
            Vec3 d = V.sub(aim, from);
            Vec3 u = V.len(d) < 1e-3 ? sp.getViewVector(1F) : V.unit(d);
            double reach = Math.min(range, RuneKit.rayLength(level, from, u, range));
            Vec3 end = V.add(from, u, Math.max(1.0, reach));
            JobFx.boneSpear(level, from, end, j, speed, 0.75, Opt.empty());
            SkillFx.play(level, "item.trident.throw", from, 0.8, 1.0);
            SkillFx.play(level, "entity.skeleton.shoot", from, 0.6, 0.8);
            // 꿰뚫음: 길 (from → end) 폭 width 안의 적 — 창끝이 닿는 틱에
            int markFor = (int) k.num("markFor", 100);
            for (LivingEntity e : Kit.around(sp, V.lerp(from, end, 0.5), V.len(V.sub(end, from)) / 2 + width, 4)) {
                Vec3 b = Kit.bodyPoint(e);
                if (JobTraits8.segDist(b, from, end) > width + Kit.bodyPad(e)) continue;
                double along = Math.max(0, V.dot(V.sub(b, from), u));
                int delay = Math.max(0, (int) Math.round(along / speed * 20));
                JobKit.strike(sp, e, j, P, k.num("spear", 4.9), true, false, life, delay, x -> {
                    JobTraits8.markSpear(sp, e, j, markFor);
                    if (e.level() instanceof ServerLevel lv) {
                        if (RuneKit.fxQuota("jobBoneSpear", 6)) JobFx.hit(e, j, 1.0);
                        SkillFx.sparks(lv, Kit.bodyPoint(e), BONE, 5, 3);
                        SkillFx.play(lv, "entity.skeleton.hurt", Kit.bodyPoint(e), 0.8, 0.6);
                    }
                    // 뼈 가시가 터짐 (쓴 때부터 burstAt 틱)
                    int left = (int) Math.max(0, t0 + burstAt - Tasks.now());
                    JobKit.strike(sp, e, j, P, k.num("burst", 2.1), true, false, life, left, y -> {
                        if (!(e.level() instanceof ServerLevel lv)) return;
                        Vec3 c = Kit.bodyPoint(e);
                        JobFx.boneWall(lv, e.position(), 0.55 + Kit.bodyPad(e) * 0.4, 2, 0.35, Opt.of("vel", RuneKit.entVel(e)), j);
                        JobFx.shatter(lv, c, 0.8 + Kit.bodyPad(e) * 0.3, BONE, BONE_CORE, 0.35, Opt.empty());
                        ghostfire(lv, e.position(), 1.1, 0.4, 0.8);
                        SkillFx.play(lv, "block.bone_block.break", c, 0.8, 1.0);
                    });
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 망자와 자리 바꾸기

    /**
     * 망자와 자리 바꾸기: 보는 쪽의 망자 (range 칸 · cone 도 — 없으면 가장 먼 망자) 와 자리를 바꿈. 망자가 없으면 뒤 (막히면 왼쪽 · 오른쪽) puppet 칸 땅에서 뼈 허수아비가 솟아
     * 그와 바꿈 (허수아비는 떠난 자리에서 무너짐). 떠난 자리: wallAt 틱 뼈 방벽이 솟아 둘레 radius 칸 (wall · root 틱 묶음 — 보스는 둔화) → shatterAt 틱 부서지며 뼈 조각
     * (shatter). 묶였거나 갈 곳이 없으면 쓰지 않음
     */
    static boolean swap(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        NecroCrew.Member m = NecroCrew.swapPick(sp, k.num("range", 20), k.num("cone", 40));
        Vec3 dest = null;
        if (m != null) {
            Vec3 at = m.x.position();
            if (Kit.moveTo(sp, at)) {
                dest = at;
                m.x.getNavigation().stop();
                m.x.teleportTo(start.x, start.y, start.z);
                m.x.setYRot(sp.getYRot());
                m.foe = null;
            }
        }
        boolean puppet = false;
        if (dest == null) {
            double want = k.num("puppet", 6);
            for (Vec3 dir : new Vec3[] {V.scale(f, -1), V.scale(V.rightOf(f), -1), V.rightOf(f)}) {
                double dist = RuneKit.dashDistance(sp, dir, want, 1.1, 1.8);
                if (dist < 1.2) continue;
                Vec3 b = V.add(start, dir, dist);
                Vec3 at = new Vec3(b.x, RuneKit.groundAt(level, b, start.y), b.z);
                if (Kit.moveTo(sp, at)) {
                    dest = at;
                    puppet = true;
                    break;
                }
            }
        }
        if (dest == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 자리를 바꿀 망자가 없고 둘레가 막혔습니다", 30);
            return false;
        }
        Kit.noFall(sp, 30);
        String life = RuneKit.lifeToken(sp);
        // 자리 바꿈: 두 자리를 잇는 녹색 혼불 줄 · 두 자리의 무덤불 · 해골 연기 (허수아비면 간 자리의 땅에서 뼈 손이 솟았다 사라짐)
        RuneKit.streak(level, V.up(start, 1.0), V.up(dest, 1.0), FIRE, 0.2, 0.35, 0.8, 0);
        RuneKit.streak(level, V.up(start, 1.0), V.up(dest, 1.0), BONE, 0.07, 0.3, 0.9, 0);
        ghostfire(level, start, 1.8, 0.5, 0.9);
        ghostfire(level, dest, 1.5, 0.45, 0.8);
        JobFx.skull(level, V.up(start, 1.4), 0.5, 0, 2, 0.3, false, Opt.empty(), j);
        if (puppet) {
            JobFx.hand(level, dest, 0.36, j, 0.5, JobFx.hide1p(sp));
            JobFx.boneWall(level, start, 0.6, 2, 0.4, Opt.empty(), j);
        }
        JobFx.speed(sp, j, 0.3, 0.8);
        SkillFx.play(level, "entity.enderman.teleport", start, 0.7, 0.8);
        SkillFx.play(level, "entity.skeleton.ambient", dest, 0.6, 0.8);
        double radius = k.num("radius", 2.5);
        int root = (int) k.num("root", 20);
        int wallAt = (int) k.num("wallAt", 4);
        int shatterAt = (int) k.num("shatterAt", 24);
        // 뼈 방벽: 떠난 자리 둘레에 뼈 가시 다섯 (둘레 1.6칸 — 세워 카메라 쪽) + 가운데 큰 것 · 녹색 금 → 둘레 적 (wall · 묶음)
        RuneKit.later(wallAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            for (int i = 0; i < 5; i++) {
                double a = Math.toRadians(i * 72 + 18);
                Vec3 p = new Vec3(start.x + Math.cos(a) * 1.6, start.y, start.z + Math.sin(a) * 1.6);
                JobFx.boneWall(d, p, 0.75, 0, 0.15, Opt.empty(), j);
                RuneKit.later(3, () -> JobFx.boneWall(d, p, 0.75, 1, (shatterAt - wallAt - 3) / 20.0, Opt.empty(), j));
            }
            JobFx.boneWall(d, start, 1.05, 0, 0.15, Opt.empty(), j);
            RuneKit.later(3, () -> JobFx.boneWall(d, start, 1.05, 1, (shatterAt - wallAt - 3) / 20.0, Opt.empty(), j));
            crack(d, start, radius * 1.1, (shatterAt - wallAt) / 20.0 + 0.4);
            SkillFx.shock(d, V.up(start, 0.1), FIRE, radius / 1.8, 0.4);
            SkillFx.play(d, "block.bone_block.break", start, 0.6, 1.2);
            SkillFx.play(d, "entity.zombie.break_wooden_door", start, 0.8, 0.5);
            Kit.shakeNear(d, start, 8, 0.15, 0.2);
            for (LivingEntity e : Kit.around(sp, start, radius, 2.5)) {
                JobKit.strike(sp, e, j, P, k.num("wall", 2.8), true, false, life, 0, x -> {
                    RuneKit.effect(e, "slowness", root, JobKit.isBoss(e) ? 1 : 10);
                    RuneKit.knock(e, V.add(V.sub(e.position(), start), V.UP, 0.2), 0.15, 0.3);
                    if (RuneKit.fxQuota("jobBoneWall", 6)) JobFx.hit(e, j, 1.0);
                });
            }
        });
        // 부서짐: 뼈 가시가 조각나 둘레로 튐 (shatter)
        RuneKit.later(shatterAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            for (int i = 0; i < 5; i++) {
                double a = Math.toRadians(i * 72 + 18);
                Vec3 p = new Vec3(start.x + Math.cos(a) * 1.6, start.y, start.z + Math.sin(a) * 1.6);
                JobFx.boneWall(d, p, 0.75, 2, 0.35, Opt.empty(), j);
            }
            JobFx.boneWall(d, start, 1.05, 2, 0.35, Opt.empty(), j);
            JobFx.shatter(d, V.up(start, 1.0), radius * 0.6, BONE, BONE_CORE, 0.4, Opt.empty());
            SkillFx.sparks(d, V.up(start, 0.8), BONE, 12, 5);
            SkillFx.play(d, "block.bone_block.break", start, 1.0, 1.2);
            SkillFx.play(d, "entity.skeleton.death", start, 0.9, 0.6);
            for (LivingEntity e : Kit.around(sp, start, radius + 0.5, 2.5)) {
                JobKit.strike(sp, e, j, P, k.num("shatter", 1.4), true, false, life, 0, x -> {
                    if (RuneKit.fxQuota("jobBoneShard", 6)) JobFx.hit(e, j, 0.9);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 시체 폭발

    /**
     * 시체 폭발: 겨눈 적 (없으면 조준한 곳) 둘레 seek 칸의 시체 (쓰러진 적의 녹색 혼불) → 없으면 망자 → 둘 다 없으면 겨눈 곳 땅 속 망자를 끌어올림. 그 몸이 부풀어 (swell 틱 —
     * 녹색 빛 · 해골 · 심장 소리) 터짐 (blast — 둘레 radius 칸, 밀쳐냄) → 독 뼈 구름이 남아 cloudAt 틱부터 gap 틱마다 (pulses 번) 둘레 cloudRadius 칸 (cloud · 둔화)
     */
    static boolean burst(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        double range = k.num("range", 14);
        double seek = k.num("seek", 6);
        LivingEntity t = Kit.pickTarget(sp, range, 12);
        Vec3 aim;
        if (t != null) aim = t.position();
        else {
            Vec3 a = Kit.aimPoint(sp, range);
            aim = new Vec3(a.x, RuneKit.groundAt(level, a, sp.getY()), a.z);
        }
        JobTraits8.Corpse corpse = JobTraits8.takeCorpse(level, aim, seek);
        NecroCrew.Member crew = corpse == null ? NecroCrew.nearest(sp, aim, seek) : null;
        Vec3 at;
        TyrfingWarrior body;
        int swell = (int) k.num("swell", 10);
        if (crew != null) {
            NecroCrew.take(sp, crew);
            body = crew.x;
            body.getNavigation().stop();
            at = body.position();
        } else {
            // 시체 · 땅 속 망자: 그 자리 땅 아래에서 망자 몸이 반쯤 솟음 (swell 틱 — 물리 꺼짐)
            at = NecroCrew.standAt(level, corpse != null ? corpse.at() : aim);
            body = TyrfingWarrior.spawnAs(TyrfingWarrior.CREW_ID, level, V.up(at, -NecroCrew.DEPTH), (float) (Kit.rand() * 360), swell + 20);
            if (body != null) {
                body.noPhysics = true;
                body.setNoGravity(true);
                TyrfingWarrior b = body;
                Vec3 g = at;
                for (int tt = 1; tt <= swell; tt++) {
                    double q = tt / (double) swell;
                    double up = 1 - (1 - q) * (1 - q);
                    RuneKit.later(tt, () -> {
                        if (!b.isRemoved()) b.setPos(g.x, g.y - NecroCrew.DEPTH * (1 - up * 0.75), g.z);
                    });
                }
            }
            crack(level, at, 1.6, swell / 20.0 + 0.6);
            JobFx.hand(level, at, 0.3, j, swell / 20.0, Opt.empty());
        }
        String life = RuneKit.lifeToken(sp);
        // 부풀어 오름: 녹색 무덤불이 커짐 · 몸 위 해골 (눈빛 맥동) · 심장 소리
        Vec3 c0 = at;
        for (int tt = 0; tt < swell; tt += 3) {
            int ttt = tt;
            RuneKit.later(tt, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ghostfire(sp.level(), c0, 1.0 + ttt * 0.12, 0.25, 0.85);
            });
        }
        JobFx.skull(level, V.up(at, 2.6), 0.55, 0, 4, swell / 20.0, false, Opt.empty(), j);
        SkillFx.play(level, "mob.warden.heartbeat", at, 1.4, 1.0);
        SkillFx.play(level, "entity.zombie.ambient", at, 0.6, 0.8);
        double radius = k.num("radius", 3.5);
        TyrfingWarrior bodyF = body;
        RuneKit.later(swell, () -> {
            if (bodyF != null && !bodyF.isRemoved()) bodyF.discard();
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 c = V.up(c0, 1.0);
            JobFx.skull(d, V.up(c0, 1.6), 1.4, 4, 2, 0.4, false, Opt.empty(), j);
            JobFx.burst(d, c, radius * 0.6, j, 0.4, Opt.empty());
            JobFx.shatter(d, c, radius * 0.55, BONE, BONE_CORE, 0.45, Opt.empty());
            ghostfire(d, c0, 3.2, 0.6, 0.95);
            JobFx.ring(d, V.up(c0, 0.06), radius, j, false, 0.4, Opt.empty());
            crack(d, c0, radius, 1.5);
            SkillFx.sparks(d, c, BONE, 14, 6);
            SkillFx.sparks(d, c, FIRE, 10, 5);
            SkillFx.play(d, "random.explode", c0, 0.9, 1.0);
            SkillFx.play(d, "entity.skeleton.death", c0, 0.6, 0.9);
            SkillFx.play(d, "block.bone_block.break", c0, 0.7, 1.2);
            Kit.shakeNear(d, c0, 12, 0.3, 0.3);
            for (LivingEntity e : Kit.around(sp, c0, radius, 3)) {
                JobKit.strike(sp, e, j, P, k.num("blast", 8.4), true, false, life, 0, x -> {
                    RuneKit.knock(e, V.sub(e.position(), c0), 0.4, 0.3);
                    if (RuneKit.fxQuota("jobCorpse", 6)) JobFx.hit(e, j, 1.3);
                });
            }
        });
        // 독 뼈 구름 (cloudAt 틱부터 gap 틱마다 pulses 번)
        int pulses = (int) k.num("pulses", 3);
        int gap = (int) k.num("gap", 10);
        int cloudAt = (int) k.num("cloudAt", 20);
        double cr = k.num("cloudRadius", 3);
        int slow = (int) k.num("slow", 30);
        for (int i = 0; i < pulses; i++) {
            int ii = i;
            RuneKit.later(swell + 2 + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                JobFx.smoke(sp.level(), V.up(c0, 0.8 + 0.2 * ii), cr * 0.75, DEEP, FIRE, gap / 20.0 + 0.35, Opt.of("alpha", 0.8));
            });
            RuneKit.later(cloudAt + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                JobFx.soul(d, V.up(c0, 0.5), 0.3, j, 0.4, Opt.of("dir", V.UP, "speed", 1.2));
                SkillFx.play(d, "block.lava.extinguish", c0, 0.6, 0.5);
                for (LivingEntity e : Kit.around(sp, c0, cr, 3)) {
                    JobKit.strike(sp, e, j, P, k.num("cloud", 1.4), true, false, life, 0, x -> {
                        RuneKit.effect(e, "slowness", slow, 1);
                        if (RuneKit.fxQuota("jobCloud", 6)) JobFx.hit(e, j, 0.7);
                    });
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 망자 일으키기

    /**
     * 망자 일으키기: dur 틱 태세 (망자 공격 + boost — JobTraits8.crewMult) · 앞 2.2칸 양옆에서 망자 선원 crew 명이 바로 솟음 (특성의 망자 — 최대 trait.max 안에서, 넘으면 가장
     * 오래된 것이 무너짐). 그림: 완드로 땅을 두드림 · 녹색 고리 · 갈라진 땅 · 머리 위 비명 지르는 해골 · 혼불
     */
    static boolean raise(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", 200);
        JobTraits.startStance(sp, k, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 r = V.rightOf(f);
        int n = Math.max(1, (int) k.num("crew", 2));
        int life = (int) JobTraits8.trait(j, "crewFor", 400);
        for (int i = 0; i < n; i++) {
            double side = n == 1 ? 0 : (i - (n - 1) / 2.0) * 3.2;
            Vec3 spot = V.add(V.add(c, f, 2.2), r, side);
            NecroCrew.raise(sp, j, spot, sp.getYRot(), life, NecroCrew.TRAIT);
        }
        JobFx.aura(sp, j, 2.6, 1.0, 0.85);
        JobFx.ring(level, V.up(c, 0.06), 2.8, j, false, 0.6, Opt.empty());
        crack(level, c, 2.2, 1.2);
        JobFx.skull(level, V.up(c, 2.8), 0.6, 0, 5, 0.9, false, JobFx.hide1p(sp), j);
        ghostfire(level, c, 1.4, 0.5, 0.7);
        SkillFx.sparks(level, V.up(c, 0.6), FIRE, 12, 4);
        SkillFx.play(level, "mob.evocation_illager.prepare_summon", c, 0.8, 1.0);
        SkillFx.play(level, "mob.warden.emerge", c, 1.3, 0.7);
        SkillFx.play(level, "particle.soul_escape", c, 0.7, 1.0);
        return true;
    }

    // ------------------------------------------------------------ 나글파르의 출항

    /**
     * 나글파르의 출항: 짙은 녹색 안개 · 보는 쪽 start 칸부터 length 칸에 손발톱 배 (입체 naglfar) 가 땅을 가르고 솟음 (rise 틱 — emergeAt 틱에 배 밑의 적 ram · 띄움) →
     * sail 틱 동안 travel 칸 나아감 (뱃머리가 닿는 적 ram — 적마다 한 번 · 폭 width) → jumpAt 틱 선원 crew 명이 갑판에서 뛰어내려 landAt 틱 적 곁에 내려앉음 (적에게
     * 돌아가며 — land × 수, 같은 적은 한 번에 · 숫자 여러 줄) → 배가 땅속으로 가라앉고 finalAt 틱 갈라진 땅 (길 전체 — 폭 width) 에서 녹색 혼불이 치솟음 (final — 큰 숫자 ·
     * 띄움) · 선원은 stay 틱 더 싸움 (NecroCrew — 궁극기의 선원)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 r = V.rightOf(f);
        double ground = c0.y;
        int rise = (int) k.num("rise", 14);
        int emergeAt = (int) k.num("emergeAt", 10);
        int sail = (int) k.num("sail", 20);
        int jumpAt = (int) k.num("jumpAt", 22);
        int landAt = (int) k.num("landAt", 44);
        int finalAt = (int) k.num("finalAt", 60);
        double start = k.num("start", 1);
        double length = k.num("length", 8);
        double travel = k.num("travel", 12);
        double half = k.num("width", 5) / 2;
        int crewN = Math.max(0, (int) k.num("crew", 6));
        int stay = (int) k.num("stay", 120);
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
        // 솟음: 배 둘레 땅이 갈라지고 녹색 불길 · 안개 · 해골 비명 · 소리
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
        Kit.shakeNear(level0, shipC, 16, 0.3, 0.8);
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
        // 선원이 뛰어내림 (jumpAt): 갑판 (배의 그때 자리) 에서 적 곁으로 (landAt 틱에 내려앉음)
        List<NecroCrew.Member> crew = new ArrayList<>();
        RuneKit.later(jumpAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            double u = JobMove.ease((jumpAt - rise) / (double) sail, JobMove.INOUT);
            Vec3 shipNow = V.add(shipC, f, travel * u);
            List<LivingEntity> foes = new ArrayList<>();
            for (LivingEntity e : RuneKit.nearestTo(Kit.around(sp, V.add(c0, f, (start + length + travel) / 2), (start + length + travel) / 2 + 2, 4), c0)) {
                double s = V.dot(V.sub(e.position(), c0), f);
                double lat = Math.abs(V.dot(V.sub(e.position(), c0), r));
                if (s >= -2 && s <= start + length + travel + 2 && lat <= half + 2.5) foes.add(e);
            }
            Map<UUID, Integer> count = new LinkedHashMap<>();
            Map<UUID, LivingEntity> byId = new LinkedHashMap<>();
            Map<UUID, int[]> ringIdx = new java.util.HashMap<>();
            int flight = Math.max(4, landAt - jumpAt);
            for (int i = 0; i < crewN; i++) {
                Vec3 deck = V.up(V.add(V.add(shipNow, f, (i - (crewN - 1) / 2.0) * 1.0), r, (i % 2 == 0 ? 0.6 : -0.6)), 1.4);
                Vec3 land;
                LivingEntity e = foes.isEmpty() ? null : foes.get(i % foes.size());
                if (e != null) {
                    int idx = ringIdx.computeIfAbsent(e.getUUID(), x -> new int[1])[0]++;
                    double a = Math.toRadians(idx * 60 + 30);
                    Vec3 back = Math.hypot(e.getX() - c0.x, e.getZ() - c0.z) > 0.3 ? V.flat(V.sub(c0, e.position())) : V.scale(f, -1);
                    Vec3 off = V.turnY(back, Math.toDegrees(a));
                    land = NecroCrew.standAt(d, V.add(e.position(), off, Kit.bodyPad(e) + 0.9));
                    count.merge(e.getUUID(), 1, Integer::sum);
                    byId.put(e.getUUID(), e);
                } else {
                    land = NecroCrew.standAt(d, V.add(V.add(c0, f, start + 2 + 2.2 * i), r, (i % 2 == 0 ? 1.6 : -1.6)));
                }
                float yaw = e != null ? (float) Math.toDegrees(Math.atan2(-(e.getX() - land.x), e.getZ() - land.z)) : sp.getYRot();
                NecroCrew.Member m = NecroCrew.crewAt(sp, deck, yaw, stay + finalAt - jumpAt + 20);
                if (m == null) continue;
                crew.add(m);
                Vec3 from = deck;
                Vec3 to = land;
                double peak = 2.2 + 0.3 * (i % 3);
                for (int tt = 1; tt <= flight; tt++) {
                    double q = tt / (double) flight;
                    RuneKit.later(tt, () -> {
                        if (m.x.isRemoved()) return;
                        Vec3 p = RuneKit.leapPoint(from, to, peak, q);
                        m.x.setPos(p.x, p.y, p.z);
                    });
                }
                RuneKit.later(i, () -> ghostfire(d, from, 1.0, 0.3, 0.7));
            }
            SkillFx.play(d, "entity.zombie.ambient", shipNow, 0.5, 1.2);
            SkillFx.play(d, "entity.skeleton.ambient", shipNow, 0.6, 1.0);
            // 내려앉음 (landAt): 땅에 섬 (아직 스킬이 움직임) · 녹색 충격 고리 · 갈라진 땅 → 적마다 land × 덮친 선원 수 (한 번에 · 숫자 여러 줄)
            RuneKit.later(flight, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel dd = sp.level();
                for (NecroCrew.Member m : crew) {
                    if (m.x.isRemoved()) continue;
                    Vec3 g = m.x.position();
                    NecroCrew.settle(m, g);
                    m.x.noPhysics = true;
                    m.x.setNoGravity(true);
                    SkillFx.shock(dd, V.up(g, 0.1), FIRE, 0.9, 0.35);
                    crack(dd, g, 1.1, 1.0);
                    ghostfire(dd, g, 1.3, 0.35, 0.8);
                    m.x.swingAxe();
                }
                SkillFx.play(dd, "mace.smash_ground", V.add(c0, f, 4), 0.7, 1.0);
                SkillFx.play(dd, "entity.zombie.attack_wooden_door", V.add(c0, f, 4), 0.7, 0.8);
                Kit.shakeNear(dd, V.add(c0, f, 4), 12, 0.25, 0.3);
                for (var en : count.entrySet()) {
                    LivingEntity e = byId.get(en.getKey());
                    int n = en.getValue();
                    JobKit.strikeSplit(sp, e, j, P, k.num("land", 3) * n, n, 2, true, false, life, 0, x -> {
                        if (RuneKit.fxQuota("jobCrewLand", 6)) JobFx.hit(e, j, 1.1);
                    });
                }
            });
        });
        JobKit.motionLater(sp, "necromancer_ult:jump", jumpAt - 2, life);
        // 배가 가라앉음 (rise + sail ~ finalAt − 4): 앞으로 조금 더 가며 땅속으로
        RuneKit.later(rise + sail, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            mmorpg.fabric.Fx3d.move(sp.level(), shipId, sinkEnd, Math.max(0.2, (finalAt - 4 - rise - sail) / 20.0), "in");
            SkillFx.play(sp.level(), "entity.warden.dig", sailEnd, 0.6, 1.2);
            JobFx.smoke(sp.level(), V.up(sailEnd, 0.8), 3.0, DEEP, FIRE, 1.0, Opt.of("alpha", 0.75));
        });
        // 마지막: 갈라진 땅 (길 전체) 에서 녹색 혼불이 치솟음 · 해골 비명 · 선원이 함께 내려침 → final (큰 숫자 · 띄움)
        JobKit.motionLater(sp, "necromancer_ult:final", finalAt - 4, life);
        RuneKit.later(finalAt - 6, () -> {
            for (NecroCrew.Member m : crew) if (!m.x.isRemoved()) m.x.swingAxe();
        });
        RuneKit.later(finalAt, () -> {
            if (!RuneKit.sameLife(sp, life)) {
                for (NecroCrew.Member m : crew) NecroCrew.crumble(m, false);
                return;
            }
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
            RuneKit.shakeAround(d, V.add(c0, f, endS / 2), 20, 0.45, 0.5);
            SkillFx.play(d, "random.explode", V.add(c0, f, 4), 0.7, 1.2);
            SkillFx.play(d, "mob.wither.shoot", V.add(c0, f, 4), 0.6, 1.0);
            SkillFx.play(d, "particle.soul_escape", V.add(c0, f, 4), 0.6, 1.2);
            for (LivingEntity e : Kit.around(sp, V.add(c0, f, endS / 2), endS / 2 + half, 4)) {
                double s = V.dot(V.sub(e.position(), c0), f);
                double lat = Math.abs(V.dot(V.sub(e.position(), c0), r));
                if (s < -0.5 || s > endS + 0.5 || lat > half + 0.5 + Kit.bodyPad(e)) continue;
                JobKit.strike(sp, e, j, P, k.num("final", 24), true, true, life, 0, x -> {
                    if (JobKit.pushable(e)) Kit.knockback(e, 0, 0, 0.55);
                    if (RuneKit.fxQuota("jobNaglfar", 6)) JobFx.hit(e, j, 1.5);
                });
            }
            // 선원을 놓아 줌 (stay 틱 더 — 이제 망자의 군대 틀이 움직임)
            for (NecroCrew.Member m : crew) NecroCrew.release(m, m.x.position(), stay);
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
                if (RuneKit.fxQuota("jobNaglfarRam", 6)) JobFx.hit(e, j, 1.3);
            }
        });
    }
}
