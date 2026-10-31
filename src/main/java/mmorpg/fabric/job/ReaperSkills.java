package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.DamageNumbers;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.29.0 사신 직업 스킬 (jobs.json reaper — 청록 · 짙은 청록, 특성 「혼」): 갈퀴 베기 · 망령 걸음 · 혼백 폭발 · 사신 현신 · 황천문.
 * 피해 = 위력 P × 배율 (JobKit.strike), 맞힌 적마다 혼 +1 (혼백 폭발 제외 — JobTraits3.gainSouls).
 * 연출 = 메이플식 2D 그림 (JobFx — 큰 낫 초승달 · 혼불 · 그림자 연기 · 사신 그림자 · 명계의 문 · 망자의 손, 룬 문양 · 마법진은 쓰지 않음).
 */
final class ReaperSkills {
    private ReaperSkills() {}

    static void register() {
        JobSystem.register("reaper_reap", ReaperSkills::reap);
        JobSystem.register("reaper_wraith", ReaperSkills::wraith);
        JobSystem.register("reaper_burst", ReaperSkills::burst);
        JobSystem.register("reaper_avatar", ReaperSkills::avatar);
        JobSystem.register("reaper_ult", ReaperSkills::ult);
    }

    // ------------------------------------------------------------ 갈퀴 베기

