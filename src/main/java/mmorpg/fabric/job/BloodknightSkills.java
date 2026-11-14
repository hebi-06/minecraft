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
 * 세 번 다시 쓰기 · 칼끝) · 피의 돌진 (롤 브라이어 Q) · 피의 사슬 (롤 아트록스 W) · 광란 (롤 브라이어 W) · 블러드 문 (롤 아트록스 R — 사용자 "검사 궁 2개 다른걸로
 * 바꾸자 멋이 안난다", 칼을 뽑다 대신). 맞은 자리는
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
        // 0.51.0 크게 감는 동안 칼끝에 핏빛이 모임 (사용자 "커다란 검기") — 첫째 왼쪽 뒤 · 둘째 오른쪽 뒤 · 셋째 머리 위
        Vec3 coil = stage == 0 ? new Vec3(-0.8, 0.75, -0.45) : stage == 1 ? new Vec3(0.8, 0.65, -0.45) : new Vec3(0.1, 2.4, -0.1);
        JobFx.charge(sp, j, coil, Math.max(1, at - 1), 0.6);
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
        JobFx.slashWave(sp, j, tilt, 2.5, reach + 2.5, 26); // 0.51.0 핏빛 초승달 검기가 앞으로 뻗어 나감
        // 칼끝 자리: 누운 붉은 베인 줄 (앞 sweetFrom ~ reach)
        JobFx.slashCard(level, V.up(V.add(sp.position(), f, (from + reach) / 2), 0.35), f, 0.0, 4.2, 0.4, V.ZERO, j, Opt.empty(), JobFx.CardFace.UP);
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
        JobFx.groundWave(level, sp, g, f, 1.9, 6.0, 24, j); // 0.51.0 내리찍은 자리에서 땅을 타고 달리는 세로 검기
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
            JobFx.groundWave(lv, sp, sp.position(), face, 1.6, 5.0, 22, j); // 0.51.0 내려 박은 칼에서 앞으로 달리는 세로 검기
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
                // 2026-11-14 사슬이 3틱 동안 몸에서 적으로 뻗어 나가 박힘 → 적 발밑 붉은 족쇄 고리 · 피 튀김
                for (int s = 0; s < 3; s++) {
                    double upTo = (s + 1) / 3.0;
                    RuneKit.later(s, () -> {
                        if (RuneKit.sameLife(sp, life) && e.isAlive()) hellChain(sp.level(), V.up(sp.position(), 0.9), Kit.bodyPoint(e), upTo, s3(upTo), Opt.empty());
                    });
                }
                RuneKit.later(2, () -> {
                    if (!RuneKit.sameLife(sp, life) || !e.isAlive()) return;
                    ServerLevel l2 = sp.level();
                    JobFx.ring(l2, V.up(e.position(), 0.06), 0.55 + Kit.bodyPad(e), j, true, 0.45, Opt.of("alpha", 1.0));
                    JobTraits5.blood(l2, Kit.bodyPoint(e), 1.0 + Kit.bodyPad(e) * 0.4, j, 0.4, Opt.empty());
                    SkillFx.sparks(l2, Kit.bodyPoint(e), CHAIN_CORE, 6, 3.5);
                });
                JobKit.strike(sp, e, j, P, k.num("bind", 3), false, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.0));
            }
            SkillFx.play(lv, "block.chain.break", sp.position(), 0.8, 0.8);
            SkillFx.play(lv, "item.trident.hit", sp.position(), 0.7, 0.6);
        });
        // 사슬이 이어짐 (4틱마다 다시 — 적을 따라감) · 8틱마다 적의 피가 사슬을 타고 내게로 흘러듦 (붉은 빛 구슬)
        for (int t = first + 3; t < first + delay; t += 4) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                for (LivingEntity e : bound) {
                    if (e.isRemoved() || !e.isAlive()) continue;
                    hellChain(lv, V.up(sp.position(), 0.9), Kit.bodyPoint(e), 1.0, 0.25, Opt.of("alpha", 0.95));
                    if ((tt - first) % 8 == 7 && RuneKit.fxQuota("bkChainDrain", 6)) {
                        JobFx.spell(lv, 2, Kit.bodyPoint(e), V.up(sp.position(), 1.0), 0.32, 0.3, CHAIN_GLOW, CHAIN_CORE);
                    }
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
                    // 벗어남: 사슬이 끊어짐 (내 쪽 반만 남아 흐려짐)
                    hellChain(lv, V.up(c, 0.9), Kit.bodyPoint(e), 0.5, 0.3, Opt.empty());
                    SkillFx.sparks(lv, Kit.bodyPoint(e), JobFx.inner(j), 6, 3.0);
                    SkillFx.play(lv, "block.chain.break", e.position(), 1.3, 0.8);
                    continue;
                }
                any = true;
                Vec3 was = Kit.bodyPoint(e);
                if (JobKit.pushable(e)) {
                    Vec3 d = V.flat(V.sub(e.position(), front));
                    double off = Math.min(0.9, V.len(d));
                    Vec3 to = V.len(d) < 1e-3 ? front : V.add(front, V.unit(d), off);
                    e.teleportTo(to.x, e.getY(), to.z);
                    e.setDeltaMovement(0, 0.15, 0);
                    // 2026-11-14 끌려온 길 (붉은 잔상 줄기)
                    JobFx.dash(lv, was, Kit.bodyPoint(e), CHAIN_GLOW, CHAIN_CORE, 0.3, Opt.empty());
                }
                hellChain(lv, V.up(c, 0.9), Kit.bodyPoint(e), 1.0, 0.2, Opt.empty());
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

    /** 2026-11-14 헬 체인 사슬 빛 (사용자 직업 도감 메모 "사슬이 잘 안보임" — 짙은 핏빛 · 가는 사슬이 회색 적 · 풀밭에 묻혔음): 밝은 피빛 · 옅은 분홍 심지 */
    private static final double[] CHAIN_GLOW = {0.98, 0.14, 0.18};
    private static final double[] CHAIN_CORE = {1.0, 0.8, 0.78};

    /**
     * 굵은 핏빛 사슬 (from → to 의 upTo 만큼 — 뻗어 나가는 사슬): 사슬 뒤에 붉게 빛나는 줄 (rfx_jline2 — 옅게) + 사슬 고리 (rfx_jchain2 — 1.1칸마다 한 장: 굵기 약
     * 0.14칸, 예전 0.5칸마다 · 0.065칸)
     */
    private static void hellChain(ServerLevel level, Vec3 from, Vec3 to, double upTo, double life, Opt o) {
        Vec3 end = V.lerp(from, to, Math.max(0.05, Math.min(1, upTo)));
        JobFx.line(level, from, end, MOON_GLOW, CHAIN_GLOW, life, o.with("alpha", 0.55 * o.num("alpha", 1)));
        JobFx.chain(level, from, to, CHAIN_GLOW, CHAIN_CORE, 1.1, upTo, life, o);
    }

    /** 뻗어 나가는 사슬 한 토막의 수명 (끝 토막이 다음 틱의 이어 그림까지 남게) */
    private static double s3(double upTo) {
        return upTo >= 1 ? 0.25 : 0.12;
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

    // ------------------------------------------------------------ 블러드 문

    /** 핏빛 달 (rfx_jmoon 0 보름달 · 7 붉은 테) — 빛 · 심지 */
    private static final double[] MOON_GLOW = {0.78, 0.04, 0.08};
    private static final double[] MOON_CORE = {1.0, 0.42, 0.36};

    /** 땅 높이로 내린 자리 */
    private static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, V.up(p, 0.6), p.y), p.z);
    }

    /**
     * 블러드 문 (사용자 "검사 궁 2개 다른걸로 바꾸자 멋이 안난다" → 고른 것 "하늘에 핏빛 달 → 둘레 적들의 피를 빨아들인 칼로 달을 등지고 뛰어올라 거대한 초승달
     * 검기를 내리꽂음 → 피의 기둥 · 회복", 롤 아트록스 R): 하늘이 붉게 물들고 앞 하늘에 핏빛 달이 떠오름 · 칼을 얼굴 앞에 세움 → drainAt 틱에 둘레 range 칸 적들의
     * 피가 줄기로 빨려 듦 (drain — 적 하나당 최대 체력 heal 회복 · healMax 명까지) → leapAt 틱에 달을 향해 높이 뛰어오름 (칼이 핏빛으로 타오름) → slamAt 틱에 1칸 앞에
     * 내리꽂으며 거대한 초승달 핏빛 검기 (앞 length 칸 · 폭 width — speed 칸/초, 지나가는 틱에 crescent) · 땅이 갈라짐 → pillarAt 틱부터 갈라진 땅을 따라
     * pillarSpeed 칸/틱 (검기와 같은 빠르기 — 같은 적에게 늘 10틱 뒤) 으로 피의 기둥이 솟구침 (pillar · 띄움). 내리꽂을 쪽은 쓴 순간 보는 쪽. 몸 쓰는 직업 기준
     * (제 몸 · 제 칼 — 달 · 검기 · 기둥은 칼과 피의 힘)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            mmorpg.fabric.Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        int drainAt = Math.max(4, (int) k.num("drainAt", 10));
        int leapAt = Math.max(drainAt + 2, (int) k.num("leapAt", 16));
        int slamAt = Math.max(Math.max(leapAt + 8, drainAt + 10), (int) k.num("slamAt", 31));
        int pillarAt = Math.max(slamAt + 10, (int) k.num("pillarAt", 41));
        double range = k.num("range", 10);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        RpgCombat.setSteadfast(sp, slamAt + 6);
        Ult.sky(level0, c0, "rpg:ult_bloodmoon", pillarAt + 40);
        Ult.flash(level0, c0, MOON_GLOW, Opt.of("radius", 9.0, "time", new double[] {0.04, 0.08, 0.5}), sp);
        // 앞 하늘의 핏빛 달 (보름달 · 붉은 테 — 천천히 커짐) · 종소리 · 낮은 울림
        double moonLife = (pillarAt + 30) / 20.0;
        Vec3 moonAt = V.up(V.add(c0, f, 21), 8.5); // 영상 1: 11칸 위면 3인칭 화면 위 끝에 잘렸음
        JobFx.moon(level0, moonAt, 6.5, 0, 1, moonLife, Opt.of("fade", 0.12, "alpha", 0.95, "grow", 0.12), MOON_GLOW, MOON_CORE);
        JobFx.moon(level0, moonAt, 7.4, 7, 1, moonLife, Opt.of("fade", 0.12, "alpha", 0.55, "grow", 0.12), MOON_GLOW, MOON_CORE);
        SkillFx.play(level0, "block.bell.resonate", c0, 0.5, 1.0);
        SkillFx.play(level0, "ambient.cave", c0, 0.6, 0.8);
        SkillFx.play(level0, "mob.wither.ambient", c0, 0.6, 0.5);
        // 칼을 얼굴 앞에 세움: 칼에 피가 모임 · 발밑 핏빛 고리
        JobFx.charge(sp, j, new Vec3(0.05, 1.7, 0.5), leapAt, 1.0);
        JobFx.ring(level0, V.up(c0, 0.08), range * 0.35, j, true, drainAt / 20.0 + 0.2, Opt.of("alpha", 0.8));
        // drainAt: 둘레 적들의 피를 빨아들임 (피 튀김 · 피 줄기 → 내 몸 · 회복)
        RuneKit.later(drainAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            List<LivingEntity> near = JobKit.near(sp, range, 32);
            int healed = Math.min(near.size(), (int) k.num("healMax", 5));
            for (int q = 0; q < near.size(); q++) {
                LivingEntity e = near.get(q);
                boolean stream = q < 8;
                JobKit.strike(sp, e, j, P, k.num("drain", 8), true, false, life, 0, d -> {
                    splash(lv, Kit.bodyPoint(e), 0.9, j, 4);
                    JobImpact.fx(sp, e, j, 1.0);
                    if (stream) bloodReturn(sp, j, Kit.bodyPoint(e));
                });
            }
            JobFx.ring(lv, V.up(sp.position(), 0.08), 2.2, j, false, 0.45, Opt.of("alpha", 0.95));
            SkillFx.play(lv, "entity.warden.heartbeat", sp.position(), 0.8, 1.0);
            SkillFx.play(lv, "entity.generic.drink", sp.position(), 0.6, 0.7);
            if (healed > 0) RuneKit.later(8, () -> {
                if (RuneKit.sameLife(sp, life)) JobTraits5.leech(sp, j, sp.getMaxHealth() * k.num("heal", 0.02) * healed);
            });
        });
        // leapAt ~ slamAt: 달을 향해 높이 뛰어올라 (머리 위 칼에 핏빛 불길) 1칸 앞에 내리꽂음
        double land = 1.0;
        JobMove.run(sp,
                JobMove.to(leapAt, slamAt - 5, from -> JobMove.upAt(sp, V.add(from, f, 0.6), 5.5), JobMove.OUT),
                JobMove.hold(slamAt - 5, slamAt - 3),
                JobMove.to(slamAt - 3, slamAt, from -> ground(sp.level(), V.add(c0, f, land)), JobMove.IN));
        JobKit.motionLater(sp, k.id() + ":leap", leapAt, life);
        RuneKit.later(leapAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobFx.charge(sp, j, new Vec3(0.05, 2.3, 0.2), slamAt - leapAt - 2, 1.3);
            JobFx.aura(sp, j, 3.0, (slamAt - leapAt) / 20.0, 0.9);
            SkillFx.play(sp.level(), "entity.player.attack.sweep", sp.position(), 0.5, 0.8);
            SkillFx.play(sp.level(), "item.trident.riptide_2", sp.position(), 0.7, 0.8);
        });
        double lead = land + 0.8;
        RuneKit.later(slamAt, () -> {
            if (RuneKit.sameLife(sp, life)) crescent(sp, j, k, P, c0, f, lead, life);
        });
        RuneKit.later(pillarAt, () -> {
            if (RuneKit.sameLife(sp, life)) pillars(sp, j, k, P, c0, f, lead, life);
        });
        return true;
    }

    /**
     * 초승달 핏빛 검기 (slamAt): 내리꽂은 자리의 터짐 · 피 튀김 · 앞으로 달리는 거대한 세로 초승달 둘 (겹친 잔상) · 길을 따라 갈라지는 땅 · 충격파 · 1인칭 눈앞 세로 베기
     * — 길 (앞 lead ~ lead + length 칸 · 폭 width) 의 적에게 지나가는 틱에 crescent
     */
    private static void crescent(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 c0, Vec3 f, double lead, String life) {
        ServerLevel level = sp.level();
        double len = k.num("length", 12);
        double half = k.num("width", 4) / 2;
        double speed = Math.max(10, k.num("speed", 30));
        Vec3 hit = ground(level, V.add(c0, f, lead - 0.5));
        JobFx.two(level, "jimpact", JobImpact.IMPACT, V.up(hit, 0.8), 0.35, Opt.of("size", 2.2, "spin", Kit.rand() * 360, "fade", 0.25), JobFx.outer(j), MOON_CORE);
        JobFx.burst(level, V.up(hit, 0.6), 2.4, j, 0.42, Opt.empty());
        JobFx.smash(level, V.up(hit, 0.05), 3.2, j, 0.5, Opt.empty());
        splash(level, V.up(hit, 0.8), 1.6, j, 10);
        // 2026-11-14 사용자 도감 메모 "참격이 위를 보게": 세로 초승달 (90°) → 위로 불룩한 가로 초승달 (0°) 이 땅을 스치며 달림 — 아래 끝이 땅에 묻히지 않게 조금 낮춤
        Vec3 low = V.up(V.add(c0, f, lead), 1.5);
        JobFx.bigWave(level, sp, low, f, speed, 3.8, 0, len / speed + 0.15, j, Opt.empty());
        RuneKit.later(2, () -> JobFx.bigWave(level, null, low, f, speed, 3.0, 6, len / speed + 0.1, j, MOON_GLOW, MOON_CORE, Opt.of("alpha", 0.6)));
        WarlordSkills.rift(level, ground(level, V.add(c0, f, lead)), f, len, JobFx.outer(j), 1.6, speed);
        SkillFx.shock(level, V.up(hit, 0.1), JobFx.outer(j), 4.0, 0.5);
        RuneKit.rockBurst(level, V.up(hit, 0.3), 10, 6.5, 0.2);
        JobFx.slash(sp, j, 2.3, 0.2, 3.2, 96.0, 0.3);
        Ult.flash(level, hit, MOON_CORE, Opt.empty(), sp);
        SkillFx.play(level, "random.explode", hit, 0.8, 0.9);
        SkillFx.play(level, "item.mace.smash_ground_heavy", hit, 0.7, 1.0);
        SkillFx.play(level, "item.trident.thunder", hit, 0.8, 0.6);
        for (LivingEntity e : ArcanebladeSkills.inLine(sp, c0, f, lead, lead + len, half)) {
            int at = (int) Math.ceil(Math.max(0, ArcanebladeSkills.along(c0, f, e) - lead) / (speed / 20));
            JobKit.strike(sp, e, j, P, k.num("crescent", 20), false, false, life, at, d -> {
                JobImpact.hit(sp, e, j, 2.0);
                splash(sp.level(), Kit.bodyPoint(e), 1.0, j, 5);
            });
        }
    }

    /**
     * 피의 기둥 (pillarAt ~): 갈라진 땅을 따라 1.5칸마다 pillarSpeed 칸/틱으로 피가 솟구침 (위로 솟는 핏빛 검기 · 핏빛 가시 · 피 튀김 · 불티) — 길의 적에게 닿는 틱에
     * pillar (큰 숫자) · 띄움
     */
    private static void pillars(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 c0, Vec3 f, double lead, String life) {
        ServerLevel level = sp.level();
        double len = k.num("length", 12);
        double half = k.num("width", 4) / 2;
        double speed = Math.max(0.5, k.num("pillarSpeed", 1.5));
        Vec3 right = V.rightOf(f);
        for (double s = lead; s <= lead + len + 1e-6; s += 1.5) {
            double ss = s;
            int at = (int) Math.ceil((s - lead) / speed);
            RuneKit.later(at, () -> {
                ServerLevel lv = sp.level();
                Vec3 g = ground(lv, V.add(V.add(c0, f, ss), right, (Kit.rand() - 0.5) * 0.7));
                double q = 1 - (ss - lead) / Math.max(1, len) * 0.3;
                JobFx.bigWave(lv, null, V.up(g, 0.4), V.UP, 15.0, 1.8 * q, (Kit.rand() - 0.5) * 20, 0.36, j, Opt.empty()); // 2026-11-14 위로 불룩 (위를 봄)
                JobFx.spikeLife(lv, g, 1.1 * q, 0.1, 0.4, Opt.empty(), MOON_GLOW, MOON_CORE);
                JobTraits5.blood(lv, V.up(g, 1.4), 1.4 * q, j, 0.4, Opt.empty());
                SkillFx.sparks(lv, V.up(g, 1.0), JobFx.inner(j), 8, 6.0);
                if (((int) Math.round(ss / 1.5)) % 2 == 0) SkillFx.play(lv, "random.explode", g, 1.2, 0.5);
            });
        }
        SkillFx.play(level, "entity.warden.sonic_boom", V.add(c0, f, lead + len * 0.4), 0.7, 0.5);
        for (LivingEntity e : ArcanebladeSkills.inLine(sp, c0, f, lead, lead + len, half)) {
            int at = (int) Math.ceil(Math.max(0, ArcanebladeSkills.along(c0, f, e) - lead) / speed);
            JobKit.strike(sp, e, j, P, k.num("pillar", 12), false, true, life, at, d -> {
                JobImpact.hit(sp, e, j, 2.3);
                splash(sp.level(), Kit.bodyPoint(e), 1.2, j, 6);
                if (JobKit.pushable(e)) Kit.launch(e, 0, 1.0, 0);
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
