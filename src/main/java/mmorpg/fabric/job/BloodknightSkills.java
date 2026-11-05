package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Jobs;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
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
 * 0.51.0 검사 반전 블러드 나이트 — 재설계안 2판 (사용자 "검사 반전도 직업컨셉은 마음에 드는데 모션, 스킬 컨셉 같은건 마음에 안듬" · "스킬 컨셉은 롤같은거
 * 많이 참고하면 좋음"): 직업 컨셉 (체력을 칼에 바쳐 세지고 벤 만큼 피로 되찾음 — 피의 갈증 JobTraits5) 은 그대로, 스킬 다섯을 새로 — 핏빛 대검 (롤 아트록스 Q —
 * 세 번 다시 쓰기 · 칼끝) · 피의 돌진 (롤 브라이어 Q) · 피의 사슬 (롤 아트록스 W) · 광란 (롤 브라이어 W) · 칼을 뽑다 (롤 브라이어 R · 아트록스 R). 맞은 자리는
 * JobImpact (큰 타격 그림 · 멈칫 — 화면은 흔들지 않음) + 피 튀김. 몸 쓰는 직업 기준 (제 몸 · 제 칼 동작만 — 칼날 검기는 칼에서 날림).
 */
final class BloodknightSkills {
    private BloodknightSkills() {}

    private static final double[] SHADE = {0.32, 0.02, 0.05};
    private static final double[] DUST = {0.5, 0.4, 0.36};
    /** 갈라진 땅 속 (검붉은 틈) */
    private static final double[] RIFT = {0.12, 0.0, 0.02};

    static void register() {
        JobSystem.register("bloodknight_cleave", BloodknightSkills::cleave);
        JobSystem.register("bloodknight_leap", BloodknightSkills::leap);
        JobSystem.register("bloodknight_chains", BloodknightSkills::chains);
        JobSystem.register("bloodknight_frenzy", BloodknightSkills::frenzy);
        JobSystem.register("bloodknight_ult", BloodknightSkills::ult);
    }

    /** 피 튀김 + 핏방울이 사방으로 */
    static void splash(ServerLevel level, Vec3 at, double size, Jobs.Job j, int drops) {
        JobTraits5.blood(level, at, size, j, 0.45, Opt.empty());
        for (int q = 0; q < drops; q++) {
            double a = Kit.rand() * Math.PI * 2;
            Vec3 d = V.unit(new Vec3(Math.cos(a), 0.35 + Kit.rand() * 0.6, Math.sin(a)));
            JobTraits5.drop(level, V.add(at, d, 0.3), d, 0.11 + Kit.rand() * 0.07, q % 3 == 0 ? JobFx.inner(j) : JobFx.outer(j), 0.55, Opt.of("speed", 3.5 + Kit.rand() * 2,
                    "fade", 0.4));
        }
    }

    // ------------------------------------------------------------ 핏빛 대검

    /** 핏빛 대검 단계마다 눈앞 · 판 기울기 (가로 왼 → 오 · 비스듬히 되돌려 · 내리찍음) */
    private static final double[] CLEAVE_TILT = {-4, 200, 95};

