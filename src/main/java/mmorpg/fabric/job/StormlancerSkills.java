package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 창술사 각성 스톰 랜서 (재설계안 2판 — 2026-11-06 사용자 "다음 작업", jobs.json advance stormlancer — 옥빛 바람 · 흰빛, 특성 「창끝」 + 바람 — JobTraits11):
 * 크로스 게일 · 게일 드라이브 · 템페스트 드릴 · 렌딩 게일 · 아이 오브 더 스톰 — 1차와 같은 매커니즘 (중거리 · 직선 · 연타 찌르기) 의 새 스킬 다섯. 몸 쓰는 직업이라
 * 제 몸 · 제 창 동작만 (바람 창 · 소용돌이 · 폭풍은 창끝에서 나가는 그림). 앞으로 눕는 그림은 1인칭 · 뒤 3인칭에서 보는 쪽과 나란해 보이지 않으므로 (1차 창술사와
 * 같은 까닭) 카메라를 보는 바람 소용돌이 (rfx_jswirl) · 세운 고리 · 번쩍을 길 위에 둠.
 */
final class StormlancerSkills {
    private StormlancerSkills() {}

    /** 흙먼지 · 흰 바람 */
    private static final double[] DUST = {0.66, 0.6, 0.5};
    private static final double[] WHITE = {0.95, 1.0, 0.98};

    static void register() {
        JobSystem.register("stormlancer_cross", StormlancerSkills::cross);
        JobSystem.register("stormlancer_drive", StormlancerSkills::drive);
        JobSystem.register("stormlancer_drill", StormlancerSkills::drill);
        JobSystem.register("stormlancer_rend", StormlancerSkills::rend);
        JobSystem.register("stormlancer_ult", StormlancerSkills::ult);
    }

    /** 가슴 높이 (찌르기가 나가는 자리) */
    private static Vec3 chest(ServerPlayer sp) {
        return V.up(sp.position(), 1.15);
    }

