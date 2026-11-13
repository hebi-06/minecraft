package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Jobs;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 사신 각성 소울 리퍼 (재설계안 2판 — 2026-11-06 사용자 "다음 작업", jobs.json advance soulreaper — 영혼 하늘빛 · 짙은 남청, 특성 「영혼」 — JobTraits3 의
 * 사신 줄기 + JobTraits12 영혼 해방): 1차의 낫 · 영혼 · 흡혈을 더 크게 — 리핑 크레센트 · 엄브럴 트레스패스 · 바운티풀 하베스트 · 스펙트럴 사이스 (2026-11-07 3판 —
 * 직업 스킬 도감 표시 고침: 랜턴 오브 소울즈 대신) · 페일 하베스트. 몸 쓰는 직업이라 제 몸 · 제 낫 동작만 (영혼 줄 · 영혼 낫날 · 거대한 망령 사신은 그림). 맞힌 적마다
 * 영혼 +1 · 가득이면 영혼 해방은 JobKit.hit (JobTraits12.afterJobHit).
 */
final class SoulreaperSkills {
    private SoulreaperSkills() {}

    static final double[] SOUL = JobTraits12.SOUL;
    static final double[] SOUL_CORE = JobTraits12.SOUL_CORE;
    /** 그림자 (짙은 남청 · 심지 옅은 하늘빛) */
    static final double[] SHADE = {0.1, 0.22, 0.5};
    static final double[] SHADE_CORE = {0.5, 0.8, 1.0};

    static void register() {
        JobSystem.register("soulreaper_crescent", SoulreaperSkills::crescent);
        JobSystem.register("soulreaper_trespass", SoulreaperSkills::trespass);
        JobSystem.register("soulreaper_harvest", SoulreaperSkills::harvest);
        JobSystem.register("soulreaper_scythe", SoulreaperSkills::scythe);
        JobSystem.register("soulreaper_ult", SoulreaperSkills::ult);
    }

    private static Vec3 chest(ServerPlayer sp) {
        return V.up(sp.position(), 1.15);
    }