    /**
     * 갈퀴 베기: 낫을 크게 swings 번 휘둘러 (gap 틱 간격 — 첫째는 왼쪽 → 오른쪽, 둘째는 반대로) 앞 reach 칸 (arc°) 의 적을 벰 (swing · 혼 +1) — 밀리는 적은 내 쪽으로
     * pull 칸쯤 끌려옴. 그림: 눈앞 큰 낫 초승달 · 베기 끝의 그림자 연기 · 맞은 적의 혼불
     */
    static boolean reap(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int swings = Math.max(1, (int) k.num("swings", 2));
        int gap = Math.max(1, (int) k.num("gap", 10));
        double reach = k.num("reach", 4);
        double arc = k.num("arc", 150);
        // 땅 마찰로 줄어드는 속도 — 처음 속도 × 약 2.2 칸을 미끄러짐
        double pullVel = k.num("pull", 1.3) / 2.2;
        String life = RuneKit.lifeToken(sp);
        // 0.50.0 동작: 휘두르며 앞으로 미끄러짐 (slide 칸)
        JobMove.run(sp, JobMove.to(0, (swings - 1) * gap + 3, JobMove.ahead(sp, k.num("slide", 1.5), 0), JobMove.OUT));
        for (int i = 0; i < swings; i++) {
            int ii = i;
            RuneKit.later(i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = sp.position();
                Vec3 f = Kit.facingOf(sp);
                JobFx.slash(sp, j, 2.4, 0.2, 2.3, ii % 2 == 0 ? 8.0 : 188.0, 0.28);
                // 베기 끝: 부채꼴 가장자리의 그림자 연기 다섯 (1인칭에서도 앞에 보임)
                for (int s = 0; s < 5; s++) {
                    double a = (s / 4.0 - 0.5) * arc * (ii % 2 == 0 ? 1 : -1);
                    Vec3 p = V.up(V.add(c, V.turnY(f, a), reach * 0.85), 0.9 + 0.15 * s);
                    RuneKit.later(s / 2, () -> JobFx.smoke(level, p, 0.45, j, 0.45, Opt.of("alpha", 0.7)));
                }
                SkillFx.play(level, "entity.player.attack.sweep", c, 0.55 + ii * 0.1, 1.0);
                SkillFx.play(level, "particle.soul_escape", c, 1.1, 0.6);
                int[] cnt = {0};
                for (LivingEntity e : RuneKit.coneTargets(sp, reach, arc, 10)) {
                    JobKit.strike(sp, e, j, P, k.num("swing", 3.125), false, false, life, 0, d -> {
                        JobTraits3.gainSouls(sp, j, 1, e);
                        if (JobKit.pushable(e) && e.distanceTo(sp) > 1.8) RuneKit.knock(e, V.sub(sp.position(), e.position()), pullVel, 0);
                        if (cnt[0]++ < 6) {
                            JobFx.hit(e, j, 1.0);
                            JobFx.smoke(level, Kit.bodyPoint(e), 0.55, j, 0.4, Opt.of("alpha", 0.75));
                        }
                    });
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 망령 걸음

    /**
     * 망령 걸음: ghost 틱 동안 망령 (받는 피해 없음 · 밀려나지 않음 · 몸이 투명 — 대신 청록 그림자) — 보는 쪽 dist 칸 (벽 · 낭떠러지 앞까지) 을 steps 틱에 지나가며
     * (적은 지나침) 길 (폭 width) 의 적마다 hit · 혼 +1 (지나가는 때에). 묶였거나 앞이 막혔으면 쓰지 않음
     */
    static boolean wraith(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double free = Kit.reachFlat(level, start, f, k.num("dist", 7));
        List<Kit.Spot> spots = free > 0.4 ? Kit.dashSpots(level, start, f, free) : List.of();
        if (spots.isEmpty()) {
            Notify.notify(sp, "§c" + k.name() + " §7— 앞이 막혔습니다", 30);
            return false;
        }
        double len = spots.get(spots.size() - 1).s();
        int steps = Math.max(1, (int) k.num("steps", 6));
        List<Vec3> marks = new ArrayList<>();
        for (int s = 1; s <= steps; s++) {
            double want = len * s / steps;
            Kit.Spot best = spots.get(0);
            for (Kit.Spot x : spots) if (Math.abs(x.s() - want) < Math.abs(best.s() - want)) best = x;
            marks.add(best.p());
        }
        int ghost = Math.max(steps, (int) k.num("ghost", 30));
        String life = RuneKit.lifeToken(sp);
        JobTraits3.ghostFor(sp, ghost);
        RpgCombat.setSteadfast(sp, ghost);
        Kit.noFall(sp, ghost + 10);
        sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ghost, 0, false, false, false));
        // 길의 적: 지나가는 때에 hit (한 번씩)
        Set<UUID> seen = new HashSet<>();
        for (Kit.LineHit lh : Kit.lineHits(sp, V.up(start, 1.0), f, len + 0.6, k.num("width", 1.6) / 2)) {
            LivingEntity e = lh.e();
            if (!seen.add(e.getUUID())) continue;
            int at = (int) Math.round(steps * Math.min(1, lh.t() / Math.max(0.5, len)));
            JobKit.strike(sp, e, j, P, k.num("hit", 3.75), false, false, life, at, d -> {
                JobTraits3.gainSouls(sp, j, 1, e);
                if (RuneKit.fxQuota("jobWraithHit", 6)) {
                    JobFx.hit(e, j, 1.1);
                    JobFx.soul(e.level() instanceof ServerLevel lv ? lv : level, Kit.bodyPoint(e), 0.3, j, 0.5, Opt.of("dir", V.UP, "speed", 1.2));
                }
            });
        }
        // 출발: 몸에서 빠져나오는 그림자 · 연기 · 소리
        RuneKit.shadeClone(sp, level, start, f, 1, JobKit.glow(j), JobKit.core(j), Opt.of("life", 0.5, "fade", 0.6, "alpha", 0.85));
        JobFx.smoke(level, V.up(start, 1.0), 1.0, j, 0.55, JobFx.hide1p(sp));
        // 1인칭: 눈앞 고리 · 빠른 줄이 몸과 같이 나아감 (한 틱에 len / steps 칸 — 걸음마다 그리면 다음 틱에 카메라 뒤로 지나가 보이지 않았음, 사진 검토)
        Vec3 dashVel = V.scale(f, len / steps * 20);
        JobFx.eyeRing(sp, j, 2.4, 0.8, steps / 20.0 + 0.15, dashVel);
        JobFx.speed(sp, j, steps / 20.0 + 0.05, 0.7, dashVel);
        SkillFx.play(level, "mob.vex.charge", start, 0.8, 0.9);
        SkillFx.play(level, "particle.soul_escape", start, 0.7, 1.0);
        for (int s = 0; s < marks.size(); s++) {
            Vec3 to = marks.get(s);
            int ss = s;
            RuneKit.later(s, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 prev = sp.position();
                if (!Kit.moveTo(sp, to)) return;
                // 망령: 지나온 자리의 청록 그림자 (몸 뒤 — 1인칭 카메라 밖) · 혼불 꼬리 · 1인칭 빠른 줄
                RuneKit.shadeClone(sp, d, prev, f, 1 + ss % 3, JobKit.glow(j), JobKit.core(j), Opt.of("life", 0.3, "fade", 0.6, "alpha", 0.75));
                JobFx.dash(d, V.up(prev, 1.0), V.up(to, 1.0), j, 0.3, JobFx.hide1p(sp));
                JobFx.soul(d, V.up(prev, 0.6 + Kit.rand() * 0.8), 0.14, j, 0.45, Opt.of("dir", V.UP, "speed", 0.8));
                if (ss == marks.size() - 1) JobFx.speed(sp, j, 0.25, 0.7);
            });
        }
        // 망령인 동안: 1인칭 화면 네 귀퉁이의 옅은 청록 연기 (암영과 같은 꼴 — 몸이 그림자인 동안)
        for (int t = steps; t < ghost; t += 8) {
            RuneKit.later(t, () -> {
                if (RuneKit.sameLife(sp, life) && JobTraits3.ghost(sp)) JobFx.cornerSmoke(sp, j, 0.55, 0.45, 0.4, RuneKit.bodyVel(sp));
            });
        }
        // 망령이 풀림
        RuneKit.later(ghost, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            MobEffectInstance inv = sp.getEffect(MobEffects.INVISIBILITY);
            if (inv != null && !inv.isInfiniteDuration() && inv.getDuration() <= 5) sp.removeEffect(MobEffects.INVISIBILITY);
            JobFx.smoke(sp.level(), V.up(sp.position(), 1.0), 0.8, j, 0.45, JobFx.hide1p(sp));
            SkillFx.play(sp.level(), "particle.soul_escape", sp.position(), 1.3, 0.6);
        });
        return true;
    }