    /**
     * 핏빛 대검 (롤 아트록스 Q): 세 번 — 누를 때마다 다음 단계 (window 틱 안 — JobRecast, 재사용 대기는 셋째 뒤 · 창이 닫힐 때). 1 · 2 단계: hitAt 틱에 앞 reach 칸 ·
     * arc 도를 크게 휩쓸어 casts (앞 sweetFrom 칸 너머 = 칼끝 → × sweet). 3 단계: 작게 뛰어올라 앞 slamAt 칸에 내리찍음 (둘레 slamRadius 칸 — 가운데 sweetIn 칸
     * 안 = 칼끝 × sweet). 단계마다 체력을 바침 (JobTraits5 — 시전마다). 그림: 커다란 핏빛 초승달 판 · 칼끝 자리에 누운 붉은 베인 줄 · 핏방울 / 땅 갈라짐 · 피 왕관
     */
    static boolean cleave(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int stage = Math.min(2, JobRecast.stage(sp, k.id()));
        List<Double> casts = SwordmasterSkills.numbers(k, "casts", List.of(1.4, 1.4, 2.2));
        List<Double> hitAt = SwordmasterSkills.numbers(k, "hitAt", List.of(5.0, 5.0, 7.0));
        if (stage < 2) JobRecast.open(sp, k, stage + 1, (int) k.num("window", 70), (int) k.num("gap", 10), null);
        else JobRecast.close(sp, k.id());
        if (stage > 0) JobRecast.motion(sp, k.id() + ":" + (stage + 1));
        String life = RuneKit.lifeToken(sp);
        int at = (int) Math.round(hitAt.get(Math.min(stage, hitAt.size() - 1)));
        double mult = casts.get(Math.min(stage, casts.size() - 1));
        ServerLevel level = sp.level();
        // 칼에 피가 감김 (모으는 동안)
        RuneKit.Hand h = RuneKit.handOf(sp);
        JobTraits5.blood(level, V.up(V.add(h.hand(), h.f(), 0.5), 0.2), 0.5, j, 0.3, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
        SkillFx.play(level, "item.trident.riptide_1", sp.position(), 0.8 + 0.12 * stage, 0.6);
        if (stage == 2) {
            // 셋째: 작게 뛰어올라 내리찍음 (몸 — at 틱에 땅)
            JobMove.run(sp, JobMove.hop(0, at, from -> V.add(from, Kit.facingOf(sp), 1.2), 0.9));
            Kit.noFall(sp, at + 20);
        }
        RuneKit.later(at, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            if (stage < 2) sweep(sp, j, k, P, mult, stage, life);
            else slam(sp, j, k, P, mult, life);
        });
        if (stage < 2) JobSystem.note(sp, "§c다시 누르면 " + (stage == 0 ? "되돌려 휩씀" : "내리찍음") + " §7(" + Math.round(k.num("window", 70) / 20.0 * 10) / 10.0 + "초)");
        return true;
    }