    /** p 자리의 땅 (턱 하나 — 없으면 p 높이) */
    private static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, V.up(p, 0.6), p.y), p.z);
    }

    /** 회복 (숫자) */
    private static void heal(ServerPlayer sp, double amount) {
        double got = RpgHealth.healBy(sp, amount);
        if (got >= 0.05) DamageNumbers.show(sp, got, DamageNumbers.Style.HEAL, "", 0);
    }

    /**
     * 몸 둘레를 한 바퀴 휘도는 낫 (리핑 크레센트 · 페일 하베스트): 초승달 베기 여섯이 2틱에 걸쳐 차례로 (cw 면 시계 방향 — 바깥으로 불룩한 누운 판) · 허리 높이 회전 칼날 ·
     * 땅 고리 · 테두리의 그림자 연기 (scale = 크기, 1인칭은 눈앞 베기를 따로)
     */
    private static void sweep(ServerPlayer sp, Jobs.Job j, Vec3 c, Vec3 f, double r, boolean cw, double scale) {
        ServerLevel level = sp.level();
        for (int q = 0; q < 6; q++) {
            double a = (cw ? 1 : -1) * (q * 60.0 - 60);
            Vec3 d = V.turnY(f, a);
            Vec3 at = V.up(V.add(c, d, r * 0.55), 0.95);
            RuneKit.later(q / 3, () -> JobFx.slashCard(level, at, d, cw ? 8.0 : -8.0, 1.8 * scale, 0.24, Vec3.ZERO, j, JobFx.hide1p(sp)));
        }
        JobFx.whirl(level, V.up(c, 0.9), r * 0.95, j, 0.3, Opt.of("alpha", 0.9));
        JobFx.ring(level, V.up(c, 0.08), r, j, !cw, 0.35, Opt.empty());
        for (int q = 0; q < 6; q++) {
            Vec3 p = V.up(V.add(c, V.turnY(f, q * 60 + (cw ? 15 : 45)), r * 0.9), 0.6);
            JobFx.smoke(level, p, 0.55 * scale, SHADE, SOUL, 0.45, Opt.of("alpha", 0.6));
        }
        SkillFx.sparks(level, V.up(c, 0.9), SOUL, (int) Math.round(8 * scale), 4.5);
    }

    // ------------------------------------------------------------ 리핑 크레센트

    /**
     * 리핑 크레센트 (롤 케인 Q): 보는 쪽 dist 칸을 dashTicks 틱에 내달리며 (벽 앞까지 · 묶였으면 제자리) first 틱부터 every 틱마다 낫을 크게 spins 바퀴 돌림 — 둘레
     * radius 칸 적을 벰 (spin · 마지막 바퀴는 가운데로 pull 칸 끌어당김 · 맞은 적의 영혼이 몸으로 빨려 듦). 그림: 내달린 길의 그림자 · 몸 둘레를 휘도는 초승달 베기 여섯 ·
     * 허리 높이 회전 칼날 · 땅 고리 · 테두리의 그림자 연기
     */
    static boolean crescent(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int spins = Math.max(1, (int) k.num("spins", 2));
        int dashTicks = Math.max(1, (int) k.num("dashTicks", 4));
        int first = Math.max(dashTicks, (int) k.num("first", 4));
        int every = Math.max(10, (int) k.num("every", 10));
        double r = k.num("radius", 3.4);
        double dist = k.num("dist", 3.5);
        double pull = k.num("pull", 0.35); // 둘째 바퀴가 가운데로 끌어오는 칸 (JobKit.reel — 자리를 직접 옮김 · 유틸 아님: 바퀴 안에 붙잡아 두는 정도)
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        RpgCombat.setSteadfast(sp, first + (spins - 1) * every + 4);
        if (!Status.isRooted(sp) && !Kit.ridingAny(sp)) {
            JobMove.run(sp, JobMove.to(0, dashTicks, JobMove.ahead(sp, dist, 0), JobMove.OUT));
            RuneKit.shadeClone(sp, level0, start, f, 1, SHADE, SHADE_CORE, Opt.of("life", 0.4, "fade", 0.6, "alpha", 0.8));
            JobFx.smoke(level0, V.up(start, 0.7), 0.9, SHADE, SOUL, 0.5, JobFx.hide1p(sp).with("alpha", 0.7));
            JobFx.speed(sp, j, dashTicks / 20.0 + 0.1, 0.7, V.scale(f, dist / dashTicks * 20));
            RuneKit.later(dashTicks, () -> {
                if (RuneKit.sameLife(sp, life)) JobFx.dash(sp.level(), V.up(start, 1.0), V.up(sp.position(), 1.0), j, 0.25, JobFx.hide1p(sp));
            });
        }
        SkillFx.play(level0, "entity.vex.charge", start, 0.8, 0.9);
        SkillFx.play(level0, "particle.soul_escape", start, 1.0, 0.8);
        for (int i = 0; i < spins; i++) {
            int ii = i;
            boolean last = i == spins - 1;
            RuneKit.later(first + i * every, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 c = sp.position();
                sweep(sp, j, c, Kit.facingOf(sp), r, ii % 2 == 0, 1.0);
                JobFx.slash(sp, j, 2.2, 0.25, 2.4, ii % 2 == 0 ? 6.0 : 186.0, 0.24);
                SkillFx.play(lv, "entity.player.attack.sweep", c, 0.6 + 0.12 * ii, 1.0);
                SkillFx.play(lv, "item.trident.riptide_1", c, 1.3 + 0.1 * ii, 0.6);
                int[] cnt = {0};
                for (LivingEntity e : Kit.around(sp, c, r, 2.5)) {
                    JobKit.strike(sp, e, j, P, k.num("spin", 3.125), false, false, life, 0, d -> {
                        if (cnt[0]++ < 6 && e.level() instanceof ServerLevel el) {
                            JobImpact.hit(sp, e, j, last ? 1.2 : 1.0);
                            JobFx.soulFly(el, Kit.bodyPoint(e), chest(sp), 11, 0.2, j, Opt.empty());
                        }
                        // 둘째 바퀴: 낫 끝에 걸려 3틱 동안 가운데로 끌려옴 (가운데 1.2칸에서 멈춤 — 움찔 뒤에 옮겨서 덮어쓰이지 않음)
                        if (last) JobKit.reel(sp, e, () -> c, pull, 3, 1.2 + Kit.bodyPad(e), life);
                    });
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 엄브럴 트레스패스

    /**
     * 엄브럴 트레스패스 (롤 케인 R): 겨눈 적 (range 칸) 에게 그림자로 스며들어 (exitAt − inside 틱) 몸속에 숨음 (inside 틱 — 받는 피해 없음 · 밀려나지 않음 · 몸이 보이지
     * 않음 · 그 적을 따라 붙음 · 그 적이 검푸르게 갈라짐) → exitAt 틱에 건너편 (behind 칸) 으로 찢고 나오며 크게 베어 올림 (그 자리 둘레 radius 칸 burst) · 최대 체력 heal
     * 회복. 겨눈 적이 없거나 움직일 수 없으면 쓰지 않음
     */
    static boolean trespass(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        LivingEntity e = Kit.pickTarget(sp, k.num("range", 8), 25);
        if (e == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int exitAt = Math.max(6, (int) k.num("exitAt", 18));
        int inside = Math.max(2, Math.min(exitAt - 1, (int) k.num("inside", 16)));
        int diveT = exitAt - inside;
        Vec3 start = sp.position();
        Vec3 d0 = V.flat(V.sub(e.position(), start));
        Vec3 toward = V.len(d0) > 0.1 ? V.unit(d0) : Kit.facingOf(sp);
        Vec3[] last = {e.position()};
        JobMove.cancel(sp);
        JobTraits3.ghostFor(sp, exitAt + 2);
        RpgCombat.setSteadfast(sp, exitAt + 4);
        Kit.noFall(sp, exitAt + 30);
        sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, exitAt + 1, 0, false, false, false));
        // 스며듦: 몸에서 빠져나오는 그림자 · 그림자 줄기 · 연기
        RuneKit.shadeClone(sp, level0, start, toward, 1, SHADE, SHADE_CORE, Opt.of("life", 0.45, "fade", 0.6, "alpha", 0.85));
        JobFx.smoke(level0, V.up(start, 1.0), 1.0, SHADE, SOUL, 0.55, JobFx.hide1p(sp));
        JobFx.eyeRing(sp, j, 2.4, 0.9, diveT / 20.0 + 0.2);
        SkillFx.play(level0, "entity.vex.charge", start, 0.6, 1.0);
        SkillFx.play(level0, "particle.soul_escape", start, 0.7, 1.0);
        for (int t = 1; t <= diveT; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                if (e.isAlive() && !e.isRemoved()) last[0] = e.position();
                Vec3 prev = sp.position();
                Kit.moveTo(sp, V.lerp(start, last[0], tt / (double) diveT));
                JobFx.dash(lv, V.up(prev, 1.0), V.up(sp.position(), 1.0), j, 0.25, JobFx.hide1p(sp));
                if (tt < diveT || e.isRemoved()) return;
                // 스며듦: 적 몸에 그림자가 빨려 듦 · 검푸른 터짐 · 비명
                Vec3 b = Kit.bodyPoint(e);
                JobFx.burst(lv, b, 0.9 + Kit.bodyPad(e) * 0.5, SHADE, SHADE_CORE, 0.3, Opt.of("vel", RuneKit.entVel(e)));
                JobFx.smoke(lv, b, 0.8 + Kit.bodyPad(e) * 0.5, SHADE, SOUL, 0.5, Opt.of("vel", RuneKit.entVel(e), "alpha", 0.8));
                SkillFx.play(lv, "entity.vex.hurt", e.position(), 0.6, 0.9);
            });
        }
        // 몸속: 적을 따라 붙음 · 검푸르게 갈라짐 (빛 금 · 새어 나오는 영혼 · 연기) · 1인칭 화면 귀퉁이 연기 · 가운데쯤 비명
        for (int t = diveT + 1; t < exitAt; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                if (e.isAlive() && !e.isRemoved()) last[0] = e.position();
                Kit.moveTo(sp, last[0]);
                double u = (tt - diveT) / (double) inside;
                if ((tt - diveT) % 3 == 1) cracks(lv, e, j, u);
                if ((tt - diveT) % 6 == 1) JobFx.cornerSmoke(sp, j, 0.6, 0.4, 0.5, RuneKit.bodyVel(sp));
                if (tt == diveT + inside / 2 && !e.isRemoved()) {
                    JobFx.wail(lv, V.up(Kit.bodyPoint(e), 0.7), 0.7 + Kit.bodyPad(e) * 0.4, j, 0.5, Opt.of("vel", RuneKit.entVel(e)));
                    SkillFx.play(lv, "entity.vex.ambient", e.position(), 0.6, 1.0);
                }
            });
        }
        // 찢고 나옴: 건너편으로 · 크게 베어 올림 (세운 큰 참격 판) · 검푸른 터짐 · 솟는 영혼 · 비명 · 회복
        RuneKit.later(exitAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            if (e.isAlive() && !e.isRemoved()) last[0] = e.position();
            Vec3 c = last[0];
            double pad = e.isRemoved() ? 0.3 : Kit.bodyPad(e);
            Vec3 out = JobTraits12.beyond(sp, c, toward, k.num("behind", 1.6) + pad);
            if (out != null) Kit.moveTo(sp, out);
            // 이 스킬이 건 짧은 투명만 지움 (물약의 긴 투명은 그대로 — 시험의 가짜 플레이어는 효과 시간이 줄지 않아 남은 틱으로 가림)
            MobEffectInstance inv = sp.getEffect(MobEffects.INVISIBILITY);
            if (inv != null && !inv.isInfiniteDuration() && inv.getDuration() <= exitAt + 2) sp.removeEffect(MobEffects.INVISIBILITY);
            Vec3 b = e.isRemoved() ? V.up(c, 1.0) : Kit.bodyPoint(e);
            double s = 1.0 + pad * 0.6;
            JobFx.slashCard(lv, V.up(b, 0.2), toward, 95.0, 3.6 * s, 0.3, Vec3.ZERO, j, JobFx.hide1p(sp));
            JobFx.slash(sp, j, 2.0, 0.0, 2.4, 95.0, 0.26);
            JobFx.burst(lv, b, 1.5 * s, SHADE, SHADE_CORE, 0.35, Opt.empty());
            JobFx.burst(lv, V.up(b, 0.3), 1.1 * s, j, 0.3, Opt.empty());
            JobFx.wail(lv, V.up(b, 0.9), 1.1 * s, j, 0.6, Opt.of("dir", V.UP, "speed", 1.6));
            JobFx.halo(lv, V.up(ground(lv, c), 0.08), k.num("radius", 2.5), j, 0.45, Opt.empty());
            for (int q = 0; q < 8; q++) {
                double a = q * Math.PI / 4;
                Vec3 dir = V.unit(new Vec3(Math.cos(a), 1.1, Math.sin(a)));
                JobFx.soul(lv, b, 0.22, j, 0.6, Opt.of("dir", dir, "speed", 3.5));
            }
            RuneKit.shadeClone(sp, lv, sp.position(), toward, 2, SHADE, SHADE_CORE, Opt.of("life", 0.3, "fade", 0.6, "alpha", 0.7));
            SkillFx.sparks(lv, b, SOUL, 12, 5.0);
            SkillFx.play(lv, "entity.player.attack.sweep", c, 0.55, 1.0);
            SkillFx.play(lv, "item.trident.riptide_3", c, 1.2, 0.7);
            SkillFx.play(lv, "particle.soul_escape", c, 0.6, 1.0);
            for (LivingEntity x : Kit.around(sp, c, k.num("radius", 2.5), 2.5)) {
                JobKit.strike(sp, x, j, P, k.num("burst", 3.75), false, x == e, life, 0, dd -> JobImpact.hit(sp, x, j, x == e ? 1.5 : 1.0));
            }
            heal(sp, sp.getMaxHealth() * k.num("heal", 0.06));
        });
        return true;
    }

    /**
     * 몸속에 숨은 적이 검푸르게 갈라짐: 몸 둘레에서 바깥으로 뻗는 빛 금 셋 (몸 밖에서 시작 — 2026-11-06 영상 1: 몸 가운데에서 그은 금은 몸에 가려 보이지 않았음) · 몸을
     * 감싼 검푸른 불길 · 새어 나오는 영혼 · 짙은 연기 (u = 숨은 동안의 몫 0 ~ 1 — 점점 크게)
     */
    private static void cracks(ServerLevel level, LivingEntity e, Jobs.Job j, double u) {
        if (e.isRemoved()) return;
        Vec3 b = Kit.bodyPoint(e);
        double pad = Kit.bodyPad(e);
        double s = 0.6 + pad * 0.6;
        Opt vel = Opt.of("vel", RuneKit.entVel(e));
        for (int q = 0; q < 3; q++) {
            Vec3 dir = V.unit(new Vec3(Kit.rand() - 0.5, (Kit.rand() - 0.25) * 1.4, Kit.rand() - 0.5));
            Vec3 from = V.add(b, dir, 0.35 + pad);
            JobFx.line(level, from, V.add(from, dir, s * (0.7 + 0.8 * u)), SOUL, SOUL_CORE, 0.22, vel);
        }
        JobFx.burst(level, b, (1.1 + 0.6 * u) * s, SHADE, SOUL, 0.2, vel.with("alpha", 0.85));
        JobFx.soul(level, V.add(b, new Vec3(Kit.rand() - 0.5, 0.5, Kit.rand() - 0.5), 0.5 + pad), 0.24 + 0.14 * u, j, 0.5, Opt.of("dir", V.UP, "speed", 1.6));
        JobFx.smoke(level, b, (1.2 + 0.6 * u) * s, SHADE, SOUL, 0.4, vel.with("alpha", 0.8));
        // 2026-11-06 영상 2: 갈라짐이 아직 옅었음 → 발밑의 검푸른 고리 (적이 사로잡힘)
        JobFx.halo(level, V.up(e.position(), 0.06), 0.9 + pad + 0.4 * u, j, 0.25, vel.with("alpha", 0.8));
    }

    // ------------------------------------------------------------ 바운티풀 하베스트

    /**
     * 바운티풀 하베스트 (롤 피들스틱 W): 낫을 앞 땅에 꽂고 (6틱) first 틱부터 every 틱마다 drains 번 둘레 radius 칸의 가까운 적 targets 까지에게 영혼 줄을 걸어 빨아들임
     * (drain — 적 → 낫으로 영혼) → ripAt 틱에 줄을 홱 당겨 영혼을 한꺼번에 뜯어냄 (rip — 비명 지르는 얼굴이 낫으로). 그동안 흡혈 × leech · 이동 속도 − slow
     * (JobTraits12.harvestOn)
     */
    static boolean harvest(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int first = Math.max(7, (int) k.num("first", 10));
        int every = Math.max(10, (int) k.num("every", 10));
        int drains = Math.max(1, (int) k.num("drains", 4));
        int lastDrain = first + (drains - 1) * every;
        int ripAt = Math.max(lastDrain + 10, (int) k.num("ripAt", 50));
        double r = k.num("radius", 6);
        int maxT = Math.max(1, (int) k.num("targets", 6));
        Vec3 c0 = sp.position();
        JobTraits12.harvestOn(sp, ripAt + 2, k.num("slow", 0.6), k.num("leech", 2.0));
        List<LivingEntity> held = new ArrayList<>();
        // 꽂음 (6틱 — 동작이 낫을 내리꽂는 때): 낫 끝 둘레 땅 금 · 영혼 고리 · 범위 고리
        RuneKit.later(6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 tip = scythe(sp);
            JobFx.smash(lv, V.up(tip, 0.05), 1.3, j, 0.4, Opt.empty());
            JobFx.burst(lv, V.up(tip, 0.6), 0.9, SHADE, SHADE_CORE, 0.3, Opt.empty());
            JobFx.halo(lv, V.up(sp.position(), 0.08), r, j, 0.6, Opt.of("alpha", 0.75));
            for (int q = 0; q < 5; q++) {
                double a = q * Math.PI * 2 / 5;
                JobFx.soul(lv, V.add(tip, new Vec3(Math.cos(a) * 0.6, 0.2, Math.sin(a) * 0.6)), 0.18, j, 0.6, Opt.of("dir", V.UP, "speed", 1.8));
            }
            SkillFx.play(lv, "item.trident.hit_ground", tip, 0.6, 0.9);
            SkillFx.play(lv, "particle.soul_escape", tip, 0.7, 1.0);
        });
        // 영혼 줄 (3틱마다 — 걸린 적마다 낫 끝 → 몸) · 낫 끝의 영혼 불꽃
        for (int t = first; t < ripAt; t += 3) {
            RuneKit.later(t, () -> {
                if (!channel(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 knot = V.up(scythe(sp), 0.9);
                for (LivingEntity e : held) {
                    if (!e.isAlive() || e.isRemoved() || V.hdist(e.position(), sp.position()) > r + 1.5) continue;
                    JobFx.beam(lv, knot, Kit.bodyPoint(e), SOUL, SOUL_CORE, 0.7, 0.2, Opt.of("alpha", 0.7));
                }
                JobFx.soul(lv, knot, 0.24 + 0.02 * held.size(), j, 0.25, Opt.of("alpha", 0.9));
            });
        }
        // 빨아들임: 걸린 적 (가까운 순 targets 까지 — 빨 때마다 다시 고름) 마다 drain · 영혼이 낫으로
        for (int i = 0; i < drains; i++) {
            int ii = i;
            RuneKit.later(first + i * every, () -> {
                if (!channel(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 knot = V.up(scythe(sp), 0.9);
                held.clear();
                held.addAll(nearest(sp, sp.position(), r, maxT));
                JobFx.ring(lv, V.up(sp.position(), 0.08), r, j, ii % 2 == 0, 0.4, Opt.of("alpha", 0.6));
                SkillFx.play(lv, "particle.soul_escape", knot, 0.8 + ii * 0.12, 0.9);
                SkillFx.play(lv, "entity.vex.ambient", knot, 0.7 + ii * 0.1, 0.5);
                for (LivingEntity e : held) {
                    JobKit.strike(sp, e, j, P, k.num("drain", 1.0), true, false, life, 0, d -> {
                        if (!(e.level() instanceof ServerLevel el)) return;
                        JobFx.burst(el, Kit.bodyPoint(e), 0.6 + Kit.bodyPad(e) * 0.4, SOUL, SOUL_CORE, 0.25, Opt.of("vel", RuneKit.entVel(e)));
                        JobFx.soulFly(el, Kit.bodyPoint(e), V.up(scythe(sp), 0.9), 9, 0.24, j, Opt.empty());
                    });
                }
            });
        }
        // 뜯어냄 (동작: 낫을 홱 뽑아 당김 — 4틱 전): 비명 지르는 얼굴이 적 → 낫으로 · 끊어지는 밝은 줄 · 낫 끝의 큰 터짐
        JobKit.motionLater(sp, "soulreaper_harvest:rip", ripAt - 4, life);
        RuneKit.later(ripAt, () -> {
            if (!RuneKit.sameLife(sp, life) || JobTraits12.jobIf(sp, JobTraits12.SR) == null) return;
            ServerLevel lv = sp.level();
            Vec3 knot = V.up(scythe(sp), 0.9);
            List<LivingEntity> ts = nearest(sp, sp.position(), r + 0.5, maxT);
            JobFx.burst(lv, knot, 1.8, SHADE, SHADE_CORE, 0.35, Opt.empty());
            JobFx.burst(lv, knot, 1.2, j, 0.3, Opt.empty());
            JobFx.halo(lv, V.up(sp.position(), 0.08), 2.6, j, 0.45, Opt.empty());
            JobFx.eyeRing(sp, j, 2.4, 1.0, 0.4);
            SkillFx.play(lv, "entity.vex.death", knot, 0.6, 1.0);
            SkillFx.play(lv, "item.trident.return", knot, 0.7, 1.0);
            SkillFx.play(lv, "particle.soul_escape", knot, 0.5, 1.0);
            for (LivingEntity e : ts) {
                Vec3 b = Kit.bodyPoint(e);
                JobFx.line(lv, knot, b, SOUL, SOUL_CORE, 0.22, Opt.empty());
                Vec3 to = V.sub(knot, b);
                double len = Math.max(0.5, V.len(to));
                JobFx.wail(lv, V.up(b, 0.4), 0.75 + Kit.bodyPad(e) * 0.3, j, Math.min(0.6, len / 10 + 0.15), Opt.of("dir", V.scale(to, 1 / len), "speed", 10.0));
                JobKit.strike(sp, e, j, P, k.num("rip", 4.0), true, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.4));
            }
            held.clear();
        });
        // 낫을 뽑음 (뜯어낸 피해가 무적 시간으로 미뤄진 것까지 흡혈 두 배 — 2틱 뒤): 느려짐 · 흡혈을 풂 (JobTraits12.tick 은 4틱마다라 늦을 수 있음)
        RuneKit.later(ripAt + 2, () -> JobTraits12.harvestOff(sp));
        SkillFx.play(level0, "entity.warden.dig", c0, 1.3, 0.5);
        return true;
    }

    /** 꽂은 낫 끝 (몸 앞 1.1칸 땅 — 느려져도 몸을 따라감) */
    private static Vec3 scythe(ServerPlayer sp) {
        return ground(sp.level(), V.add(sp.position(), Kit.facingOf(sp), 1.1));
    }

    /** 하베스트가 이어지는지 (같은 목숨 · 소울 리퍼 · 하베스트 중) */
    private static boolean channel(ServerPlayer sp, String life) {
        return RuneKit.sameLife(sp, life) && JobTraits12.jobIf(sp, JobTraits12.SR) != null && JobTraits12.harvesting(sp);
    }

    /** c 둘레 r 칸의 적 (가까운 순 max 까지) */
    private static List<LivingEntity> nearest(ServerPlayer sp, Vec3 c, double r, int max) {
        List<LivingEntity> out = new ArrayList<>(Kit.around(sp, c, r, 3));
        out.removeIf(e -> !e.isAlive() || e.isRemoved());
        out.sort(java.util.Comparator.comparingDouble(e -> e.position().distanceToSqr(c)));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    // ------------------------------------------------------------ 스펙트럴 사이스 (2026-11-07 3판 — 랜턴 오브 소울즈 대신)

    /**
     * 스펙트럴 사이스 (태세 — 롤 헤카림 · 요릭의 낫 강화): dur 틱 동안 영혼이 낫으로 모여들어 낫이 거대한 영혼 낫이 됨 — 평타 사거리 + reach 칸 (JobTraits.startStance) ·
     * 낫 평타의 넓게 베기 range 칸 · ratio (JobTraits12.scytheSweep) · 휘두를 때마다 영혼 초승달 검기 (JobTraits12.onWeaponHit — 그림) · 평타 궤적이 영혼 빛으로
     * range 칸까지 (rules_more SWING_LOOK). 그동안 적을 쓰러뜨리면 perKill 틱씩 늘어남 (maxExtra 까지 — JobTraits12.onDeath). 그림: 둘레의 영혼 여섯이 낫으로
     * 빨려 들고 머리 위에 거대한 영혼 낫날이 피었다가 몸과 함께 한 바퀴 휘둘러 펼침 (영혼 고리)
     */
    static boolean scythe(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) k.num("dur", 160);
        JobTraits.startStance(sp, k, dur);
        JobTraits12.scytheOn(sp);
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        // 모여듦 (0 ~ 8): 둘레의 영혼 여섯이 치켜든 낫으로 · 낫 끝에 차오르는 영혼빛 · 발밑 영혼 고리 · 영혼 비명
        Vec3 top = V.up(c, 2.6);
        for (int q = 0; q < 6; q++) {
            double a = q * Math.PI / 3 + 0.3;
            JobFx.soulFly(level0, V.add(V.up(c, 0.8), new Vec3(Math.cos(a) * 2.6, 0.3 * (q % 3), Math.sin(a) * 2.6)), top, 8, 0.26, j, Opt.empty());
        }
        JobFx.charge(sp, j, new Vec3(0.1, 2.6, 0.2), 8, 0.8);
        JobFx.halo(level0, V.up(c, 0.08), 2.4, j, 0.6, Opt.empty());
        SkillFx.play(level0, "particle.soul_escape", c, 0.7, 1.2);
        SkillFx.play(level0, "entity.vex.charge", c, 0.6, 0.8);
        // 낫날이 핌 (8): 머리 위에 거대한 영혼 낫날 (아래로 휜 초승달) · 영혼 터짐
        RuneKit.later(8, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 cc = sp.position();
            Vec3 ff = Kit.facingOf(sp);
            JobFx.bigWave(lv, sp, V.up(V.add(cc, ff, 0.4), 2.7), ff, 0, 2.8, 180, 0.45, j, SOUL, SOUL_CORE, Opt.of("alpha", 0.95));
            JobFx.burst(lv, V.up(cc, 2.6), 1.2, SOUL, SOUL_CORE, 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
            JobFx.wail(lv, V.up(cc, 2.2), 1.3, j, 0.4, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
            SkillFx.play(lv, "item.trident.return", cc, 0.5, 1.0);
        });
        // 펼침 (11 — 한 바퀴 휘두름): 몸 둘레를 휩쓰는 영혼 초승달 여섯 · 영혼 고리 · 사방으로 흩어지는 영혼 불티
        RuneKit.later(11, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 cc = sp.position();
            sweep(sp, j, cc, Kit.facingOf(sp), k.num("range", 5), true, 1.2);
            SkillFx.sparks(lv, V.up(cc, 1.0), SOUL, 12, 4.0);
            SkillFx.play(lv, "entity.player.attack.sweep", cc, 0.5, 1.0);
            SkillFx.play(lv, "entity.vex.ambient", cc, 0.6, 0.8);
        });
        JobFx.aura(sp, j, 2.2, 0.8, 0.9);
        Notify.notify(sp, "§b§l" + k.name() + " §7— 넓게 베기 " + Math.round(k.num("range", 5)) + "칸 · " + Math.round(JobUpgrades.num(sp, k, "ratio", 0.5) * 100)
                + "%", 40);
        return true;
    }

    // ------------------------------------------------------------ 페일 하베스트

    /** 2026-11-13 3D 망령 사신 spin2 의 첫 바퀴 가운데 (동작을 튼 뒤 7틱 — 다음 바퀴 +10) · reap 의 날이 땅에 박히는 때 (8틱) */
    static final int REAPER_SPIN_AT = 7;
    static final int REAPER_REAP_AT = 8;

    /**
     * 페일 하베스트 (롤 요릭 R · 다리우스 Q): 영혼의 밤 (rpg:ult_souls) — 낫을 치켜들고, riseAt 틱부터 몸 너머로 (3인칭 카메라는 왼쪽 뒤 — 등 뒤에 세우면 화면을 덮음)
     * 거대한 망령 사신이 땅에서 솟아 (0.5초) → sweepAt 틱부터 every 틱마다 sweeps 번 둘레 radius 칸을 크게 휩씀 (sweep · 맞은 적의 영혼이 사신에게 빨려 듦 — 휩쓸 때마다
     * 적 하나당 최대 체력 heal 회복 · healMax 까지) → finalAt 틱에 사신과 함께 낫을 내리꽂아 둘레 finalRadius 칸을 터뜨림 (final · 띄움 lift) → 사신이 영혼으로 흩어짐
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int riseAt = Math.max(0, (int) k.num("riseAt", 4));
        int sweepAt = Math.max(riseAt + 12, (int) k.num("sweepAt", 20));
        int every = Math.max(10, (int) k.num("every", 10));
        int sweeps = Math.max(1, (int) k.num("sweeps", 2));
        int lastSweep = sweepAt + (sweeps - 1) * every;
        int finalAt = Math.max(lastSweep + 10, (int) k.num("finalAt", 46));
        double r = k.num("radius", 9);
        double fr = k.num("finalRadius", 6);
        Vec3 c0 = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        RpgCombat.setSteadfast(sp, finalAt + 4);
        Ult.sky(level0, c0, "rpg:ult_souls", finalAt + 30);
        // 거대한 망령 사신: 몸 너머 3.2칸 · 높이 약 6.4칸 (size 2.4 — 가운데 at), 반투명 (앞의 적이 보이게). 2026-11-06 영상 1: 2칸 앞 · 8칸이면 3인칭 화면 위로 머리가
        // 나갔음
        double size = 2.4;
        double tall = size * 2.67;
        Vec3 base = ground(level0, V.add(c0, f0, 3.2));
        Vec3 mid = V.up(base, tall / 2 - 0.3);
        Opt ghost = JobFx.hide1p(sp).with("alpha", 0.8, "coreAlpha", 0.75);
        // 2026-11-13 3D 망령 사신 (직업 도감 메모 "사신을 3d몹 처럼 만들어서 직접 낫을 휩쓸고 베게" — rpg:job_reaper 크기 2.4): 몸 너머 3.2칸 땅에서 솟아 (rise) 나를
        // 봄 → 휩쓸기 두 번에 맞춰 낫을 옆으로 뻗고 두 바퀴 돎 (spin2 — 바퀴 가운데가 휩쓸기 틱) → 마지막에 낫을 머리 위로 치켜들어 앞 땅에 내리찍음 (reap) → 흩어짐.
        // 못 세우면 예전 2D 그림 (JobFx.reaper)
        boolean mob3d = mmorpg.fabric.mob.RpgEntities.type(JobTraits3.REAPER) != null; // 솟는 때 (riseAt) 에 세움
        mmorpg.fabric.rune.RuneMob[] gm = {null};
        // 치켜듦: 낫 끝에 모이는 영혼 빛 · 발밑 영혼 고리 · 비명 · 1인칭 눈앞 고리
        JobFx.charge(sp, j, new Vec3(0.3, 2.3, 0.2), sweepAt, 1.0);
        JobFx.halo(level0, V.up(c0, 0.08), 3.0, j, 0.6, Opt.empty());
        JobFx.wail(level0, V.up(c0, 2.2), 1.0, j, 0.6, JobFx.hide1p(sp).with("dir", V.UP, "speed", 1.2));
        JobFx.eyeRing(sp, j, 2.4, 1.1, 0.5);
        SkillFx.play(level0, "entity.wither.ambient", c0, 0.5, 0.6);
        SkillFx.play(level0, "particle.soul_escape", c0, 0.6, 1.0);
        // 솟음: 땅에서 솟는 사신 (0.5초) · 땅의 짙은 연기 · 솟는 영혼 기둥
        RuneKit.later(riseAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            double rise = tall * 0.6;
            if (mob3d) {
                gm[0] = mmorpg.fabric.rune.RuneMob.spawn(JobTraits3.REAPER, lv, base, V.scale(f0, -1), finalAt + 40 - riseAt, 2.4, true);
                if (gm[0] != null) {
                    gm[0].anim("rise");
                    mmorpg.fabric.rune.RuneMob m = gm[0];
                    RuneKit.later(Math.max(0, sweepAt - REAPER_SPIN_AT - riseAt), () -> {
                        if (m.alive()) m.anim("spin2");
                    });
                    RuneKit.later(finalAt - REAPER_REAP_AT - riseAt, () -> {
                        if (m.alive()) m.anim("reap");
                    });
                    RuneKit.later(finalAt + 6 - riseAt, () -> {
                        if (m.alive()) m.leave("vanish", 10);
                    });
                }
            }
            if (gm[0] == null) JobFx.reaper(lv, V.up(mid, -rise), size, j, 0, 0.5, ghost.with("vel", new Vec3(0, rise / 0.5, 0), "fade", 0.05));
            JobFx.smoke(lv, V.up(base, 0.4), 2.6, SHADE, SOUL, 0.9, Opt.of("alpha", 0.8));
            JobFx.halo(lv, V.up(base, 0.08), 3.4, j, 0.7, Opt.empty());
            for (int q = 0; q < 8; q++) {
                double a = q * Math.PI / 4;
                JobFx.soul(lv, V.add(base, new Vec3(Math.cos(a) * 2.2, 0.3, Math.sin(a) * 2.2)), 0.3, j, 0.8, Opt.of("dir", V.UP, "speed", 5.0));
            }
            SkillFx.play(lv, "entity.warden.emerge", base, 0.7, 1.0);
            SkillFx.play(lv, "entity.wither.spawn", base, 1.6, 0.35);
        });
        // 서 있음 (휩쓸기 사이 — 흔들리는 사신 · 둘레를 도는 영혼)
        int standFrom = riseAt + 10;
        if (!mob3d) standLater(sp, life, j, mid, size, ghost, standFrom, sweepAt - standFrom);
        for (int i = 0; i < sweeps; i++) {
            int ii = i;
            int at = sweepAt + i * every;
            int next = i < sweeps - 1 ? at + every : finalAt;
            // 휘두름 (4장 — 0.3초) 뒤 다음까지 서 있음
            if (!mob3d) {
                RuneKit.later(at, () -> {
                    if (RuneKit.sameLife(sp, life)) JobFx.reaper(sp.level(), mid, size, j, 1, 0.3, ghost.with("fade", 0.05));
                });
                standLater(sp, life, j, mid, size, ghost, at + 6, next - at - 6);
            }
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 c = sp.position();
                Vec3 f = Kit.facingOf(sp);
                sweep(sp, j, c, f, r * 0.62, ii % 2 == 0, 2.0);
                JobFx.whirl(lv, V.up(c, 1.0), r * 0.95, j, 0.4, Opt.of("alpha", 0.85));
                JobFx.ring(lv, V.up(c, 0.1), r, j, ii % 2 != 0, 0.45, Opt.empty());
                JobFx.radialWaves(sp, j, c, 12, 1.5, r + 0.5, 1.0, 1.5, 0.3, ii * 15);
                JobFx.slash(sp, j, 2.6, 0.2, 3.0, ii % 2 == 0 ? 8.0 : 188.0, 0.3);
                SkillFx.play(lv, "entity.player.attack.sweep", c, 0.45 + 0.1 * ii, 1.0);
                SkillFx.play(lv, "item.trident.riptide_2", c, 0.8, 0.8);
                SkillFx.play(lv, "entity.vex.charge", c, 0.6, 0.8);
                int[] healed = {0};
                int healMax = Math.max(0, (int) k.num("healMax", 5));
                double each = sp.getMaxHealth() * k.num("heal", 0.02);
                double[] sum = {0};
                for (LivingEntity e : Kit.around(sp, c, r, 3)) {
                    JobKit.strike(sp, e, j, P, k.num("sweep", 14), false, false, life, 0, d -> {
                        if (healed[0] < healMax) {
                            healed[0]++;
                            sum[0] += RpgHealth.healBy(sp, each);
                        }
                        if (e.level() instanceof ServerLevel el && RuneKit.fxQuota("srUltSweep", 8)) {
                            JobImpact.hit(sp, e, j, 1.4);
                            JobFx.soulFly(el, Kit.bodyPoint(e), V.up(mid, 1.2), 12, 0.3, j, Opt.empty());
                        }
                    });
                }
                // 회복 숫자는 한 번에 (몹 무적 시간으로 미뤄진 타까지 — 3틱 뒤)
                RuneKit.later(3, () -> {
                    if (sum[0] >= 0.05 && !sp.isRemoved()) DamageNumbers.show(sp, sum[0], DamageNumbers.Style.HEAL, "", 0);
                });
            });
        }
        // 내리꽂음 (동작 6틱 전 — 뛰어올라 낫을 거꾸로)
        JobKit.motionLater(sp, "soulreaper_ult:slam", finalAt - 6, life);
        RuneKit.later(finalAt - 3, () -> {
            if (RuneKit.sameLife(sp, life) && !mob3d) JobFx.reaper(sp.level(), mid, size, j, 1, 0.3, ghost.with("fade", 0.1));
        });
        RuneKit.later(finalAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 g = ground(lv, c);
            JobFx.quake(lv, V.up(g, 0.05), fr, j, 0.6, Opt.empty());
            JobFx.smash(lv, V.up(g, 0.06), fr * 0.7, j, 0.5, Opt.empty());
            JobFx.burst(lv, V.up(g, 1.2), 3.2, SHADE, SHADE_CORE, 0.45, Opt.empty());
            JobFx.burst(lv, V.up(g, 1.0), 2.4, j, 0.4, JobFx.hide1p(sp));
            JobFx.radialWaves(sp, j, g, 10, 1.0, fr + 0.5, 0.6, 1.4, 0.35, 18);
            JobFx.slash(sp, j, 2.2, 0.3, 2.8, 90.0, 0.3);
            for (int q = 0; q < 10; q++) {
                double a = q * Math.PI / 5;
                Vec3 p = ground(lv, V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), fr * (0.45 + 0.4 * (q % 2))));
                RuneKit.later(q % 3, () -> {
                    JobFx.soul(lv, V.up(p, 0.3), 0.32, j, 0.8, Opt.of("dir", V.UP, "speed", 6.0));
                    JobFx.smoke(lv, V.up(p, 0.4), 0.8, SHADE, SOUL, 0.6, Opt.of("alpha", 0.7));
                });
            }
            for (int q = 0; q < 4; q++) {
                double a = Math.toRadians(q * 90 + 45);
                Vec3 u = V.unit(new Vec3(Math.cos(a), 0.6, Math.sin(a)));
                JobFx.wail(lv, V.add(V.up(g, 1.3), u, 1.6), 1.1, j, 0.8, JobFx.hide1p(sp).with("dir", u, "speed", 3.0));
            }
            RuneKit.godRays(sp, lv, g, fr * 0.6, 6, SOUL, SOUL_CORE, Opt.of("h", 10.0, "life", 0.8, "alpha", 0.7, "skipNear", true));
            Ult.flash(lv, g, SOUL, Opt.empty(), sp);
            SkillFx.shock(lv, V.up(g, 0.1), SOUL, fr * 1.1, 0.55);
            SkillFx.play(lv, "entity.generic.explode", g, 0.7, 1.0);
            SkillFx.play(lv, "block.respawn_anchor.deplete", g, 0.6, 1.0);
            SkillFx.play(lv, "entity.wither.shoot", g, 0.6, 0.8);
            for (LivingEntity e : Kit.around(sp, g, fr, 3.5)) {
                JobKit.strike(sp, e, j, P, k.num("final", 22), false, true, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 2.2);
                    if (JobKit.pushable(e)) Kit.launch(e, 0, k.num("lift", 0.7), 0);
                });
            }
        });
        // 흩어짐: 사신이 영혼으로 (솟는 영혼 · 비명)
        RuneKit.later(finalAt + 6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            if (!mob3d) JobFx.reaper(lv, mid, size, j, 0, 0.45, ghost.with("fade", 0.9, "alpha", 0.6));
            JobFx.smoke(lv, mid, 2.8, SHADE, SOUL, 0.8, JobFx.hide1p(sp).with("alpha", 0.6));
            for (int q = 0; q < 12; q++) {
                Vec3 p = V.add(mid, new Vec3((Kit.rand() - 0.5) * size * 1.4, (Kit.rand() - 0.5) * tall * 0.8, (Kit.rand() - 0.5) * 0.6));
                JobFx.soul(lv, p, 0.26, j, 0.9, Opt.of("dir", V.UP, "speed", 3.0 + Kit.rand() * 2));
            }
            SkillFx.play(lv, "particle.soul_escape", mid, 0.5, 1.0);
        });
        return true;
    }

    /** 사신이 서 있음 (흔들림 이어 돌림 — from 틱부터 ticks 틱) */
    private static void standLater(ServerPlayer sp, String life, Jobs.Job j, Vec3 mid, double size, Opt ghost, int from, int ticks) {
        if (ticks <= 0) return;
        RuneKit.later(from, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.reaper(sp.level(), mid, size, j, 0, ticks / 20.0 + 0.05, ghost.with("fade", 0.05));
        });
    }
}