    // ------------------------------------------------------------ 혼백 폭발

    /**
     * 혼백 폭발: 혼을 모두 씀 — 몸 둘레의 혼불이 몸으로 모였다가 (3틱) 둘레 radius 칸을 터뜨림 (base × (1 + per × 혼) — 혼이 가득이면 큰 숫자). 이 피해는 혼을 주지
     * 않음. 그림: 모이는 혼불 · 몸의 큰 터짐 · 땅의 둥근 베기 둘 · 맞은 적에서 솟는 혼불 · 화면 흔들림
     */
    static boolean burst(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int max = j.trait() == null ? 10 : Math.max(1, j.trait().max());
        int n = JobTraits3.takeSouls(sp);
        double mult = k.num("base", 8) * (1 + k.num("per", 0.05) * n);
        double r = k.num("radius", 6);
        boolean full = n >= max;
        String life = RuneKit.lifeToken(sp);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        int show = Math.max(3, Math.min(n, 10));
        for (int i = 0; i < show; i++) {
            double a = i * Math.PI * 2 / show;
            Vec3 from = new Vec3(c.x + Math.cos(a) * 1.7, c.y + 0.7 + (i % 3) * 0.35, c.z + Math.sin(a) * 1.7);
            JobFx.soulFly(level, from, V.up(c, 1.1), 11, i < n ? 0.17 : 0.11, j, Opt.empty());
        }
        SkillFx.play(level, "particle.soul_escape", c, 0.6, 1.0);
        // 0.50.0 동작: 영혼을 모으며 떠올랐다 (lift 칸) 내리꽂으며 폭발 (slamAt 틱 — 내려앉은 자리)
        int slamAt = (int) k.num("slamAt", 9);
        JobMove.run(sp, JobMove.to(0, slamAt - 3, JobMove.up(sp, k.num("lift", 1.5)), JobMove.OUT), JobMove.to(slamAt - 3, slamAt, JobMove.down(sp, c.y),
                JobMove.IN));
        RuneKit.later(slamAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 p = sp.position();
            JobFx.burst(d, V.up(p, 1.0), full ? 2.4 : 1.8, j, 0.4, JobFx.hide1p(sp));
            Vec3 look = sp.getViewVector(1F);
            JobFx.burst(d, V.add(V.add(sp.getEyePosition(), look, 2.4), JobFx.screenUp(look), -0.5), full ? 1.5 : 1.1, j, 0.35, JobFx.only1p(sp));
            JobFx.ring(d, V.up(p, 0.08), r, j, false, 0.4, Opt.empty());
            RuneKit.later(3, () -> JobFx.ring(d, V.up(p, 0.1), r * 0.55, j, true, 0.35, Opt.of("alpha", 0.85)));
            JobFx.smoke(d, V.up(p, 0.5), 1.6, j, 0.7, JobFx.hide1p(sp));
            SkillFx.sparks(d, V.up(p, 1.0), JobKit.glow(j), 14, 6);
            JobKit.shake(sp, 10, full ? 0.4 : 0.3, 0.3);
            SkillFx.play(d, "random.explode", p, 1.3, 0.7);
            SkillFx.play(d, "block.respawn_anchor.deplete", p, 1.2, 1.0);
            for (LivingEntity e : Kit.around(sp, p, r, 2.5)) {
                JobKit.strike(sp, e, j, P, mult, true, full, life, 0, x -> {
                    if (RuneKit.fxQuota("jobSoulBurst", 8)) {
                        JobFx.hit(e, j, 1.2);
                        JobFx.soul(d, Kit.bodyPoint(e), 0.3, j, 0.6, Opt.of("dir", V.UP, "speed", 2.0));
                    }
                });
            }
        });
        JobSystem.note(sp, "§3영혼 " + n + " §7→ +" + Math.round(k.num("per", 0.05) * n * 100) + "%" + (full ? " §3(가득)" : ""));
        return true;
    }

    // ------------------------------------------------------------ 사신 현신

    /**
     * 사신 현신: dur 틱 태세 — 등 뒤에 거대한 사신 그림자가 서서 낫 평타를 따라 휘두름, 넓게 베기 범위 · 비율이 range · ratio (JobTraits3.sweepMod). 그림: 땅에서 솟는
     * 그림자 · 연기 · 혼불 · 1인칭 눈앞 고리 · 글자, 태세 동안 등 뒤 그림자 (JobTraits3.stanceFx) · 평타마다 휘두름 (JobTraits3.avatarSwing)
     */
    static boolean avatar(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) k.num("dur", 240);
        JobTraits.startStance(sp, k, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 behind = JobTraits3.avatarAt(sp);
        JobTraits3.avatarRise(sp, j);
        JobFx.smoke(level, V.up(c, 0.3), 1.6, j, 0.8, JobFx.hide1p(sp));
        JobFx.smoke(level, V.up(behind, -0.4), 1.4, j, 0.7, JobFx.hide1p(sp).with("alpha", 0.7));
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            JobFx.soul(level, new Vec3(c.x + Math.cos(a) * 1.2, c.y + 0.3, c.z + Math.sin(a) * 1.2), 0.18, j, 0.7, Opt.of("dir", V.UP, "speed", 2.4));
        }
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.5);
        Ult.flash(level, c, JobKit.glow(j), Opt.of("radius", 4.0, "time", new double[] {0.03, 0.05, 0.35}), sp);
        DamageNumbers.showFace(sp, "§3§l" + k.name() + " §7— 넓게 베기 " + mmorpg.core.Js.str(k.num("range", 5)) + "칸 · " + Math.round(JobUpgrades.num(sp, k, "ratio", 0.6) * 100) + "%", 2.4, 0,
                -0.3);
        SkillFx.play(level, "mob.warden.emerge", c, 1.4, 0.6);
        SkillFx.play(level, "particle.soul_escape", c, 0.6, 1.0);
        return true;
    }

    // ------------------------------------------------------------ 황천문

    /**
     * 황천문: 조준한 곳 (range 칸) 에 명계의 문이 솟음 (first 틱) — 둘레 radius 칸의 적을 망자의 손이 붙잡음 (root 틱 묶음 · 보스는 둔화), first 틱부터 gap 틱마다
     * (drains 번) 혼을 빨아냄 (drain · 혼 +1 — 적 → 문으로 혼불) → 마지막 빨아냄 finalDelay 틱 뒤 문이 닫히며 큰 피해 (final — 큰 숫자)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        double range = k.num("range", 16);
        Vec3 aim = Kit.aimPoint(sp, range);
        Vec3 ground = new Vec3(aim.x, RuneKit.groundAt(level0, aim, sp.getY()), aim.z);
        double r = k.num("radius", 6);
        int drains = Math.max(1, (int) k.num("drains", 6));
        int gap = Math.max(1, (int) k.num("gap", 10));
        int first = Math.max(4, (int) k.num("first", 10));
        int root = (int) k.num("root", 60);
        int lastDrain = first + (drains - 1) * gap;
        int finalAt = lastDrain + (int) k.num("finalDelay", 15);
        String life = RuneKit.lifeToken(sp);
        // 0.50.0 동작: 낫을 치켜들고 떠올라 (lift 칸) 문이 열린 동안 떠 있다가, 문이 닫힐 때 내려침
        double ground0 = sp.getY();
        JobMove.run(sp, JobMove.to(0, 8, JobMove.up(sp, k.num("lift", 1.0)), JobMove.OUT), JobMove.hold(8, finalAt - 3), JobMove.to(finalAt - 3, finalAt + 1,
                JobMove.down(sp, ground0), JobMove.IN));
        // 문은 조준한 곳 뒤 (시전자에서 먼 쪽으로 2.5칸) 에 서고 시전자 눈에 가까우면 작게 — 낫은 가까이서 쓰는 무기라 눈앞 4칸에 높이 6칸 문이 서면
        // 화면을 다 덮었음 (사진 검토). 손 · 빨아냄 · 큰 피해는 그대로 조준한 곳 둘레
        Vec3 away = V.flat(V.sub(ground, sp.position()));
        Vec3 behind = V.add(ground, V.len(away) > 0.3 ? V.unit(away) : Kit.facingOf(sp), 2.5);
        Vec3 gateGround = new Vec3(behind.x, RuneKit.groundAt(level0, behind, ground.y), behind.z);
        double gs = 3.0 * RuneKit.nearK(sp, V.up(gateGround, 3.0), 10, 0.65);
        Vec3 gateAt = V.up(gateGround, gs);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Ult.sky(level0, sp.position(), "rpg:ult_void", finalAt + 25);
        // 0.47.0 입체 명계의 문: 땅에서 솟으며 문짝이 열림 (first 틱) → 소용돌이가 돎 → 닫힘 (Fx3d.anim) → 땅으로 가라앉음. 문은 시전자 쪽을 봄
        Vec3 gateFace = V.len(away) > 0.3 ? V.scale(V.unit(away), -1) : V.scale(Kit.facingOf(sp), -1);
        double gScale = gs * 2 / JobFx3d.GATE_H;
        int gateId = JobFx3d.spawn(level0, "gate", V.up(gateGround, -JobFx3d.GATE_H * gScale * 0.9), j, Opt.of("to", gateGround, "ctrl", V.up(gateGround, -0.3), "travel",
                Math.max(0.2, first / 20.0), "ease", "out3", "dir", V.UP, "face", gateFace, "scale", gScale, "life", finalAt / 20.0 + 0.6, "anim", "open", "animSpeed",
                0.5 / Math.max(0.2, first / 20.0), "fadeIn", 0.15, "fadeOut", 0.35, "glowAlpha", 0.4, "shine", 0.15));
        JobFx3d.guard(sp, life, new int[] {gateId}, finalAt + 10);
        RuneKit.later(first, () -> mmorpg.fabric.Fx3d.anim(sp.level(), gateId, "swirl", true, gateGround));
        JobFx.smoke(level0, V.up(ground, 0.4), 2.4, j, 0.9, Opt.empty());
        JobFx.ring(level0, V.up(ground, 0.08), r, j, false, 0.5, Opt.of("alpha", 0.8));
        JobFx.slash(sp, j, 2.2, 0.0, 1.8, 275.0, 0.25);
        JobFx.eyeRing(sp, j, 2.4, 1.1, 0.5);
        SkillFx.play(level0, "mob.warden.emerge", ground, 0.8, 1.0);
        SkillFx.play(level0, "block.end_portal.spawn", ground, 1.4, 0.4);
        // 열린 문 (소용돌이 — 10틱마다 다시) · 땅의 연기 · 혼불이 솟음
        for (int t = first; t < lastDrain + 6; t += 10) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                JobFx.smoke(d, V.up(ground, 0.3), 2.0, j, 0.6, Opt.of("alpha", 0.6));
                for (int i = 0; i < 3; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    double rr = Kit.rand() * r;
                    JobFx.soul(d, new Vec3(ground.x + Math.cos(a) * rr, ground.y + 0.3, ground.z + Math.sin(a) * rr), 0.16, j, 0.7, Opt.of("dir", V.UP, "speed", 1.6));
                }
            });
        }
        // 망자의 손: 적마다 발밑에서 솟아 붙잡음
        RuneKit.later(first, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            for (LivingEntity e : Kit.around(sp, ground, r, 3)) {
                boolean boss = JobKit.isBoss(e);
                RuneKit.effect(e, "slowness", root, boss ? 2 : 10);
                Vec3 g = new Vec3(e.getX(), RuneKit.groundAt(d, e.position(), e.getY()), e.getZ());
                Vec3 side = V.rightOf(V.flat(V.sub(e.position(), sp.position())));
                double s = 0.32 + Kit.bodyPad(e) * 0.2;
                for (int h = -1; h <= 1; h += 2) {
                    Vec3 at = V.add(g, side, h * (0.35 + Kit.bodyPad(e)));
                    // 0.47.0 입체 망자의 손: 땅에서 솟아 (0.3초) 적 쪽으로 움켜쥐고 root 틱 붙잡음 (손바닥이 적 쪽)
                    double sc = s * 4.2;
                    Vec3 palm = V.flat(V.sub(e.position(), at));
                    JobFx3d.spawn(d, "bone_hand", V.up(at, -1.3 * sc), j, Opt.of("to", V.up(at, 0.15 * sc), "ctrl", V.up(at, -0.2 * sc), "travel", 0.3, "ease", "out3",
                            "dir", V.unit(V.add(V.UP, palm, 0.25)), "face", palm, "scale", sc, "life", root / 20.0, "anim", "grasp", "animSpeed", 1.4, "fadeIn", 0.1,
                            "fadeOut", 0.35, "glowAlpha", 0.45, "shine", 0.2, "delay", (h + 1) * 0.04));
                    JobFx.hand(d, at, s, j, 0.6, Opt.of("alpha", 0.45, "coreAlpha", 0.35)); // 솟는 빛 (2D 손은 옅게 한 번)
                }
            }
            SkillFx.play(d, "particle.soul_escape", ground, 0.5, 1.0);
            SkillFx.play(d, "entity.warden.dig", ground, 1.2, 0.6);
        });
        // 빨아냄: 둘레의 적마다 drain · 혼 +1 · 적 → 문으로 혼불 (0.47.0 동작: 왼손을 움켜쥐어 끌어당김 — 1틱 전에)
        for (int i = 0; i < drains; i++) {
            int ii = i;
            JobKit.motionLater(sp, "reaper_ult:drain", first + i * gap - 1, life);
            RuneKit.later(first + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                for (LivingEntity e : Kit.around(sp, ground, r, 3)) {
                    JobKit.strike(sp, e, j, P, k.num("drain", 3), true, false, life, 0, x -> {
                        JobTraits3.gainSouls(sp, j, 1, null);
                        if (RuneKit.fxQuota("jobGateDrain", 6)) JobFx.soulFly(d, Kit.bodyPoint(e), V.up(gateAt, -0.4), 9, 0.2, j, Opt.empty());
                    });
                }
                SkillFx.play(d, "particle.soul_escape", ground, 0.8 + ii * 0.08, 0.8);
            });
        }
        // 닫힘 → 큰 피해 (0.47.0 입체 문의 문짝이 닫히고 소용돌이가 빨려 듦 → 터진 뒤 땅으로 가라앉음)
        RuneKit.later(lastDrain + 6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            mmorpg.fabric.Fx3d.anim(d, gateId, "close", false, gateGround);
            SkillFx.play(d, "block.respawn_anchor.charge", ground, 0.6, 1.0);
        });
        RuneKit.later(finalAt + 2, () -> mmorpg.fabric.Fx3d.move(sp.level(), gateId, V.up(gateGround, -JobFx3d.GATE_H * gScale), 0.55));
        JobKit.motionLater(sp, "reaper_ult:close", finalAt - 4, life); // 0.47.0 낫을 크게 휘둘러 문을 닫는 동작 (4틱 뒤)
        RuneKit.later(finalAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            JobFx.burst(d, V.up(ground, 1.5), 3.2, j, 0.5, Opt.empty());
            JobFx.smoke(d, V.up(ground, 1.0), 3.0, j, 0.9, Opt.empty());
            JobFx.ring(d, V.up(ground, 0.08), r, j, false, 0.5, Opt.empty());
            RuneKit.later(3, () -> JobFx.ring(d, V.up(ground, 0.1), r * 0.6, j, true, 0.45, Opt.empty()));
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI / 5;
                JobFx.soul(d, new Vec3(ground.x + Math.cos(a) * 1.5, ground.y + 0.5, ground.z + Math.sin(a) * 1.5), 0.25, j, 0.8, Opt.of("dir", V.UP, "speed", 4.0));
            }
            RuneKit.godRays(sp, d, ground, r * 0.6, 6, glow, core, Opt.of("h", 10.0, "life", 0.8, "alpha", 0.75, "skipNear", true));
            Ult.flash(d, ground, glow, Opt.empty(), sp);
            RuneKit.shakeAround(d, ground, 18, 0.45, 0.4);
            SkillFx.play(d, "random.explode", ground, 0.7, 1.0);
            SkillFx.play(d, "block.respawn_anchor.deplete", ground, 0.6, 1.0);
            SkillFx.play(d, "mob.wither.shoot", ground, 0.6, 0.8);
            for (LivingEntity e : Kit.around(sp, ground, r, 3)) {
                JobKit.strike(sp, e, j, P, k.num("final", 32), true, true, life, 0, x -> {
                    JobTraits3.gainSouls(sp, j, 1, null);
                    if (RuneKit.fxQuota("jobGateFinal", 6)) JobFx.hit(e, j, 1.6);
                });
            }
        });
        return true;
    }
}