    /** 1 · 2 단계: 크게 휩씀 — 칼끝 (앞 sweetFrom 칸 너머) 은 × sweet */
    private static void sweep(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, double mult, int stage, String life) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        double reach = k.num("reach", 4.6);
        double from = k.num("sweetFrom", 3.2);
        double sweet = k.num("sweet", 1.5);
        double tilt = CLEAVE_TILT[stage];
        JobFx.slash(sp, j, 2.4, 0.2, 3.3, tilt, 0.32);
        JobFx.slashCard(level, V.up(V.add(sp.position(), f, 1.9), 1.1), f, tilt, 5.6, 0.32, V.ZERO, j, JobFx.hide1p(sp));
        // 칼끝 자리: 누운 붉은 베인 줄 (앞 sweetFrom ~ reach)
        JobFx.slashCard(level, V.up(V.add(sp.position(), f, (from + reach) / 2), 0.35), f, 0.0, 4.2, 0.4, V.ZERO, j, Opt.empty());
        for (int q = 0; q < 8; q++) {
            double a = Math.toRadians(-50 + q * 100 / 7.0);
            Vec3 d = V.unit(V.add(V.scale(f, Math.cos(a)), V.rightOf(f), Math.sin(a)));
            JobTraits5.drop(level, V.up(V.add(sp.position(), d, reach * 0.85), 1.0), d, 0.13, q % 2 == 0 ? JobFx.outer(j) : JobFx.inner(j), 0.45, Opt.of("speed", 4.0,
                    "fade", 0.4));
        }
        SkillFx.play(level, "entity.player.attack.sweep", sp.position(), 0.75 + 0.1 * stage, 1.0);
        SkillFx.play(level, "item.trident.throw", sp.position(), 0.7, 0.8);
        for (LivingEntity e : RuneKit.coneTargets(sp, reach, k.num("arc", 110), 10)) {
            double d = Math.hypot(e.getX() - sp.getX(), e.getZ() - sp.getZ()) - Kit.bodyPad(e) * 0.5;
            boolean tip = d >= from;
            JobKit.strike(sp, e, j, P, mult * (tip ? sweet : 1), false, tip, life, 0, dd -> {
                JobImpact.hit(sp, e, j, tip ? 1.6 : 1.15);
                if (tip && e.level() instanceof ServerLevel lv) splash(lv, Kit.bodyPoint(e), 1.1 + Kit.bodyPad(e) * 0.5, j, 6);
            });
        }
    }

    /** 3 단계: 앞 slamAt 칸에 내리찍음 — 가운데 sweetIn 칸 안은 × sweet · 땅 갈라짐 · 피 왕관 */
    private static void slam(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, double mult, String life) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 c = V.add(sp.position(), f, k.num("slamAt", 3.0));
        Vec3 g = new Vec3(c.x, RuneKit.groundAt(level, V.up(c, 1), sp.getY()), c.z);
        double r = k.num("slamRadius", 2.4);
        double sweetIn = k.num("sweetIn", 1.1);
        double sweet = k.num("sweet", 1.5);
        JobFx.slash(sp, j, 2.2, -0.05, 3.2, 95.0, 0.32);
        JobFx.slashCard(level, V.up(V.add(sp.position(), f, 1.4), 1.4), f, 95.0, 5.0, 0.32, V.ZERO, j, JobFx.hide1p(sp));
        JobFx.smash(level, V.up(g, 0.05), r + 0.6, j, 0.55, Opt.empty());
        splash(level, V.up(g, 0.5), 2.6, j, 14);
        crack(level, g, f, 4.0);
        RuneKit.rockBurst(level, V.up(g, 0.2), 8, 4.2, 0.18);
        SkillFx.sparks(level, V.up(g, 0.2), DUST, 10, 4.0);
        SkillFx.play(level, "item.mace.smash_ground_heavy", g, 0.9, 1.0);
        SkillFx.play(level, "entity.player.attack.crit", g, 0.7, 0.8);
        for (LivingEntity e : Kit.around(sp, V.up(g, 0.6), r, 2.5)) {
            boolean tip = Math.hypot(e.getX() - g.x, e.getZ() - g.z) <= sweetIn + Kit.bodyPad(e) * 0.5;
            JobKit.strike(sp, e, j, P, mult * (tip ? sweet : 1), false, tip, life, 0, dd -> JobImpact.hit(sp, e, j, tip ? 1.8 : 1.3));
        }
    }

    /** 땅의 검붉은 갈라짐 (앞으로 len 칸 — 어두운 틈 · 붉은 빛) */
    private static void crack(ServerLevel level, Vec3 g, Vec3 f, double len) {
        double spin = Math.toDegrees(Math.atan2(f.z, -f.x));
        for (double s = -0.8; s < len; s += 1.9) {
            Vec3 m = V.add(g, f, s);
            Vec3 mm = new Vec3(m.x, RuneKit.groundAt(level, V.up(m, 1), g.y), m.z);
            Opt base = Opt.of("dir", V.UP, "size", 1.3, "life", 1.6, "fade", 0.35, "spin", spin + (Kit.rand() < 0.5 ? 180 : 0));
            RuneKit.rfx(level, "fissure", V.up(mm, 0.035), base.with("cell", 0.0, "color", RIFT, "alpha", 0.95));
            RuneKit.rfx(level, "fissure", V.up(mm, 0.055), base.with("cell", 1.0, "color", JobTraits5.BLOOD, "alpha", 0.95));
            RuneKit.rfx(level, "fissure", V.up(mm, 0.075), base.with("cell", 2.0, "color", JobTraits5.BLOOD_LIGHT, "alpha", 1.0));
        }
    }

    // ------------------------------------------------------------ 피의 돌진

    /**
     * 피의 돌진 (롤 브라이어 Q): 앞 dist 칸 (70°) 의 가장 가까운 보이는 적 하나에게 leap 틱에 뛰어들어 칼을 박음 (total · stun 틱 기절 — 보스 제외 · shredFor 틱
     * 받는 피해 + shred). 적이 없으면 앞 5칸으로 뛰어 내려선 자리의 가장 가까운 적. 그림: 핏빛 잔상 · 끌리는 핏방울 · 내려 박는 세로 판 · 피 튀김 · 머리 위 깨진 방패
     */
    static boolean leap(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            mmorpg.fabric.Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerLevel level = sp.level();
        int leap = Math.max(3, (int) k.num("leap", 6));
        double dist = k.num("dist", 8);
        List<LivingEntity> cone = RuneKit.coneTargets(sp, dist + 1, 70, 4);
        LivingEntity target = cone.isEmpty() ? null : cone.get(0);
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 spot;
        if (target != null) {
            Vec3 d = V.flat(V.sub(target.position(), c0));
            Vec3 dir = V.len(d) < 1e-3 ? f : V.unit(d);
            double want = Math.max(0, Math.hypot(target.getX() - c0.x, target.getZ() - c0.z) - 1.1 - Kit.bodyPad(target));
            double free = Kit.reachFlat(level, c0, dir, want);
            spot = V.add(c0, dir, free);
            f = dir;
        } else {
            double free = Kit.reachFlat(level, c0, f, 5);
            spot = V.add(c0, f, free);
        }
        String life = RuneKit.lifeToken(sp);
        Kit.noFall(sp, leap + 20);
        Vec3 face = f;
        Vec3 landAt = spot;
        JobMove.run(sp, JobMove.hop(0, leap, from -> new Vec3(landAt.x, from.y, landAt.z), 1.3));
        RuneKit.shadeClone(sp, level, c0, face, 1, SHADE, JobKit.glow(j), Opt.of("life", 0.5, "fade", 0.6, "alpha", 0.85));
        JobTraits5.blood(level, V.up(c0, 1.0), 0.9, j, 0.35, JobFx.hide1p(sp));
        SkillFx.play(level, "entity.ravager.attack", c0, 1.4, 0.6);
        SkillFx.play(level, "item.trident.riptide_1", c0, 1.2, 0.7);
        for (int t = 1; t < leap; t++) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                JobTraits5.drop(sp.level(), V.up(sp.position(), 0.9), V.DOWN, 0.12, JobFx.outer(j), 0.5, JobFx.hide1p(sp).with("speed", 1.5, "fade", 0.4));
            });
        }
        RuneKit.later(leap, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            LivingEntity e = target != null && !target.isRemoved() && target.isAlive() ? target : nearest(sp, 2.2);
            Vec3 tip = V.up(V.add(sp.position(), face, 1.0), 1.2);
            JobFx.slash(sp, j, 2.2, -0.05, 2.8, 95.0, 0.28);
            JobFx.slashCard(lv, tip, face, 95.0, 3.8, 0.28, V.ZERO, j, JobFx.hide1p(sp));
            JobFx.smash(lv, V.up(sp.position(), 0.05), 1.6, j, 0.4, Opt.of("alpha", 0.85));
            SkillFx.play(lv, "item.trident.hit", tip, 0.8, 1.0);
            SkillFx.play(lv, "entity.player.attack.crit", tip, 0.9, 0.8);
            if (e == null) return;
            JobKit.strike(sp, e, j, P, k.total(), false, false, life, 0, d -> {
                JobImpact.hit(sp, e, j, 1.5);
                if (e.level() instanceof ServerLevel l2) splash(l2, Kit.bodyPoint(e), 1.2 + Kit.bodyPad(e) * 0.5, j, 8);
                if (!JobKit.isBoss(e)) RuneKit.stun(e, (int) k.num("stun", 10), JobFx.outer(j), true);
                JobTraitsMore.applyBreak(e, k.num("shred", 0.1), (int) k.num("shredFor", 80));
                JobFx.breakMark(e, j, 0.6, 0.6, 0.95);
            });
        });
        return true;
    }

    /** 둘레 r 칸의 가장 가까운 적 (없으면 null) */
    private static LivingEntity nearest(ServerPlayer sp, double r) {
        List<LivingEntity> l = JobKit.near(sp, r, 1);
        return l.isEmpty() ? null : l.get(0);
    }

    // ------------------------------------------------------------ 피의 사슬

    /**
     * 피의 사슬 (롤 아트록스 W): first 틱에 둘레 radius 칸의 적에게 피의 사슬을 박음 (bind) → delay 틱 동안 사슬이 이어짐 (4틱마다 다시 그림 — 적을 따라감) →
     * 그때 내게서 radius + slack 칸 안에 남은 적은 내 앞 1.3칸으로 끌려와 내리찍힘 (pull · stun 틱 기절 — 보스는 끌리지 않고 피해만), 벗어난 적은 사슬이 끊어짐
     */
    static boolean chains(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int first = Math.max(2, (int) k.num("first", 4));
        int delay = Math.max(10, (int) k.num("delay", 30));
        double R = k.num("radius", 5);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level = sp.level();
        List<LivingEntity> bound = new ArrayList<>();
        // 칼을 땅에 꽂아 사슬을 부름 (땅의 붉은 고리 · 피 튀김)
        JobFx.ring(level, V.up(sp.position(), 0.07), R, j, false, 0.45, Opt.of("alpha", 0.9));
        JobTraits5.blood(level, V.up(sp.position(), 0.4), 1.2, j, 0.4, Opt.empty());
        SkillFx.play(level, "block.chain.place", sp.position(), 0.7, 1.0);
        SkillFx.play(level, "entity.wither.shoot", sp.position(), 0.6, 0.4);
        RuneKit.later(first, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            for (LivingEntity e : Kit.around(sp, V.up(sp.position(), 1.0), R, 3.0)) {
                bound.add(e);
                Vec3 from = V.up(sp.position(), 0.9);
                JobFx.chain(lv, from, Kit.bodyPoint(e), j, 0.5, 0.3, Opt.empty());
                JobKit.strike(sp, e, j, P, k.num("bind", 3), false, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.0));
            }
            SkillFx.play(lv, "block.chain.break", sp.position(), 0.8, 0.8);
            SkillFx.play(lv, "item.trident.hit", sp.position(), 0.7, 0.6);
        });
        // 사슬이 이어짐 (4틱마다 다시 — 적을 따라감)
        for (int t = first + 4; t < first + delay; t += 4) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                for (LivingEntity e : bound) {
                    if (e.isRemoved() || !e.isAlive()) continue;
                    JobFx.chain(lv, V.up(sp.position(), 0.9), Kit.bodyPoint(e), j, 0.5, 0.25, Opt.of("alpha", 0.95));
                }
            });
        }
        RuneKit.later(first + delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            double keep = R + k.num("slack", 0.6);
            Vec3 front = V.add(c, f, 1.3);
            boolean any = false;
            for (LivingEntity e : bound) {
                if (e.isRemoved() || !e.isAlive()) continue;
                if (Math.hypot(e.getX() - c.x, e.getZ() - c.z) > keep) {
                    // 벗어남: 사슬이 끊어짐
                    JobFx.line(lv, V.up(c, 0.9), Kit.bodyPoint(e), j, 0.25, Opt.empty());
                    SkillFx.sparks(lv, Kit.bodyPoint(e), JobFx.inner(j), 6, 3.0);
                    SkillFx.play(lv, "block.chain.break", e.position(), 1.3, 0.8);
                    continue;
                }
                any = true;
                if (JobKit.pushable(e)) {
                    Vec3 d = V.flat(V.sub(e.position(), front));
                    double off = Math.min(0.9, V.len(d));
                    Vec3 to = V.len(d) < 1e-3 ? front : V.add(front, V.unit(d), off);
                    e.teleportTo(to.x, e.getY(), to.z);
                    e.setDeltaMovement(0, 0.15, 0);
                }
                JobFx.chain(lv, V.up(c, 0.9), Kit.bodyPoint(e), j, 0.5, 0.18, Opt.empty());
                JobKit.strike(sp, e, j, P, k.num("pull", 6), false, false, life, 2, d -> {
                    JobImpact.hit(sp, e, j, 1.6);
                    if (e.level() instanceof ServerLevel l2) splash(l2, Kit.bodyPoint(e), 1.3 + Kit.bodyPad(e) * 0.5, j, 8);
                    if (!JobKit.isBoss(e)) RuneKit.stun(e, (int) k.num("stun", 10), JobFx.outer(j), true);
                });
            }
            if (any) {
                Vec3 g = V.up(front, 0.05);
                JobFx.smash(lv, g, 2.4, j, 0.5, Opt.empty());
                splash(lv, V.up(front, 0.6), 2.0, j, 10);
                SkillFx.play(lv, "item.mace.smash_ground", front, 0.85, 1.0);
                SkillFx.play(lv, "block.chain.break", front, 0.6, 1.0);
                JobKit.motion(sp, "bloodknight_chains:pull");
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 광란

    /**
     * 광란 (롤 브라이어 W): dur 틱 태세 — 공격 속도 + haste · 흡혈 × leechMult · 둘레 autoRange 칸의 가장 가까운 적을 저절로 벰 (JobTraits5). 그림: 붉은 포효 ·
     * 몸에서 터지는 피 · 발밑 핏빛 고리 · 붉은 눈빛
     */
    static boolean frenzy(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", (int) k.num("dur", 160));
        JobTraits.startStance(sp, k, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        String life = RuneKit.lifeToken(sp);
        splash(level, V.up(c, 1.2), 1.8, j, 12);
        JobFx.aura(sp, j, 3.0, 0.8, 1.0);
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.45);
        for (int i = 0; i < 2; i++) {
            int ii = i;
            RuneKit.later(i * 5, () -> {
                if (RuneKit.sameLife(sp, life)) JobFx.ring(sp.level(), V.up(sp.position(), 0.07), 1.8 + ii * 1.2, j, ii == 1, 0.45, Opt.of("alpha", 0.9));
            });
        }
        SkillFx.play(level, "entity.ravager.roar", c, 1.35, 0.7);
        SkillFx.play(level, "entity.player.hurt_sweet_berry_bush", c, 0.7, 0.7);
        return true;
    }

    // ------------------------------------------------------------ 칼을 뽑다

    /**
     * 칼을 뽑다 (다인슬레이프는 뽑으면 피를 보아야 칼집에 들어감 — 롤 브라이어 R · 아트록스 R): draw 틱 동안 칼을 뽑음 (짙은 진홍 하늘) → 앞 range 칸으로 핏빛 칼날을 날림
     * (speed 칸/초 · 폭 width — 처음 맞은 적 blade) → 맞은 적 (없으면 칼날이 멈춘 자리) 에게 fly 틱에 날아가 내리꽂음 (둘레 slamRadius 칸 slam) → every 틱마다
     * 그 적을 X 자로 벰 (slashes — 쓰러졌으면 둘레 4칸의 가장 가까운 적) · 그 적을 쓰러뜨리면 체력 heal 회복 (한 번)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        int draw = Math.max(4, (int) k.num("draw", 8));
        int fly = Math.max(10, (int) k.num("fly", 11));
        int every = Math.max(10, (int) k.num("every", 10));
        List<Double> slashes = SwordmasterSkills.numbers(k, "slashes", List.of(5.0, 5.0, 5.0, 5.0));
        double speed = Math.max(10, k.num("speed", 40));
        double range = k.num("range", 24);
        String life = RuneKit.lifeToken(sp);
        Vec3 c0 = sp.position();
        Ult.sky(level0, c0, "rpg:ult_bloodsplit", draw + 30 + fly + every * slashes.size());
        RpgCombat.setSteadfast(sp, draw + 30 + fly + every * slashes.size());
        Kit.noFall(sp, draw + 60 + fly + every * slashes.size());
        // 칼을 뽑음: 칼집에서 솟는 핏빛 안개 · 심장 소리
        RuneKit.Hand h = RuneKit.handOf(sp);
        JobTraits5.blood(level0, V.up(V.add(h.hand(), h.f(), 0.4), 0.2), 0.9, j, 0.5, JobFx.hide1p(sp));
        JobFx.aura(sp, j, 3.0, 0.6, 0.95);
        SkillFx.play(level0, "entity.warden.heartbeat", c0, 0.8, 1.0);
        SkillFx.play(level0, "item.armor.equip_netherite", c0, 0.7, 1.0);
        RuneKit.later(draw, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 look = sp.getViewVector(1F);
            Vec3 f = Kit.facingOf(sp);
            Vec3 dir = V.unit(new Vec3(look.x, look.y * 0.4, look.z));
            Vec3 from = V.up(V.add(sp.position(), f, 0.6), 1.15);
            double len = Math.max(2, RuneKit.rayLength(level, from, dir, range));
            LivingEntity hit = null;
            double ht = len;
            for (Kit.LineHit lh : RuneKit.lineTargets(sp, from, dir, len, k.num("width", 2.4) / 2)) {
                if (lh.t() < ht) {
                    ht = lh.t();
                    hit = lh.e();
                }
            }
            double travel = hit != null ? ht : len;
            int arrive = Math.max(1, (int) Math.round(travel / speed * 20));
            // 핏빛 칼날 (1인칭 눈앞 · 3인칭 날아가는 판)
            JobFx.slash(sp, j, 2.3, 0.2, 3.2, -4.0, 0.3);
            JobFx.wave(sp, j, V.add(from, dir, 1.0), dir, speed, travel / speed + 0.08, Opt.of("size", 3.6, "spin", -4.0, "fade", 0.2));
            for (double s = 1; s < travel; s += 1.2) {
                double ss = s;
                RuneKit.later((int) Math.round(s / speed * 20), () -> JobTraits5.drop(level, V.add(from, dir, ss), V.DOWN, 0.13, JobFx.outer(j), 0.5, Opt.of("speed", 1.6,
                        "fade", 0.4)));
            }
            SkillFx.play(level, "item.trident.throw", from, 0.75, 1.0);
            SkillFx.play(level, "entity.player.attack.sweep", from, 0.6, 1.0);
            LivingEntity tgt = hit;
            Vec3 endAt = V.add(from, dir, travel);
            if (tgt != null) JobKit.strike(sp, tgt, j, P, k.num("blade", 10), false, false, life, arrive, d -> {
                JobImpact.hit(sp, tgt, j, 1.6);
                if (tgt.level() instanceof ServerLevel lv) splash(lv, Kit.bodyPoint(tgt), 1.4 + Kit.bodyPad(tgt) * 0.5, j, 8);
            });
            // 날아감: 칼날이 닿은 틱부터 fly 틱 (적 앞 · 칼날이 멈춘 자리)
            RuneKit.later(arrive, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 to = tgt != null && !tgt.isRemoved() ? tgt.position() : new Vec3(endAt.x, RuneKit.groundAt(sp.level(), endAt, sp.getY()), endAt.z);
                Vec3 d = V.flat(V.sub(to, sp.position()));
                Vec3 u = V.len(d) < 1e-3 ? Kit.facingOf(sp) : V.unit(d);
                double want = Math.max(0, Math.hypot(to.x - sp.getX(), to.z - sp.getZ()) - (tgt != null ? 1.2 + Kit.bodyPad(tgt) : 0));
                double free = Kit.reachFlat(sp.level(), sp.position(), u, want);
                Vec3 spot = V.add(sp.position(), u, free);
                RuneKit.shadeClone(sp, sp.level(), sp.position(), u, 1, SHADE, JobKit.glow(j), Opt.of("life", 0.55, "fade", 0.6, "alpha", 0.85));
                JobMove.run(sp, JobMove.hop(0, fly, from2 -> new Vec3(spot.x, from2.y, spot.z), Math.min(4.0, 1.5 + free * 0.15)));
                JobKit.motionLater(sp, "bloodknight_ult:fly", 0, life);
                for (int t = 1; t < fly; t++) {
                    RuneKit.later(t, () -> {
                        if (!RuneKit.sameLife(sp, life)) return;
                        JobTraits5.drop(sp.level(), V.up(sp.position(), 0.9), V.DOWN, 0.13, JobFx.outer(j), 0.5, JobFx.hide1p(sp).with("speed", 1.5, "fade", 0.4));
                    });
                }
                RuneKit.later(fly, () -> {
                    if (RuneKit.sameLife(sp, life)) landing(sp, j, k, P, tgt, slashes, every, life);
                });
            });
        });
        return true;
    }

    /** X 베기 동작 (:x0 · :x1) 의 첫 베기가 닿는 틱 (동작이 이만큼 먼저 나감 — tools/job_motions_d.py) */
    static final int X_HIT = 1;

    /** 내리꽂음 (둘레 slamRadius 칸) → every 틱마다 X 자 베기 (그 적 — 쓰러졌으면 둘레 4칸의 가장 가까운 적) · 쓰러뜨리면 회복 (한 번) */
    private static void landing(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, LivingEntity tgt, List<Double> slashes, int every, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = tgt != null && !tgt.isRemoved() ? V.unit(V.flat(V.sub(tgt.position(), c))) : Kit.facingOf(sp);
        if (!(V.len(f) > 0.5)) f = Kit.facingOf(sp);
        JobKit.motion(sp, "bloodknight_ult:slam");
        JobFx.slashCard(level, V.up(V.add(c, f, 1.0), 1.4), f, 95.0, 4.6, 0.32, V.ZERO, j, JobFx.hide1p(sp));
        JobFx.slash(sp, j, 2.2, -0.05, 3.1, 95.0, 0.3);
        JobFx.smash(level, V.up(c, 0.05), k.num("slamRadius", 3.5) + 0.5, j, 0.6, Opt.empty());
        splash(level, V.up(c, 0.6), 3.2, j, 16);
        crack(level, new Vec3(c.x, RuneKit.groundAt(level, V.up(c, 1), c.y), c.z), f, 5.0);
        RuneKit.rockBurst(level, V.up(c, 0.2), 10, 4.6, 0.19);
        SkillFx.play(level, "item.mace.smash_ground_heavy", c, 0.8, 1.0);
        SkillFx.play(level, "entity.warden.heartbeat", c, 1.0, 1.0);
        for (LivingEntity e : Kit.around(sp, V.up(c, 0.6), k.num("slamRadius", 3.5), 2.8)) {
            JobKit.strike(sp, e, j, P, k.num("slam", 10), false, false, life, 0, d -> {
                JobImpact.hit(sp, e, j, 1.7);
                if (JobKit.pushable(e) && e != tgt) RuneKit.knock(e, V.flat(V.sub(e.position(), c)), 0.35, 0.25);
            });
        }
        boolean[] healed = {false};
        for (int s = 0; s < slashes.size(); s++) {
            int ss = s;
            double mult = slashes.get(s);
            boolean lastOne = s == slashes.size() - 1;
            // 동작 (:x0 · :x1) 을 먼저 보내고 X_HIT 틱 뒤에 벰 (동작의 첫 대각 베기가 닿는 틱 — 멈칫이 벤 자세에 걸리게)
            RuneKit.later(every * (s + 1) - X_HIT, () -> {
                if (RuneKit.sameLife(sp, life)) JobKit.motion(sp, "bloodknight_ult:x" + (ss % 2));
            });
            RuneKit.later(every * (s + 1), () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                LivingEntity e = tgt != null && !tgt.isRemoved() && tgt.isAlive() && tgt.distanceToSqr(sp) <= 36 ? tgt : nearest(sp, 4.0);
                Vec3 ff = e != null ? V.unit(V.flat(V.sub(e.position(), sp.position()))) : Kit.facingOf(sp);
                if (!(V.len(ff) > 0.5)) ff = Kit.facingOf(sp);
                double t1 = ss % 2 == 0 ? 35.0 : 145.0;
                Vec3 at = V.up(V.add(sp.position(), ff, 1.1), 1.2);
                JobFx.slashCard(lv, at, ff, t1, 4.0, 0.26, V.ZERO, j, JobFx.hide1p(sp));
                JobFx.slashCard(lv, at, ff, 180 - t1, 4.0, 0.26, V.ZERO, j, JobFx.hide1p(sp));
                JobFx.slash(sp, j, 2.2, 0.2, 2.9, t1, 0.24);
                SkillFx.play(lv, "entity.player.attack.sweep", at, 0.8 + 0.08 * ss, 1.0);
                if (e == null) return;
                LivingEntity ee = e;
                JobKit.strike(sp, ee, j, P, mult, false, lastOne, life, 0, d -> {
                    JobImpact.hit(sp, ee, j, lastOne ? 2.0 : 1.3);
                    if (ee.level() instanceof ServerLevel l2) splash(l2, Kit.bodyPoint(ee), 1.0 + Kit.bodyPad(ee) * 0.5, j, 6);
                    if (!healed[0] && ee == tgt && (ee.isRemoved() || !ee.isAlive() || ee.isDeadOrDying())) {
                        healed[0] = true;
                        double got = JobTraits5.leech(sp, j, sp.getMaxHealth() * k.num("heal", 0.2));
                        bloodReturn(sp, j, Kit.bodyPoint(ee));
                        if (got > 0) mmorpg.fabric.DamageNumbers.show(sp, got, mmorpg.fabric.DamageNumbers.Style.HEAL, "", 0);
                    }
                });
            });
        }
    }

    /** 쓰러뜨린 적의 피가 내게로 흘러듦 (핏방울 줄) */
    private static void bloodReturn(ServerPlayer sp, Jobs.Job j, Vec3 from) {
        ServerLevel level = sp.level();
        Vec3 to = V.up(sp.position(), 1.1);
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.2) return;
        Vec3 u = V.scale(d, 1 / len);
        for (int q = 0; q < 10; q++) {
            int qq = q;
            RuneKit.later(q, () -> JobTraits5.drop(level, V.add(from, u, len * qq / 10.0), u, 0.14, qq % 2 == 0 ? JobFx.outer(j) : JobFx.inner(j), 0.4, Opt.of("speed", 6.0,
                    "fade", 0.4)));
        }
        JobFx.aura(sp, j, 2.6, 0.5, 0.9);
        SkillFx.play(level, "entity.player.levelup", sp.position(), 0.6, 0.4);
        if (RpgHealth.isRpgMode(sp)) SkillFx.play(level, "entity.generic.drink", sp.position(), 0.8, 0.6);
    }
}