    private static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, V.up(p, 0.6), p.y), p.z);
    }

    /**
     * 바람 창 (날아감): start → end 를 speed 칸/초로 — 일섬 줄 · 날아가는 바람 소용돌이 (카메라를 봄) · 길 위의 세운 바람 고리 (보는 쪽을 봄 — 1인칭 · 뒤 3인칭) · 끝의
     * 번쩍. size = 소용돌이 반 크기
     */
    static void lance(ServerLevel level, ServerPlayer sp, Jobs.Job j, Vec3 start, Vec3 end, double size, double speed, double life) {
        Vec3 d = V.sub(end, start);
        double len = V.len(d);
        if (len < 0.3) return;
        Vec3 u = V.scale(d, 1 / len);
        double secs = len / Math.max(1, speed);
        JobFx.line(level, start, end, j, life, Opt.of("alpha", 0.9));
        JobFx.swirl(level, start, size, secs + 0.1, Opt.of("vel", V.scale(u, speed), "spinRate", 900.0, "alpha", 0.95), JobKit.glow(j), JobKit.core(j));
        for (double t = 1.6; t < len - 0.4; t += 2.1) {
            Vec3 p = V.add(start, u, t);
            double s = size * (0.5 + 0.25 * t / len);
            RuneKit.later(Math.round(t / speed * 20), () -> JobFx.ringFacing(level, p, s, j, V.scale(u, -1), 0.24, Opt.of("alpha", 0.65)));
        }
        RuneKit.later(Math.round(secs * 20), () -> {
            JobFx.stab(level, end, size * 0.75, j, 0.22, Opt.empty());
            JobFx.swirl(level, end, size * 0.9, 0.28, Opt.of("alpha", 0.8), JobKit.glow(j), JobKit.core(j));
        });
    }

    /** 앞 1인칭 번쩍 (손 앞 — 찌른 쪽 side 칸 · 크기 size) */
    private static void eyeStab(ServerPlayer sp, Jobs.Job j, double side, double size) {
        Vec3 look = sp.getViewVector(1F);
        Vec3 at = V.add(V.add(V.add(sp.getEyePosition(), look, 1.6), JobFx.screenUp(look), -0.25), V.rightOf(V.flat(look)), side);
        JobFx.stab(sp.level(), at, size, j, 0.18, JobFx.only1p(sp).with("vel", RuneKit.bodyVel(sp)));
    }

    // ------------------------------------------------------------ 크로스 게일

    /**
     * 크로스 게일 (롤 갈리오 Q — 두 바람이 만나 소용돌이): thrustA 틱에 왼쪽 · thrustB 틱에 오른쪽으로 비껴 찔러 바람 창 둘을 날림 (가슴 양옆 side 칸에서 앞 focus 칸
     * — 창끝 — 을 지나 len 칸까지 · 폭 width, 줄의 적마다 gust — 바람 창이 닿는 틱) → 둘째가 엇갈린 자리에 닿으면 소용돌이가 점점 커지다 crossAt 틱에 터짐 (둘레 radius 칸
     * cross · X 번쩍). 엇갈린 자리의 적은 셋 다 맞음 (같은 적은 서버가 10틱씩 띄움)
     */
    static boolean cross(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 f = Kit.facingOf(sp);
        Vec3 r = V.rightOf(f);
        Vec3 c = chest(sp);
        double len = k.num("len", 9);
        double side = k.num("side", 1.1);
        double half = k.num("width", 1.2) / 2;
        int aAt = Math.max(0, (int) k.num("thrustA", 2));
        int bAt = Math.max(aAt + 1, (int) k.num("thrustB", 6));
        int xAt = Math.max(bAt + 10, (int) k.num("crossAt", 22));
        double speed = 45;
        // 엇갈리는 자리: 앞 focus 칸 (벽이면 벽 앞)
        double reach = Math.max(2.0, Math.min(k.num("focus", 6.5), RuneKit.rayLength(level0, c, f, k.num("focus", 6.5) + 0.5) - 0.3));
        Vec3 fp = V.add(c, f, reach);
        // 창끝에 감기는 바람 (예비 — 0 ~ thrustA)
        JobFx.swirl(level0, V.add(RuneKit.handOf(sp).hand(), f, 1.0), 0.45, 0.25, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), JobKit.glow(j), JobKit.core(j));
        for (int s : new int[] {-1, 1}) {
            int at = s < 0 ? aAt : bAt;
            Vec3 start = V.add(c, r, side * s);
            Vec3 dir = V.unit(V.sub(fp, start));
            double can = Math.max(1.5, RuneKit.rayLength(level0, start, dir, len));
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                lance(lv, sp, j, V.add(start, dir, 0.5), V.add(start, dir, can), 0.8, speed, 0.32);
                JobFx.stab(lv, V.add(start, dir, 0.9), 0.55, j, 0.2, JobFx.hide1p(sp));
                eyeStab(sp, j, 0.4 * s, 0.36);
                SkillFx.play(lv, "item.spear.attack_miss", sp.position(), 1.15 + 0.15 * s, 0.9);
                SkillFx.play(lv, "entity.breeze.shoot", sp.position(), 1.3 + 0.1 * s, 0.55);
                int n = 0;
                for (Kit.LineHit lh : Kit.lineHits(sp, start, dir, can, half)) {
                    LivingEntity e = lh.e();
                    if (n++ >= 8) break;
                    JobKit.strike(sp, e, j, P, k.num("gust", 2.0), false, false, life, (int) Math.round(lh.t() / speed * 20), d -> {
                        if (!(e.level() instanceof ServerLevel el) || !RuneKit.fxQuota("jobCrossHit", 6)) return;
                        JobFx.hit(e, j, 0.9);
                        JobFx.swirl(el, Kit.bodyToward(e, sp.getEyePosition(), dir), 0.55 + Kit.bodyPad(e) * 0.3, 0.3, Opt.of("vel", RuneKit.entVel(e)), JobKit.glow(j),
                                JobKit.core(j));
                    });
                }
            });
        }
        // 엇갈린 자리: 둘째 바람이 닿은 뒤부터 소용돌이가 점점 커짐 (3틱마다) → crossAt 에 터짐
        int meet = bAt + (int) Math.round(reach / speed * 20) + 1;
        for (int t = meet; t < xAt; t += 3) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                double u = (tt - meet) / (double) Math.max(1, xAt - meet);
                JobFx.swirlLoop(lv, fp, 0.8 + 1.0 * u, 0.2, Opt.of("alpha", 0.85, "spinRate", 720.0 + 600 * u), JobKit.glow(j), JobKit.core(j));
                if (tt == meet) {
                    JobFx.ringFacing(lv, fp, 1.1, j, V.scale(f, -1), 0.3, Opt.of("alpha", 0.85));
                    SkillFx.play(lv, "entity.breeze.idle_ground", fp, 1.4, 0.6);
                }
                if ((tt - meet) % 6 == 0) SkillFx.sparks(lv, fp, WHITE, 3, 2.5);
            });
        }
        RuneKit.later(xAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            double rad = k.num("radius", 1.8);
            JobFx.swirl(lv, fp, 2.3, 0.35, Opt.of("alpha", 1.0, "spinRate", 1200.0), JobKit.glow(j), JobKit.core(j));
            JobFx.cross(lv, fp, 1.5, j, 0.32, Opt.empty());
            JobFx.burst(lv, fp, 1.4, j, 0.32, Opt.empty());
            JobFx.ringFacing(lv, fp, rad * 1.15, j, V.scale(f, -1), 0.3, Opt.of("alpha", 0.9));
            SkillFx.sparks(lv, fp, JobKit.glow(j), 12, 6.0);
            SkillFx.play(lv, "entity.breeze.wind_burst", fp, 1.0, 1.0);
            SkillFx.play(lv, "item.trident.riptide_3", fp, 1.3, 0.6);
            for (LivingEntity e : Kit.around(sp, fp, rad, 2.0)) {
                JobKit.strike(sp, e, j, P, k.num("cross", 3.25), false, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.2));
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 게일 드라이브

    /** 게일 드라이브 바람 표식: 주인 → (지나간 적 → 끝나는 틱) */
    private static final Map<UUID, Map<UUID, Long>> DRIVE = new HashMap<>();

    /**
     * 게일 드라이브 (롤 야스오 E): 바람을 타고 보는 쪽 dist 칸을 steps 틱에 꿰뚫고 지나감 (벽 앞까지 — 길 위 폭 passWidth 의 적은 지나가는 틱에 pass · 바람 표식
     * markFor 틱) → gap 틱 뒤부터 window 틱 안에 다시 누르면 ({@link #driveBack}) 돌아서며 표식이 남은 적마다 바람 창. 묶였거나 타고 있으면 쓰지 않음
     */
    static boolean drive(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (JobRecast.stage(sp, k.id()) >= 1) return driveBack(sp, j, k, P);
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        Vec3 f = Kit.facingOf(sp);
        double can = RuneKit.dashDistance(sp, f, k.num("dist", 6), 0.3, 1.5);
        if (can < 1.5) {
            Notify.notify(sp, "§c" + k.name() + " §7— 앞이 막혔습니다", 30);
            return false;
        }
        String life = RuneKit.lifeToken(sp);
        int steps = Math.max(2, (int) k.num("steps", 5));
        Vec3 start = sp.position();
        Vec3 end = V.add(start, f, can);
        RpgCombat.setSteadfast(sp, steps + 4);
        JobMove.run(sp, JobMove.to(0, steps, JobMove.at(end), JobMove.OUT));
        // 떠난 자리 바람 고리 · 흙먼지 · 1인칭 빠른 줄 · 소리
        JobFx.wind(level0, V.up(start, 0.1), 1.8, j, 0.5, Opt.empty());
        SkillFx.sparks(level0, V.up(start, 0.2), DUST, 6, 2.5);
        JobFx.speed(sp, j, 0.4, 0.85);
        SkillFx.play(level0, "mob.breeze.jump", start, 1.2, 0.9);
        SkillFx.play(level0, "item.trident.riptide_2", start, 1.4, 0.7);
        Vec3[] prev = {V.up(start, 1.0)};
        for (int t = 1; t <= steps; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 now = V.up(sp.position(), 1.0);
                JobFx.dash(lv, prev[0], now, j, 0.3, JobFx.hide1p(sp).with("alpha", 0.9));
                JobFx.swirl(lv, V.add(now, f, 0.6), 0.75, 0.22, JobFx.hide1p(sp).with("alpha", 0.75), JobKit.glow(j), JobKit.core(j));
                prev[0] = now;
                if (tt == steps) {
                    JobFx.wind(lv, V.up(sp.position(), 0.1), 2.0, j, 0.45, Opt.empty());
                    SkillFx.sparks(lv, V.up(sp.position(), 0.2), DUST, 6, 2.5);
                }
            });
        }
        // 지나간 적: 지나가는 틱에 pass · 바람 표식
        Map<UUID, Long> marks = new HashMap<>();
        DRIVE.put(sp.getUUID(), marks);
        long until = JobKit.now() + (long) k.num("markFor", 70);
        int n = 0;
        for (Kit.LineHit lh : Kit.lineHits(sp, V.up(start, 1.0), f, can + 0.5, k.num("passWidth", 1.4) / 2)) {
            LivingEntity e = lh.e();
            if (n++ >= 8) break;
            int at = Math.max(1, Math.min(steps, (int) Math.ceil(lh.t() / can * steps)));
            marks.put(e.getUUID(), until);
            JobKit.strike(sp, e, j, P, k.num("pass", 1.45), false, false, life, at, d -> {
                JobFx.hit(e, j, 0.9);
                if (e.level() instanceof ServerLevel el) {
                    JobFx.swirl(el, Kit.bodyPoint(e), 0.75 + Kit.bodyPad(e) * 0.4, 0.4, Opt.of("vel", RuneKit.entVel(e)), JobKit.glow(j), JobKit.core(j));
                    SkillFx.play(el, "entity.breeze.wind_burst", e.position(), 1.6, 0.5);
                }
            });
        }
        // 표식 그림 (8틱마다 — 적 몸에 감긴 바람)
        int window = Math.max(20, (int) k.num("window", 60));
        for (int t = steps + 2; t < window + steps; t += 8) {
            RuneKit.later(t, () -> {
                if (DRIVE.get(sp.getUUID()) != marks || !RuneKit.sameLife(sp, life)) return;
                long now = JobKit.now();
                for (Map.Entry<UUID, Long> en : marks.entrySet()) {
                    if (en.getValue() < now) continue;
                    Entity e = sp.level().getEntity(en.getKey());
                    if (e instanceof LivingEntity le && le.isAlive() && !le.isRemoved()) {
                        JobFx.swirlLoop(sp.level(), Kit.bodyPoint(le), 0.6 + Kit.bodyPad(le) * 0.4, 0.42, Opt.of("vel", RuneKit.entVel(le), "alpha", 0.6), JobKit.glow(j),
                                JobKit.core(j));
                    }
                }
            });
        }
        JobRecast.open(sp, k, 1, window, (int) k.num("gap", 10), () -> DRIVE.remove(sp.getUUID(), marks));
        JobSystem.note(sp, "§b다시 누르면 돌아서며 바람 창 §7(" + window / 20.0 + "초)");
        return true;
    }

    /** 게일 드라이브 2단계: 표식이 남은 적들 쪽으로 돌아서 stabAt 틱에 적마다 바람 창 (lance · 밀쳐냄 — 표식이 없으면 앞으로 바람 창 하나) */
    private static boolean driveBack(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        JobRecast.close(sp, k.id());
        JobRecast.motion(sp, k.id() + ":back");
        Map<UUID, Long> marks = DRIVE.remove(sp.getUUID());
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        List<LivingEntity> foes = new ArrayList<>();
        long now = JobKit.now();
        double range = k.num("range", 14);
        if (marks != null) {
            for (Map.Entry<UUID, Long> en : marks.entrySet()) {
                if (en.getValue() < now) continue;
                Entity e = level.getEntity(en.getKey());
                if (e instanceof LivingEntity le && le.isAlive() && !le.isRemoved() && le.distanceTo(sp) <= range) foes.add(le);
            }
        }
        // 돌아섬: 표식이 남은 적들의 가운데를 봄
        if (!foes.isEmpty()) {
            Vec3 m = Vec3.ZERO;
            for (LivingEntity e : foes) m = V.add(m, Kit.bodyPoint(e), 1.0);
            m = V.scale(m, 1.0 / foes.size());
            float[] rot = Kit.lookRotation(sp.getEyePosition(), m);
            Kit.moveTo(sp, sp.position(), rot[0], Mth.clamp(rot[1], -25F, 40F));
        }
        int stabAt = Math.max(1, (int) k.num("stabAt", 2));
        RpgCombat.setSteadfast(sp, stabAt + 4);
        JobFx.charge(sp, j, new Vec3(0.3, 1.2, 0.4), stabAt, 0.45);
        SkillFx.play(level, "entity.breeze.charge", sp.position(), 1.3, 0.6);
        RuneKit.later(stabAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = chest(sp);
            SkillFx.play(lv, "item.trident.riptide_3", sp.position(), 1.2, 0.8);
            SkillFx.play(lv, "entity.breeze.shoot", sp.position(), 1.1, 0.7);
            eyeStab(sp, j, 0, 0.45);
            if (foes.isEmpty()) {
                Vec3 f = Kit.facingOf(sp);
                lance(lv, sp, j, V.add(c, f, 0.6), V.add(c, f, Math.max(1.5, RuneKit.rayLength(lv, c, f, 6))), 0.85, 50, 0.3);
                return;
            }
            for (LivingEntity e : foes) {
                if (!e.isAlive() || e.isRemoved()) continue;
                Vec3 b = Kit.bodyPoint(e);
                Vec3 d = V.unit(V.sub(b, c));
                lance(lv, sp, j, V.add(c, d, 0.6), b, 0.9, 50, 0.3);
                int dl = (int) Math.round(c.distanceTo(b) / 50 * 20);
                JobKit.strike(sp, e, j, P, k.num("lance", 2.9), false, false, life, dl, x -> {
                    JobImpact.hit(sp, e, j, 1.1);
                    if (e.level() instanceof ServerLevel el) JobFx.stab(el, Kit.bodyToward(e, sp.getEyePosition(), d), 0.8 + Kit.bodyPad(e) * 0.3, j, 0.25, Opt.empty());
                    if (JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), sp.position()), k.num("knock", 0.6), 0.25);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 템페스트 드릴

    /** a 를 b 쪽으로 (수평) 많아야 maxRad 라디안 돌림 */
    private static Vec3 turnToward(Vec3 a, Vec3 b, double maxRad) {
        double aa = Math.atan2(a.z, a.x);
        double bb = Math.atan2(b.z, b.x);
        double d = Mth.wrapDegrees(Math.toDegrees(bb - aa));
        double step = Math.max(-Math.toDegrees(maxRad), Math.min(Math.toDegrees(maxRad), d));
        double t = aa + Math.toRadians(step);
        return new Vec3(Math.cos(t), 0, Math.sin(t));
    }

    /**
     * 템페스트 드릴 (롤 벨코즈 R): 창을 앞으로 겨누고 돌려 거대한 바람 드릴을 냄 (burstAt 틱까지 — 느려짐 · 밀려나지 않음, 드릴은 보는 쪽을 틱마다 turn 도까지 따라감):
     * 앞 len 칸 · 반지름 radius 의 적을 first 틱부터 매 틱 드릴 가운데 줄로 끌어당기고 (밀 수 있는 적만) every 틱마다 grind (숫자 세 줄) → burstAt 틱에 드릴이 터지며
     * burst · 앞으로 날려 보냄 (knock)
     */
    static boolean drill(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int first = Math.max(2, (int) k.num("first", 6));
        int every = Math.max(10, (int) k.num("every", 10));
        int grinds = Math.max(1, (int) k.num("grinds", 3));
        int burstAt = Math.max(first + (grinds - 1) * every + 10, (int) k.num("burstAt", 36));
        double len = k.num("len", 10);
        double rad = k.num("radius", 1.6);
        double pull = k.num("pull", 0.35);
        double turn = Math.toRadians(k.num("turn", 5));
        Vec3[] dir = {Kit.facingOf(sp)};
        RpgCombat.setSteadfast(sp, burstAt + 4);
        sp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, burstAt + 2, 2, false, false));
        JobFx.charge(sp, j, new Vec3(0.25, 1.15, 0.9), first, 0.6);
        SkillFx.play(level0, "entity.breeze.charge", sp.position(), 0.9, 0.8);
        SkillFx.play(level0, "item.trident.riptide_1", sp.position(), 0.8, 0.7);
        for (int t = 0; t <= burstAt; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                dir[0] = turnToward(dir[0], Kit.facingOf(sp), turn);
                Vec3 c = chest(sp);
                double can = Math.max(2, RuneKit.rayLength(lv, c, dir[0], len));
                if (tt >= first - 2 && tt % 2 == 0) drillFx(lv, sp, j, c, dir[0], can, rad, tt, first, burstAt);
                if (tt >= first) {
                    for (Kit.LineHit lh : Kit.lineHits(sp, c, dir[0], can, rad + 0.6)) pullToAxis(lh.e(), c, dir[0], pull * 0.35);
                }
                if (tt >= first && (tt - first) % 8 == 0 && tt < burstAt) SkillFx.play(lv, "block.grindstone.use", sp.position(), 1.4, 0.45);
            });
        }
        for (int i = 0; i < grinds; i++) {
            int at = first + i * every;
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 c = chest(sp);
                double can = Math.max(2, RuneKit.rayLength(lv, c, dir[0], len));
                SkillFx.play(lv, "entity.breeze.wind_burst", V.add(c, dir[0], can * 0.5), 1.5, 0.5);
                int n = 0;
                for (Kit.LineHit lh : Kit.lineHits(sp, c, dir[0], can, rad)) {
                    LivingEntity e = lh.e();
                    if (n++ >= 10) break;
                    JobKit.strikeSplit(sp, e, j, P, k.num("grind", 3.0), 3, 2, false, false, life, 0, d -> {
                        if (RuneKit.fxQuota("jobDrillHit", 6)) JobFx.hit(e, j, 0.85);
                    });
                }
            });
        }
        RuneKit.later(burstAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            sp.removeEffect(MobEffects.SLOWNESS);
            ServerLevel lv = sp.level();
            Vec3 c = chest(sp);
            double can = Math.max(2, RuneKit.rayLength(lv, c, dir[0], len));
            Vec3 tip = V.add(c, dir[0], can);
            for (double t = 1.5; t <= can; t += 1.7) {
                double u = t / can;
                JobFx.swirl(lv, V.add(c, dir[0], t), 1.3 - 0.5 * u + 0.6, 0.35, Opt.of("alpha", 0.95, "spinRate", 1400.0), JobKit.glow(j), JobKit.core(j));
            }
            JobFx.burst(lv, tip, 1.8, j, 0.35, Opt.empty());
            JobFx.ringFacing(lv, V.add(c, dir[0], can * 0.5), rad * 1.6, j, V.scale(dir[0], -1), 0.32, Opt.of("alpha", 0.9));
            eyeStab(sp, j, 0, 0.55);
            SkillFx.sparks(lv, tip, WHITE, 14, 7.0);
            SkillFx.play(lv, "entity.breeze.wind_burst", tip, 0.8, 1.0);
            SkillFx.play(lv, "item.trident.riptide_3", sp.position(), 1.0, 0.8);
            int n = 0;
            for (Kit.LineHit lh : Kit.lineHits(sp, c, dir[0], can, rad)) {
                LivingEntity e = lh.e();
                if (n++ >= 10) break;
                JobKit.strike(sp, e, j, P, k.num("burst", 4.05), false, false, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 1.3);
                    if (JobKit.pushable(e)) RuneKit.knock(e, dir[0], k.num("knock", 0.8), 0.3);
                });
            }
        });
        return true;
    }

    /** 드릴 가운데 줄 (c 에서 d 쪽) 로 적을 끌어당김 (수평 — 밀 수 있는 적만 · 한 틱에 많아야 0.3칸 · 막히면 그대로) */
    private static void pullToAxis(LivingEntity e, Vec3 c, Vec3 d, double frac) {
        if (!JobKit.pushable(e)) return;
        Vec3 p = e.position();
        double t = (p.x - c.x) * d.x + (p.z - c.z) * d.z;
        Vec3 q = new Vec3(c.x + d.x * t, p.y, c.z + d.z * t);
        Vec3 lat = V.sub(q, p);
        double l = V.len(lat);
        if (l < 0.15) return;
        double step = Math.min(0.3, l * frac);
        Kit.moveTo(e, V.add(p, V.scale(lat, 1 / l), step));
        e.resetFallDistance();
    }

    /** 드릴 그림 (2틱마다): 축을 따라 커졌다 작아지는 바람 소용돌이 (원뿔 — 끝이 작게) · 축을 감아 도는 바람 줄 둘 · 끝 번쩍 (4틱마다) · 1인칭 눈앞 소용돌이 */
    private static void drillFx(ServerLevel lv, ServerPlayer sp, Jobs.Job j, Vec3 c, Vec3 d, double can, double rad, int tt, int first, int burstAt) {
        double grow = Math.min(1, (tt - first + 3) / 6.0);
        Vec3 up = V.UP;
        Vec3 side = V.rightOf(d);
        for (double t = 1.2; t < can; t += 1.6) {
            double u = t / can;
            JobFx.swirlLoop(lv, V.add(c, d, t), (rad * 0.95 * (1 - 0.55 * u) + 0.2) * grow, 0.14, Opt.of("alpha", 0.8, "spinRate", 1300.0), JobKit.glow(j), JobKit.core(j));
        }
        for (int s = 0; s < 2; s++) {
            double a = Math.toRadians(tt * 42 + s * 180);
            Vec3 o0 = V.add(V.scale(side, Math.cos(a) * rad * 0.9), up, Math.sin(a) * rad * 0.9);
            Vec3 o1 = V.add(V.scale(side, Math.cos(a + 1.4) * rad * 0.3), up, Math.sin(a + 1.4) * rad * 0.3);
            JobFx.line(lv, V.add(V.add(c, d, 0.8), o0, grow), V.add(V.add(c, d, can), o1, grow), j, 0.16, Opt.of("alpha", 0.7));
        }
        if (tt % 4 == 0) JobFx.stab(lv, V.add(c, d, can), 0.6 * grow + 0.2, j, 0.18, Opt.empty());
        Vec3 look = sp.getViewVector(1F);
        JobFx.swirlLoop(lv, V.add(sp.getEyePosition(), look, 2.2), 0.55 * grow, 0.14, JobFx.only1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.55, "spinRate", 1300.0),
                JobKit.glow(j), JobKit.core(j));
    }

    // ------------------------------------------------------------ 렌딩 게일

    /**
     * 렌딩 게일 (롤 칼리스타 E — 태세 칸 몫 피해): 창을 휘둘러 앞 reach 칸 · arc 도 부채꼴 적마다 바람 창 start 개를 박음 (2틱) → window 틱 동안 창 평타가 맞을 때마다
     * 그 적에 하나 더 (JobTraits11 — 적마다 spears 까지) → gap 틱 뒤부터 다시 누르거나 창이 닫히면 박힌 바람 창을 한꺼번에 뽑아 찢음 ({@link #rip} — 창 하나당 spear)
     */
    static boolean rend(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (JobRecast.stage(sp, k.id()) >= 1) {
            JobRecast.close(sp, k.id());
            JobRecast.motion(sp, k.id() + ":rip");
            rip(sp, j, k, P);
            return true;
        }
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int window = Math.max(20, (int) k.num("window", 160));
        JobTraits11.rendOpen(sp, window);
        Vec3 f = Kit.facingOf(sp);
        double reach = k.num("reach", 5);
        double cosHalf = Math.cos(Math.toRadians(k.num("arc", 100) / 2));
        int startN = Math.max(0, (int) k.num("start", 2));
        // 휘두름: 앞 가로 큰 바람 검기 · 창끝 소용돌이 · 소리
        JobFx.bigWave(level0, sp, V.add(chest(sp), f, 1.2), f, 14, 1.7, 8, 0.3, j, Opt.empty());
        JobFx.swirl(level0, V.add(chest(sp), f, 1.6), 0.9, 0.35, JobFx.hide1p(sp), JobKit.glow(j), JobKit.core(j));
        SkillFx.play(level0, "entity.player.attack.sweep", sp.position(), 1.2, 0.9);
        SkillFx.play(level0, "entity.breeze.shoot", sp.position(), 0.9, 0.6);
        RuneKit.later(2, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            for (LivingEntity e : Kit.around(sp, sp.position(), reach, 2.5)) {
                Vec3 d = V.sub(e.position(), sp.position());
                double h = Math.hypot(d.x, d.z);
                if (h > 0.4 && (d.x * f.x + d.z * f.z) / h < cosHalf) continue;
                JobTraits11.embed(sp, e, j, k, startN);
            }
        });
        JobRecast.open(sp, k, 1, window, (int) k.num("gap", 10), () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobKit.motion(sp, k.id() + ":rip");
            rip(sp, j, k, P);
        });
        JobSystem.note(sp, "§b평타마다 바람 창이 박힘 · 다시 누르면 한꺼번에 뽑음 §7(" + window / 20 + "초)");
        return true;
    }

    /** 뽑아 찢음 (ripAt 틱 뒤): 적마다 박힌 수 × spear (숫자 그 수만큼 줄 · 가득이면 큰 숫자) · 뽑혀 날아오는 바람 창 · 터짐 */
    private static void rip(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        String life = RuneKit.lifeToken(sp);
        int ripAt = Math.max(1, (int) k.num("ripAt", 2));
        int full = (int) k.num("spears", 8);
        ServerLevel level0 = sp.level();
        JobFx.charge(sp, j, new Vec3(0.3, 1.3, -0.2), ripAt, 0.5);
        SkillFx.play(level0, "item.trident.return", sp.position(), 1.0, 0.9);
        RuneKit.later(ripAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Map<LivingEntity, Integer> all = JobTraits11.takeSpears(sp);
            ServerLevel lv = sp.level();
            Vec3 c = chest(sp);
            SkillFx.play(lv, "item.trident.riptide_3", sp.position(), 1.4, 0.8);
            if (all.isEmpty()) {
                JobFx.swirl(lv, V.add(c, Kit.facingOf(sp), 1.4), 0.7, 0.3, JobFx.hide1p(sp), JobKit.glow(j), JobKit.core(j));
                return;
            }
            for (Map.Entry<LivingEntity, Integer> en : all.entrySet()) {
                LivingEntity e = en.getKey();
                int n = en.getValue();
                Vec3 b = Kit.bodyPoint(e);
                Vec3 back = V.unit(V.sub(c, b));
                for (int i = 0; i < n; i++) {
                    double a = Math.toRadians(i * 137.5 + 20);
                    Vec3 out = V.unit(new Vec3(Math.cos(a) + back.x * 1.4, 0.4 + 0.15 * (i % 3), Math.sin(a) + back.z * 1.4));
                    JobFx.spear(lv, V.add(b, out, 0.9 + Kit.bodyPad(e) * 0.4), out, 0.5, 18, 0.28, j, Opt.of("alpha", 0.95, "fade", 0.3));
                }
                JobFx.burst(lv, b, 0.9 + 0.08 * n + Kit.bodyPad(e) * 0.4, j, 0.32, Opt.of("vel", RuneKit.entVel(e)));
                JobFx.swirl(lv, b, 0.8 + 0.1 * n + Kit.bodyPad(e) * 0.3, 0.35, Opt.of("vel", RuneKit.entVel(e), "spinRate", 1100.0), JobKit.glow(j), JobKit.core(j));
                SkillFx.sparks(lv, b, WHITE, 4 + n, 5.0);
                JobKit.strikeSplit(sp, e, j, P, k.num("spear", 2.175) * n, Math.max(1, Math.min(8, n)), 2, false, n >= full, life, 0,
                        d -> JobImpact.hit(sp, e, j, 1.0 + 0.1 * n));
            }
            JobSystem.note(sp, "§b바람 창 " + all.values().stream().mapToInt(Integer::intValue).sum() + "개를 뽑음");
        });
    }

    // ------------------------------------------------------------ 아이 오브 더 스톰

    /**
     * 아이 오브 더 스톰 (롤 잔나 R · 야스오 R): 폭풍 하늘 (rpg:ult_gale) · 그 자리에 버팀 (느려짐 · 밀려나지 않음) — 창을 높이 들어 폭풍을 모았다가 openAt 틱에 찌르면 겨눈
     * 적 (range 칸 — 없으면 앞 fallback 칸의 땅) 에 거대한 폭풍의 눈이 열림 (둘레 radius 칸 — 밀 수 있는 적을 매 틱 가운데로 끌어 lift 칸 띄워 돌림) → first 틱부터
     * every 틱마다 창을 찔러 바람 창을 폭풍 속에 꽂음 (pulse × pulses — 숫자 세 줄씩) → finalAt 틱에 거대한 폭풍 창으로 꿰찔러 터뜨림 (둘레 burstRadius 칸 final ·
     * 날려 보냄)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int openAt = Math.max(2, (int) k.num("openAt", 10));
        int first = Math.max(openAt + 1, (int) k.num("first", 14));
        int every = Math.max(10, (int) k.num("every", 10));
        int pulses = Math.max(1, (int) k.num("pulses", 5));
        int finalAt = Math.max(first + (pulses - 1) * every + 10, (int) k.num("finalAt", 70));
        double radius = k.num("radius", 5);
        double burstR = k.num("burstRadius", 6);
        double liftH = k.num("lift", 1.6);
        Vec3 f = Kit.facingOf(sp);
        LivingEntity tgt = Kit.pickTarget(sp, k.num("range", 12), 20);
        Vec3 eye;
        if (tgt != null) eye = ground(level0, tgt.position());
        else {
            double can = RuneKit.dashDistance(sp, f, k.num("fallback", 9), 0.3, 1.5);
            eye = ground(level0, V.add(sp.position(), f, Math.max(3, can)));
        }
        Ult.sky(level0, sp.position(), "rpg:ult_gale", finalAt + 30);
        RpgCombat.setSteadfast(sp, finalAt + 6);
        sp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, finalAt + 2, 3, false, false));
        // 0 ~ openAt: 창을 높이 들어 폭풍을 모음 — 창끝 빛 · 몸을 감는 회오리 · 둘레에서 모여 드는 바람
        JobFx.charge(sp, j, new Vec3(0.3, 2.5, 0.2), openAt, 0.9);
        JobFx.twister(sp, j, 3.2, openAt / 20.0 + 0.25, 0.6);
        SkillFx.play(level0, "entity.breeze.charge", sp.position(), 0.7, 1.0);
        SkillFx.play(level0, "item.trident.thunder", sp.position(), 1.6, 0.25);
        for (int q = 0; q < 6; q++) {
            double a = Math.toRadians(q * 60 + 15);
            Vec3 from = V.up(V.add(sp.position(), new Vec3(Math.cos(a), 0, Math.sin(a)), 3.2), 2.6);
            JobFx.line(level0, from, V.up(sp.position(), 2.5), j, 0.3, JobFx.hide1p(sp).with("alpha", 0.7));
        }
        // openAt: 찌름 → 폭풍의 눈이 열림
        RuneKit.later(openAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = chest(sp);
            Vec3 core = V.up(eye, 1.6);
            lance(lv, sp, j, V.add(c, V.unit(V.sub(core, c)), 0.6), core, 1.1, 45, 0.35);
            eyeStab(sp, j, 0, 0.5);
            JobFx.wind(lv, V.up(eye, 0.1), radius, j, 0.6, Opt.empty());
            JobFx.ring(lv, V.up(eye, 0.08), radius, j, false, 0.4, Opt.of("alpha", 0.9));
            SkillFx.play(lv, "entity.breeze.wind_burst", eye, 0.6, 1.0);
            SkillFx.play(lv, "item.trident.riptide_3", eye, 0.8, 0.9);
        });
        // openAt ~ finalAt: 폭풍 (2틱마다 그림 · 매 틱 끌어 띄워 돌림)
        for (int t = openAt; t <= finalAt; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                double grow = Math.min(1, (tt - openAt + 2) / 8.0);
                if (tt % 2 == 0) stormFx(lv, j, eye, radius, grow, tt);
                if (tt < finalAt) {
                    for (LivingEntity e : Kit.around(sp, eye, radius + 1.5, 6)) swirlPull(e, eye, liftH * grow, tt - openAt);
                }
                if ((tt - openAt) % 10 == 5) SkillFx.play(lv, "entity.breeze.idle_air", eye, 0.6, 0.8);
            });
        }
        for (int i = 0; i < pulses; i++) {
            int at = first + i * every;
            RuneKit.later(at - 2, () -> {
                if (RuneKit.sameLife(sp, life)) JobKit.motion(sp, k.id() + ":thrust");
            });
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 c = chest(sp);
                Vec3 core = V.up(eye, 1.4 + (at / 10 % 3) * 0.5);
                lance(lv, sp, j, V.add(c, V.unit(V.sub(core, c)), 0.6), core, 0.95, 60, 0.28);
                eyeStab(sp, j, 0.15 * ((at / 10) % 2 == 0 ? 1 : -1), 0.42);
                JobFx.swirl(lv, core, 1.6, 0.3, Opt.of("alpha", 0.9, "spinRate", 1500.0), JobKit.glow(j), JobKit.core(j));
                SkillFx.play(lv, "item.spear.attack_miss", sp.position(), 1.2, 0.8);
                SkillFx.play(lv, "entity.breeze.shoot", eye, 1.0, 0.7);
                for (LivingEntity e : Kit.around(sp, eye, radius, 6)) {
                    JobKit.strikeSplit(sp, e, j, P, k.num("pulse", 6), 3, 2, false, false, life, 0, d -> {
                        if (RuneKit.fxQuota("jobEyeHit", 6)) JobFx.hit(e, j, 1.0);
                    });
                }
            });
        }
        RuneKit.later(finalAt - 6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobKit.motion(sp, k.id() + ":final");
            JobFx.charge(sp, j, new Vec3(0.35, 1.3, -0.4), 6, 0.8);
        });
        RuneKit.later(finalAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            sp.removeEffect(MobEffects.SLOWNESS);
            ServerLevel lv = sp.level();
            Vec3 c = chest(sp);
            Vec3 core = V.up(eye, 1.6);
            Vec3 d = V.unit(V.sub(core, c));
            // 거대한 폭풍 창: 큰 바람 창 · 길 위 소용돌이 · 폭풍이 터짐 (큰 소용돌이 · 터짐 · 고리 · 둘레로 퍼지는 바람 검기)
            lance(lv, sp, j, V.add(c, d, 0.6), V.add(core, d, 2.5), 1.6, 70, 0.4);
            for (double t = 1.5; t < c.distanceTo(core); t += 1.5) JobFx.swirl(lv, V.add(c, d, t), 1.3, 0.3, Opt.of("alpha", 0.85, "spinRate", 1600.0), JobKit.glow(j), JobKit.core(j));
            eyeStab(sp, j, 0, 0.7);
            JobFx.swirl(lv, core, 4.0, 0.45, Opt.of("alpha", 1.0, "spinRate", 1800.0), JobKit.glow(j), JobKit.core(j));
            JobFx.burst(lv, core, 3.2, j, 0.4, Opt.empty());
            JobFx.ring(lv, V.up(eye, 0.1), burstR, j, false, 0.45, Opt.empty());
            JobFx.radialWaves(sp, j, eye, 8, 1.0, burstR + 1, 1.0, 1.4, 0.35, 0);
            SkillFx.shock(lv, V.up(eye, 0.1), JobFx.inner(j), burstR * 1.1, 0.5);
            SkillFx.sparks(lv, core, WHITE, 24, 9.0);
            SkillFx.play(lv, "entity.breeze.wind_burst", eye, 0.5, 1.0);
            SkillFx.play(lv, "entity.generic.explode", eye, 1.2, 0.6);
            SkillFx.play(lv, "item.trident.riptide_3", sp.position(), 0.9, 1.0);
            for (LivingEntity e : Kit.around(sp, eye, burstR, 7)) {
                JobKit.strike(sp, e, j, P, k.num("final", 28), false, true, life, 0, x -> {
                    JobImpact.hit(sp, e, j, 2.2);
                    if (JobKit.pushable(e)) {
                        Vec3 out = V.sub(e.position(), eye);
                        RuneKit.knock(e, V.len(out) < 0.2 ? d : out, k.num("knock", 1.0), 0.35);
                    }
                    e.resetFallDistance();
                });
            }
        });
        return true;
    }

    /** 폭풍의 눈 그림 (2틱마다): 땅에서 솟는 거대한 회오리 · 둘레 바람 고리 · 가운데 소용돌이 셋 (높이마다) · 둘레를 도는 바람 줄 */
    private static void stormFx(ServerLevel lv, Jobs.Job j, Vec3 eye, double radius, double grow, int tt) {
        JobFx.twisterAt(lv, eye, 7.5 * grow + 1, j, 0.16, Opt.of("alpha", 0.75));
        if (tt % 6 == 0) JobFx.wind(lv, V.up(eye, 0.12), radius * grow, j, 0.4, Opt.of("alpha", 0.8));
        for (int h = 0; h < 3; h++) {
            JobFx.swirlLoop(lv, V.up(eye, 1.0 + h * 1.7), (1.2 + h * 0.55) * grow, 0.14, Opt.of("alpha", 0.6, "spinRate", 900.0 + h * 300), JobKit.glow(j), JobKit.core(j));
        }
        double a0 = Math.toRadians(tt * 23);
        for (int q = 0; q < 3; q++) {
            double a = a0 + q * 2.1;
            double r = radius * (0.55 + 0.35 * Math.sin(tt * 0.3 + q)) * grow;
            Vec3 p0 = V.up(V.add(eye, new Vec3(Math.cos(a), 0, Math.sin(a)), r), 0.5 + q * 1.4);
            Vec3 p1 = V.up(V.add(eye, new Vec3(Math.cos(a + 0.9), 0, Math.sin(a + 0.9)), r * 0.9), 0.9 + q * 1.4);
            JobFx.line(lv, p0, p1, j, 0.18, Opt.of("alpha", 0.65));
        }
        if (tt % 4 == 0) SkillFx.sparks(lv, V.up(eye, 1.5), WHITE, 3, 4.0);
    }

    /** 폭풍의 눈으로 끌어 띄워 돌림 (밀 수 있는 적만): 가운데 쪽으로 · 한 틱에 0.35칸까지 · 둘레를 돌며 · 높이 liftH (막히면 그대로) */
    private static void swirlPull(LivingEntity e, Vec3 eye, double liftH, int t) {
        if (!JobKit.pushable(e)) return;
        Vec3 p = e.position();
        double dx = p.x - eye.x;
        double dz = p.z - eye.z;
        double r = Math.hypot(dx, dz);
        double a = Math.atan2(dz, dx) + Math.toRadians(9);
        double nr = Math.max(0.8, r - Math.min(0.35, Math.max(0.05, r * 0.12)));
        double y = Math.min(p.y + 0.25, eye.y + liftH + 0.25 * Math.sin(t * 0.4));
        if (p.y > eye.y + liftH + 0.5) y = p.y - 0.15;
        Vec3 to = new Vec3(eye.x + Math.cos(a) * nr, Math.max(eye.y, y), eye.z + Math.sin(a) * nr);
        Kit.moveTo(e, to);
        e.resetFallDistance();
    }
}
