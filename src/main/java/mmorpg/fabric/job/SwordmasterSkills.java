package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Obj;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Guards;
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
 * 0.51.0 검사 각성 소드마스터 — 재설계안 2판 (사용자 "각성은 스킬셋을 아예 바꿔도 된다니깐 매커니즘만 유지하고 컨셉만 유지하면" · "스킬 컨셉은 롤같은거
 * 많이 참고하면 좋음" · "이펙트가 전직업보다 안좋았음 타격감이 없었음 이펙트가 작았음"): 검사와 같은 매커니즘 (근접 · 소광역 · 연타 · 평타로 투지를 쌓아 씀),
 * 1차와 짝이 없는 새 스킬 다섯 — 칼집 섬광 (발도) · 받아넘기기 (롤 피오라 W) · 불의 울타리 (롤 자르반 R + 이렐리아 Q) · 용의 피 (롤 마스터 이 R) · 스타폴
 * 블레이드 (유성 연참 — 사용자 "검사 궁 2개 다른걸로 바꾸자 멋이 안난다", 다시 벼린 칼 대신). 맞은 자리는 JobImpact (큰 타격 그림 · 멈칫 — 화면은 흔들지 않음). 몸 쓰는 직업 기준 (제 몸 · 제 칼 동작만).
 */
final class SwordmasterSkills {
    private SwordmasterSkills() {}

    /** 잔상의 그림자 몸 (짙은 금빛) */
    private static final double[] SHADE = {0.45, 0.32, 0.08};
    private static final double[] WHITE = RuneKit.WHITE;
    private static final double[] DUST = {0.62, 0.56, 0.48};
    /** 불의 울타리 불꽃 (겉 · 속) */
    static final double[] FIRE = {1.0, 0.38, 0.08};
    static final double[] FIRE_CORE = {1.0, 0.88, 0.5};
    /** rfx_jcuts · rfx_jflame 장 수 (tools/job_fx_art_adv.py) */
    static final int CUTS = 8;
    static final int FLAME = 6;

    static void register() {
        JobSystem.register("swordmaster_draw", SwordmasterSkills::draw);
        JobSystem.register("swordmaster_parry", SwordmasterSkills::parry);
        JobSystem.register("swordmaster_ring", SwordmasterSkills::ring);
        JobSystem.register("swordmaster_blood", SwordmasterSkills::dragonBlood);
        JobSystem.register("swordmaster_ult", SwordmasterSkills::ult);
    }

    static List<Double> numbers(Jobs.Skill k, String key, List<Double> fallback) {
        List<Double> l = k.def().numbersOrNull(key);
        return l == null || l.isEmpty() ? fallback : l;
    }

    // ------------------------------------------------------------ 칼집 섬광

    /** 칼자국 판의 기울기 (도 — 가로 · 비스듬 · 되돌림이 섞임) */
    private static final double[] CUT_TILT = {-6, 168, 32, 205, 12, 150, 95, -28, 185, 60};

    /**
     * 칼집 섬광: sheath 틱 동안 칼을 왼 허리 칼집에 넣고 숨을 고름 → 뽑는 순간 앞 reach 칸 · arc 도 부채꼴의 적에게 total (한 번에 넣고 숫자 lines 줄 — 1틱씩).
     * 쓸 때 투지가 wave.need 이상이면 wave.use 개를 쓰고 wave.reach 칸 · wave.lines 줄. 그림: 칼집 쪽으로 모이는 금빛 → 뽑는 틱에 1인칭 눈앞 칼자국 다발
     * (rfx_jcuts) · 3인칭 부채꼴 안에 칼자국 판 lines 개가 두 개씩 그어짐 → 마지막 큰 가로 베기
     */
    static boolean draw(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int sheath = Math.max(2, (int) k.num("sheath", 6));
        int lines = Math.max(1, (int) k.num("lines", 7));
        double reach = k.num("reach", 4.2);
        double arc = k.num("arc", 120);
        Obj wave = k.def().objOrNull("wave");
        if (wave != null && JobTraits.stacks(sp) >= wave.numOr("need", 3)) {
            JobTraits.useStacks(sp, (int) wave.numOr("use", 3));
            reach = wave.numOr("reach", 6.2);
            lines = (int) wave.numOr("lines", 10);
            JobFx.aura(sp, j, 2.6, 0.45, 0.8);
            SkillFx.play(sp.level(), "block.bell.hit", sp.position(), 1.9, 0.35);
        }
        String life = RuneKit.lifeToken(sp);
        ServerLevel level = sp.level();
        Vec3 f0 = Kit.facingOf(sp);
        Vec3 hip = V.up(V.add(sp.position(), V.rightOf(f0), -0.35), 0.95);
        // 칼집에 넣음: 칼집 쪽으로 모이는 금빛 · 칼집 소리
        JobFx.glint(level, hip, 0.55, j, sheath / 20.0 + 0.15, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
        SkillFx.sparks(level, hip, JobFx.inner(j), 5, 1.6);
        SkillFx.play(level, "item.armor.equip_iron", sp.position(), 1.5, 0.8);
        SkillFx.play(level, "item.trident.return", sp.position(), 1.8, 0.5);
        JobFx.charge(sp, j, new Vec3(-0.35, 0.95, 0.15), sheath, 0.6); // 0.51.0 숨을 고르는 동안 칼집 쪽으로 금빛이 모임
        int n = lines;
        double R = reach;
        RuneKit.later(sheath, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 look = sp.getViewVector(1F);
            // 0.51.0 뽑는 순간 앞으로 퍼지는 거대한 금빛 초승달 검기 (사용자 "커다란 검기가 나오면서 때리면")
            JobFx.slashWave(sp, j, 184.0, R * 0.48, R + 2.0, 30);
            // 1인칭: 눈앞 칼자국 다발 (rfx_jcuts — 카메라를 봄)
            JobFx.two(lv, "jcuts", CUTS, V.add(sp.getEyePosition(), look, 2.3), 0.42, JobFx.only1p(sp).with("size", 2.5, "spin", Kit.rand() * 16 - 8, "vel",
                    RuneKit.bodyVel(sp), "fade", 0.25), JobFx.outer(j), JobFx.inner(j));
            // 3인칭 · 다른 사람: 부채꼴 안 칼자국 판 (한 틱에 둘)
            for (int i = 0; i < n; i++) {
                int ii = i;
                RuneKit.later(ii / 2, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    Vec3 ff = Kit.facingOf(sp);
                    double side = (Kit.rand() * 2 - 1) * R * 0.32;
                    Vec3 at = V.up(V.add(V.add(sp.position(), ff, R * (0.42 + 0.38 * Kit.rand())), V.rightOf(ff), side), 0.75 + 0.75 * Kit.rand());
                    JobFx.slashCard(sp.level(), at, ff, CUT_TILT[ii % CUT_TILT.length] + Kit.rand() * 14 - 7, 2.7 + Kit.rand() * 0.9, 0.24, V.ZERO, j, JobFx.hide1p(sp));
                    JobFx.glint(sp.level(), at, 0.35, j, 0.2, Opt.empty());
                });
            }
            // 마지막: 칼을 다 뽑아 크게 가로로 (눈앞 · 3인칭 판) — 뽑은 뒤 4틱 (줄 수와 상관없이 · 동작의 마지막 되돌려 베기와 같은 때)
            RuneKit.later(4, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 ff = Kit.facingOf(sp);
                JobFx.slash(sp, j, 2.4, 0.25, 3.1, 4.0, 0.3);
                JobFx.slashCard(sp.level(), V.up(V.add(sp.position(), ff, 1.6), 1.15), ff, 4.0, 4.8, 0.3, V.ZERO, j, JobFx.hide1p(sp));
                JobFx.slashWave(sp, j, 4.0, R * 0.58, R + 4.0, 34); // 0.51.0 마지막 큰 가로 베기의 검기
            });
            SkillFx.play(lv, "item.trident.throw", sp.position(), 1.6, 0.9);
            SkillFx.play(lv, "entity.player.attack.sweep", sp.position(), 1.25, 1.0);
            SkillFx.play(lv, "block.amethyst_block.hit", sp.position(), 1.9, 0.7);
            for (LivingEntity e : RuneKit.coneTargets(sp, R, arc, 10)) {
                JobKit.strikeSplit(sp, e, j, P, k.total(), n, 1, false, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.25));
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 받아넘기기

    /**
     * 받아넘기기 (롤 피오라 W): window 틱 동안 칼을 세워 막음 — 받는 피해 없음 (Guards 감소 1). 그동안 맞으면 (Guards 반사 — 때린 적) 그 쪽으로 돌아 세 번 받아침
     * (total — 한 번에 넣고 숫자 lines 줄, 때린 적 + 그 쪽 reach 칸 · arc 도) · 투지 + stacks · 큰 쇳소리 · 멈칫. 아무것도 막지 않으면 window 틱에 한 걸음
     * (step) 내디뎌 앞 부채꼴을 벰 (total)
     */
    static boolean parry(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int window = Math.max(4, (int) k.num("window", 10));
        String life = RuneKit.lifeToken(sp);
        boolean[] done = {false};
        Guards.set(sp, window, 1.0, Guards.Reflect.fn((p, att) -> {
            if (done[0] || !RuneKit.sameLife(sp, life)) return;
            done[0] = true;
            riposte(sp, j, k, P, att, life);
        }), null);
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        // 막는 자세: 앞에 세운 칼날의 금빛 · 초승달 막 (몸 앞 — 3인칭)
        Vec3 guard = V.up(V.add(sp.position(), f, 0.75), 1.3);
        JobFx.slashCard(level, guard, f, 92.0, 2.6, window / 20.0, RuneKit.bodyVel(sp), j, JobFx.hide1p(sp));
        JobFx.glint(level, guard, 0.6, j, window / 20.0, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
        JobFx.eyeRing(sp, j, 2.0, 0.8, window / 20.0);
        SkillFx.play(level, "item.armor.equip_iron", sp.position(), 1.2, 0.9);
        SkillFx.play(level, "block.amethyst_block.resonate", sp.position(), 1.6, 0.6);
        RuneKit.later(window, () -> {
            if (done[0] || !RuneKit.sameLife(sp, life)) return;
            done[0] = true;
            stepCut(sp, j, k, P, life);
        });
        return true;
    }

    /** 받아침이 나가는 틱 (막은 뒤 — 4틱 멈칫 (쨍) 뒤 클라이언트 동작이 1.5배로 따라잡는 동안의 첫 베기 · 동작 jb_swordmaster_parry_riposte 의 1.5 · 4.5 · 7.5) */
    static final int RIPOSTE_AT = 6;

    /** 막음 → 받아침: 쨍 (4틱 멈칫) → 때린 적 쪽으로 세 번 (RIPOSTE_AT 부터 2틱마다 판 — 피해는 한 번에 숫자 lines 줄) */
    private static void riposte(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, LivingEntity att, String life) {
        ServerLevel level = sp.level();
        Vec3 d = V.flat(V.sub(att.position(), sp.position()));
        if (V.len(d) < 1e-4) d = Kit.facingOf(sp);
        Vec3 dir = V.unit(d);
        Vec3 at = V.up(V.add(sp.position(), dir, 0.9), 1.3);
        // 쨍 — 흰 섬광 · 금빛 불티 · 큰 쇳소리
        JobFx.two(level, "jimpact", JobImpact.IMPACT, at, 0.32, Opt.of("size", 1.6, "spin", Kit.rand() * 360, "fade", 0.25), JobFx.outer(j), WHITE);
        SkillFx.sparks(level, at, JobFx.inner(j), 16, 6.0);
        SkillFx.play(level, "item.shield.block", at, 1.4, 1.0);
        SkillFx.play(level, "block.anvil.land", at, 1.8, 0.5);
        SkillFx.play(level, "entity.player.attack.crit", at, 1.2, 0.9);
        // 받아치는 동작을 먼저 보내고 멈칫 (멈칫은 지금 하는 동작에 걸림 — 막은 자세로 4틱 멈췄다가 받아침)
        JobKit.motion(sp, "swordmaster_parry:riposte");
        JobImpact.stop(sp, 4);
        JobTraits.addStacks(sp, (int) k.num("stacks", 3));
        double[] tilts = {-8, 172, 95};
        for (int i = 0; i < tilts.length; i++) {
            double tilt = tilts[i];
            RuneKit.later(RIPOSTE_AT + i * 2, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 c = V.up(V.add(sp.position(), dir, 1.2), 1.15);
                JobFx.slashCard(sp.level(), c, dir, tilt, 3.4, 0.26, V.ZERO, j, JobFx.hide1p(sp));
                JobFx.slash(sp, j, 2.2, 0.25, 2.6, tilt, 0.24);
                if (tilt == 95) JobFx.slashWave(sp, j, tilt, 2.0, 5.5, 26); // 0.51.0 마지막 내려 베기의 검기
                SkillFx.play(sp.level(), "item.spear.attack_miss", c, 1.3 + 0.15 * Math.random(), 0.8);
            });
        }
        int lines = Math.max(1, (int) k.num("lines", 3));
        Set<UUID> done = new HashSet<>();
        List<LivingEntity> hit = new ArrayList<>();
        if (Kit.canHit(sp, att) && att.distanceToSqr(sp) <= 49) hit.add(att);
        for (LivingEntity e : RuneKit.coneTargets(sp, k.num("reach", 3.6), k.num("arc", 130), 8)) hit.add(e);
        for (LivingEntity e : hit) {
            if (!done.add(e.getUUID())) continue;
            JobKit.strikeSplit(sp, e, j, P, k.total(), lines, 2, false, false, life, RIPOSTE_AT, dd -> JobImpact.hit(sp, e, j, 1.4));
        }
    }

    /** 아무것도 막지 않음: 한 걸음 내디뎌 앞 부채꼴을 벰 (가로) */
    private static void stepCut(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        double step = k.num("step", 0.5);
        if (step > 0 && sp.onGround() && !Status.isRooted(sp)) Kit.launch(sp, f.x * step, sp.getDeltaMovement().y, f.z * step);
        JobFx.slash(sp, j, 2.3, 0.2, 2.9, -4.0, 0.28);
        JobFx.slashCard(level, V.up(V.add(sp.position(), f, 1.4), 1.15), f, -4.0, 4.0, 0.28, V.ZERO, j, JobFx.hide1p(sp));
        JobFx.slashWave(sp, j, -4.0, 2.0, 5.5, 26); // 0.51.0 가로 검기
        SkillFx.play(level, "entity.player.attack.sweep", sp.position(), 1.1, 1.0);
        SkillFx.play(level, "item.spear.attack_miss", sp.position(), 1.35, 0.8);
        for (LivingEntity e : RuneKit.coneTargets(sp, k.num("reach", 3.6), k.num("arc", 130), 8)) {
            JobKit.strike(sp, e, j, P, k.total(), false, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.15));
        }
    }

    // ------------------------------------------------------------ 불의 울타리

    /**
     * 불의 울타리 (시구르드가 브륀힐드의 불의 울타리를 넘음 — 롤 자르반 R + 이렐리아 Q): 둘레 radius 칸에 불꽃 울타리가 솟고 (울타리가 끝날 때까지 안의 적은 나가지
     * 못함 · 느려짐 — 밀 수 있는 적만 안으로 되돌림), first 틱부터 every 틱마다 울타리 안을 가로지름 (cuts — 3틱에 내달려 길 가까이 (폭 width) 의 적을 벰):
     * 울타리 안의 적이 있으면 가장 가까운 적 (바로 앞에 벤 적은 다른 적이 있으면 뺌) 을 지나 그 뒤로, 없으면 별 꼴 꼭짓점으로. 지나간 길에 금빛 불 자국이 남아
     * 별을 이룸 → 끝에 별 자국 · 울타리가 한꺼번에 타오름
     */
    static boolean ring(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        List<Double> cuts = numbers(k, "cuts", List.of(1.8, 1.8, 1.8, 1.8, 1.8));
        int n = cuts.size();
        int first = Math.max(4, (int) k.num("first", 6));
        int every = Math.max(10, (int) k.num("every", 10));
        double R = k.num("radius", 5);
        double half = k.num("width", 1.8) / 2;
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        double gy = RuneKit.groundAt(level0, c0, c0.y);
        Vec3 cg = new Vec3(c0.x, gy, c0.z);
        int last = first + (n - 1) * every;
        int ringTicks = last + 16;
        String life = RuneKit.lifeToken(sp);
        Kit.noFall(sp, ringTicks + 20);
        RpgCombat.setSteadfast(sp, ringTicks);
        // 울타리가 솟음: 서 있는 불꽃 (rfx_jflame — 이어 돌림) 이 고리를 따라 · 땅의 불 고리 · 금빛 고리 · 소리
        fireRing(level0, cg, R, ringTicks / 20.0, j);
        JobFx.ring(level0, V.up(cg, 0.08), R, j, false, 0.5, Opt.of("alpha", 0.95));
        JobFx.ring(level0, V.up(cg, 0.06), R * 0.6, j, true, 0.45, Opt.of("alpha", 0.6));
        SkillFx.play(level0, "item.firecharge.use", c0, 0.7, 1.0);
        SkillFx.play(level0, "entity.blaze.shoot", c0, 0.6, 0.8);
        SkillFx.play(level0, "item.trident.riptide_2", c0, 1.3, 0.6);
        confine(sp, cg, R, ringTicks, life, (int) k.num("slowAmp", 2));
        // 가로지르기 (first 틱부터 every 틱마다 — 내달림은 그 3틱 앞에서 시작)
        Vec3 f0 = Kit.facingOf(sp);
        double a0 = Math.atan2(f0.z, f0.x);
        List<Vec3[]> paths = new ArrayList<>();
        UUID[] prev = {null};
        for (int i = 0; i < n; i++) {
            int ii = i;
            double mult = cuts.get(i);
            RuneKit.later(first + i * every - 3, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                // 가로지르기마다 동작을 다시 보냄 (되돌려 베기 · 바로 베기가 번갈아 — 마지막은 크게 끝맺음 · 멈칫이 쌓여도 효과와 맞게)
                JobKit.motion(sp, "swordmaster_ring:" + (ii == n - 1 ? "last" : ii % 2 == 0 ? "cut0" : "cut1"));
                cross(sp, j, P, mult, ii, cg, R, half, a0, prev, paths, life);
            });
        }
        // 끝: 별 자국 · 울타리가 한꺼번에 타오름
        RuneKit.later(last + 10, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            for (Vec3[] p : paths) {
                double len = V.len(V.sub(p[1], p[0]));
                for (double s = 0.5; s < len; s += 1.6) RuneKit.glowCrack(lv, V.add(p[0], V.unit(V.sub(p[1], p[0])), s), 1.3, FIRE, FIRE_CORE, Opt.of("life", 0.9));
            }
            JobFx.ring(lv, V.up(cg, 0.1), R, j, false, 0.45, Opt.of("alpha", 1.0));
            JobFx.burst(lv, V.up(cg, 0.6), 2.2, j, 0.4, Opt.empty());
            SkillFx.play(lv, "entity.blaze.shoot", cg, 0.5, 1.0);
            SkillFx.play(lv, "item.firecharge.use", cg, 0.9, 0.8);
        });
        return true;
    }

    /** 가로지르기 한 번: 고른 끝자리로 3틱에 내달림 — 잔상 · 금빛 줄기 · 참격 판 · 땅의 불 자국, 3틱에 길 가까이의 적을 벰 */
    private static void cross(ServerPlayer sp, Jobs.Job j, double P, double mult, int i, Vec3 cg, double R, double half, double a0, UUID[] prev, List<Vec3[]> paths,
            String life) {
        ServerLevel level = sp.level();
        Vec3 from = new Vec3(sp.getX(), cg.y, sp.getZ());
        // 울타리 안의 적: 가장 가까운 적 (바로 앞에 벤 적은 다른 적이 있으면 뺌)
        List<LivingEntity> inside = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, V.up(cg, 1.0), R + 0.3, 3.0)) if (Math.hypot(e.getX() - cg.x, e.getZ() - cg.z) <= R + 0.3) inside.add(e);
        inside.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
        LivingEntity target = null;
        for (LivingEntity e : inside) if (!e.getUUID().equals(prev[0])) {
            target = e;
            break;
        }
        if (target == null && !inside.isEmpty()) target = inside.get(0);
        Vec3 to;
        if (target != null) {
            Vec3 d = V.flat(V.sub(target.position(), from));
            Vec3 dir = V.len(d) < 1e-3 ? V.flat(Kit.facingOf(sp)) : V.unit(d);
            to = V.add(new Vec3(target.getX(), cg.y, target.getZ()), dir, 1.6 + Kit.bodyPad(target));
            prev[0] = target.getUUID();
        } else {
            // 별 꼴: 꼭짓점 i × 144°
            double a = a0 + Math.toRadians(144 * (i + 1));
            to = new Vec3(cg.x + Math.cos(a) * R * 0.8, cg.y, cg.z + Math.sin(a) * R * 0.8);
        }
        // 울타리 안으로 (가운데에서 R − 0.7 안)
        Vec3 off = V.sub(to, cg);
        double dist = Math.hypot(off.x, off.z);
        if (dist > R - 0.7) to = new Vec3(cg.x + off.x / dist * (R - 0.7), cg.y, cg.z + off.z / dist * (R - 0.7));
        Vec3 dir = V.unit(V.flat(V.sub(to, from)));
        double len = Math.hypot(to.x - from.x, to.z - from.z);
        double free = len < 0.3 ? 0 : Kit.reachFlat(level, from, dir, len);
        Vec3 end = free > 0.3 ? V.add(from, dir, free) : from;
        paths.add(new Vec3[] {from, end});
        Vec3 face = dir;
        RuneKit.shadeClone(sp, level, sp.position(), face, 1, SHADE, JobKit.glow(j), Opt.of("life", 0.45, "fade", 0.6, "alpha", 0.8));
        JobMove.run(sp, JobMove.to(0, 3, JobMove.at(new Vec3(end.x, sp.getY(), end.z)), JobMove.OUT));
        SkillFx.play(level, "item.trident.riptide_1", from, 1.4 + 0.08 * i, 0.7);
        RuneKit.later(3, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 a = V.up(from, 1.0);
            Vec3 b = V.up(end, 1.0);
            JobFx.dash(lv, a, b, j, 0.32, JobFx.hide1p(sp));
            JobFx.speed(sp, j, 0.22, 0.85);
            Vec3 mid = V.scale(V.add(a, b), 0.5);
            JobFx.slashCard(lv, mid, face, -6 + Kit.rand() * 12, Math.max(3.0, len * 0.9), 0.28, V.ZERO, j, JobFx.hide1p(sp));
            JobFx.slash(sp, j, 2.2, 0.25, 2.7, i % 2 == 0 ? -6.0 : 186.0, 0.24);
            // 땅의 금빛 불 자국 (울타리가 끝날 때까지)
            fireTrail(lv, from, end, cg.y);
            SkillFx.play(lv, "entity.player.attack.sweep", b, 1.2 + 0.06 * i, 0.9);
            SkillFx.play(lv, "item.firecharge.use", b, 1.4, 0.4);
            for (Kit.LineHit h : RuneKit.lineTargets(sp, a, face, Math.max(1, Math.hypot(end.x - from.x, end.z - from.z) + 0.8), half)) {
                LivingEntity e = h.e();
                JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.15));
            }
        });
    }

    /** 고리를 따라 서 있는 불꽃 (rfx_jflame — 이어 돌림): life 초 · 불티 */
    private static void fireRing(ServerLevel level, Vec3 cg, double R, double life, Jobs.Job j) {
        int count = Math.max(12, (int) Math.round(2 * Math.PI * R / 1.15));
        for (int i = 0; i < count; i++) {
            double a = i * 2 * Math.PI / count;
            Vec3 p = new Vec3(cg.x + Math.cos(a) * R, RuneKit.groundAt(level, new Vec3(cg.x + Math.cos(a) * R, cg.y + 1, cg.z + Math.sin(a) * R), cg.y), cg.z + Math.sin(a) * R);
            double size = 0.95 + Kit.rand() * 0.3;
            JobFx.loop(level, "jflame", FLAME, V.up(p, size * 0.85), life, 14, Opt.of("size", size, "fade", 0.15, "grow", 0.25), FIRE, FIRE_CORE);
            if (i % 3 == 0) SkillFx.sparks(level, V.up(p, 0.4), FIRE_CORE, 2, 2.2);
        }
    }

    /** 가로지른 길의 금빛 불 자국 (땅에 누운 갈라짐 — 끝날 때까지) */
    private static void fireTrail(ServerLevel level, Vec3 a, Vec3 b, double gy) {
        Vec3 d = V.sub(b, a);
        double len = Math.hypot(d.x, d.z);
        if (len < 0.4) return;
        Vec3 u = V.unit(V.flat(d));
        double spin = Math.toDegrees(Math.atan2(u.z, -u.x));
        for (double s = 0.4; s < len; s += 1.8) {
            Vec3 m = V.add(a, u, Math.min(len, s + 0.9));
            Vec3 g = new Vec3(m.x, RuneKit.groundAt(level, V.up(m, 1), gy), m.z);
            Opt base = Opt.of("dir", V.UP, "size", 1.25, "life", 2.0, "fade", 0.35, "spin", spin);
            RuneKit.rfx(level, "fissure", V.up(g, 0.04), base.with("cell", 1.0, "color", FIRE, "alpha", 0.95));
            RuneKit.rfx(level, "fissure", V.up(g, 0.06), base.with("cell", 2.0, "color", FIRE_CORE, "alpha", 1.0));
        }
    }

    /** 울타리: ticks 틱 동안 안의 적은 나가지 못함 (밀 수 있는 적은 벽에서 안으로 되돌림) · 느려짐 (보스 제외) */
    private static void confine(ServerPlayer sp, Vec3 cg, double R, int ticks, String life, int slowAmp) {
        Set<UUID> in = new HashSet<>();
        for (int t = 2; t <= ticks; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (LivingEntity e : Kit.around(sp, V.up(cg, 1.0), R + 1.5, 3.5)) {
                    double dx = e.getX() - cg.x;
                    double dz = e.getZ() - cg.z;
                    double d = Math.hypot(dx, dz);
                    if (d < R - 0.4) in.add(e.getUUID());
                    if (!in.contains(e.getUUID())) continue;
                    if (tt % 10 == 2 && !JobKit.isBoss(e)) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 14, Math.max(0, slowAmp), false, false));
                    if (d > R - 0.45 && JobKit.pushable(e) && d > 1e-3) {
                        double k = (R - 0.5) / d;
                        e.teleportTo(cg.x + dx * k, e.getY(), cg.z + dz * k);
                        Vec3 v = e.getDeltaMovement();
                        e.setDeltaMovement(-dx / d * 0.12, v.y, -dz / d * 0.12);
                        if (e.level() instanceof ServerLevel lv && RuneKit.fxQuota("ringWall", 4)) SkillFx.sparks(lv, V.up(e.position(), 0.6), FIRE_CORE, 4, 2.4);
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------ 용의 피

    /**
     * 용의 피 (시구르드가 파프니르의 피를 뒤집어씀 — 롤 마스터 이 R): dur 틱 태세 — 공격 속도 + haste · 이동 + move (JobTraits5) · 칼 평타 투지 +2 · 투지 유지 (JobTraits) ·
     * 받는 피해 −guard (Guards). 그림: 용의 울음 · 몸에서 솟는 금빛 불길 · 발밑 불꽃 고리 · 1인칭 눈앞 고리
     */
    static boolean dragonBlood(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", (int) k.num("dur", 160));
        JobTraits.startStance(sp, k, dur);
        Guards.set(sp, dur, k.num("guard", 0.2), null, p -> JobTraits.stanceOn(p, k.id()));
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        String life = RuneKit.lifeToken(sp);
        JobFx.aura(sp, j, 3.0, 0.9, 1.0);
        RuneKit.godRays(sp, level, c, 1.4, 6, JobKit.glow(j), JobKit.core(j), JobFx.hide1p(sp).with("h", 5.0, "life", 0.8, "alpha", 0.75));
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.5);
        for (int i = 0; i < 3; i++) {
            int ii = i;
            RuneKit.later(i * 4, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 g = V.up(sp.position(), 0.08);
                JobFx.ring(sp.level(), g, 1.4 + ii * 0.9, j, ii % 2 == 1, 0.45, Opt.of("alpha", 0.9 - ii * 0.15));
                fireRing(sp.level(), new Vec3(g.x, sp.getY(), g.z), 1.2 + ii * 0.6, 0.45, j);
            });
        }
        SkillFx.play(level, "entity.ender_dragon.growl", c, 1.5, 0.5);
        SkillFx.play(level, "item.firecharge.use", c, 0.8, 0.8);
        SkillFx.play(level, "block.amethyst_block.resonate", c, 1.3, 0.8);
        return true;
    }

    // ------------------------------------------------------------ 스타폴 블레이드

    /** 땅 높이로 내린 자리 */
    private static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, V.up(p, 0.6), p.y), p.z);
    }

    /** 혜성이 아무 적도 없을 때 내리꽂는 앞 부채꼴의 자리 (쓴 자리 c 에서 보는 쪽 f 기준 — 도 · 칸) */
    private static final double[][] EMPTY_SPOTS = {{-38, 3.6}, {34, 4.2}, {-12, 5.4}, {16, 2.8}, {-30, 5.8}, {26, 6.0}};

    /**
     * 스타폴 블레이드 (유성 연참 — 사용자 "검사 궁 2개 다른걸로 바꾸자 멋이 안난다" → 고른 것 "하늘 높이 솟구쳤다가 적마다 금빛 유성처럼 내리꽂힘 (여섯 번) → 마지막에
     * 가운데로 떨어져 큰 폭발", 롤 판테온 R · 마스터 이 Q): 황혼 하늘 (자줏빛 — 금빛 혜성이 보이게) · 칼을 두 손으로 낮게 쥐고 웅크렸다가 leapAt 틱에 rise 칸 솟구침 → first 틱부터 every 틱마다
     * 쓴 자리 둘레 range 칸의 적 (아직 안 친 적 중 가까운 적 → 모두 쳤으면 가장 오래전에 친 적 · 없으면 앞 부채꼴의 땅) 에게 금빛 혜성처럼 비스듬히 내리꽂힘
     * (strikes 번 — 칼을 거꾸로 쥐고 꽂음 · 둘레 strikeRadius 칸 strike) · 사이마다 다음 적 위로 다시 솟구침 → 친 자리들의 가운데 위 finalRise 칸으로 솟아 칼에
     * 불길을 모았다가 finalAt 틱에 마지막 낙하 (둘레 finalRadius 칸 final · 띄움). 1차 전사 궁 (메테오 크래시 — 뛰어 내려찍기) 과 달리 하늘 높이에서 혜성 꼬리를
     * 끌며 칼로 꽂음. 몸 쓰는 직업 기준 (제 몸 · 제 칼). 화면은 흔들지 않고 보는 방향도 돌리지 않음 (몸만 옮김)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        int n = Math.max(1, (int) k.num("strikes", 6));
        int first = Math.max(8, (int) k.num("first", 12));
        int every = Math.max(10, (int) k.num("every", 10));
        int lastHit = first + (n - 1) * every;
        int finalAt = Math.max(lastHit + 10, (int) k.num("finalAt", 75));
        int leapAt = Math.max(1, Math.min(first - 5, (int) k.num("leapAt", 4)));
        double rise = k.num("rise", 6);
        double range = k.num("range", 10);
        double sr = k.num("strikeRadius", 2.4);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        RpgCombat.setSteadfast(sp, finalAt + 6);
        Ult.sky(level0, c0, "rpg:ult_starfall", finalAt + 30);
        Ult.flash(level0, c0, JobFx.outer(j), Opt.of("radius", 8.0, "time", new double[] {0.03, 0.05, 0.3}), sp);
        SkillFx.play(level0, "block.beacon.power_select", c0, 1.3, 0.8);
        SkillFx.play(level0, "item.trident.riptide_3", c0, 0.9, 0.8);
        // 웅크림: 두 손으로 낮게 쥔 칼에 노을빛이 모임 · 발밑 불꽃 고리
        JobFx.charge(sp, j, new Vec3(0.25, 0.75, 0.45), leapAt + 1, 0.85);
        JobFx.ring(level0, V.up(c0, 0.08), 1.6, j, false, 0.35, Opt.of("alpha", 0.9));
        fireRing(level0, c0, 1.3, 0.4, j);
        // 친 적 → 몇 번째 혜성에 쳤는지 (가장 오래전에 친 적을 다시 고름)
        java.util.Map<UUID, Integer> struck = new java.util.HashMap<>();
        LivingEntity[] tgt = new LivingEntity[n];
        Vec3[] spot = new Vec3[n];
        Vec3[] center = {null};
        List<JobMove.Seg> segs = new ArrayList<>();
        // 솟구침 (leapAt ~ first − 4) · 잠깐 머묾
        segs.add(JobMove.to(leapAt, first - 4, JobMove.up(sp, rise), JobMove.OUT));
        segs.add(JobMove.hold(first - 4, first - 3));
        for (int i = 0; i < n; i++) {
            int ii = i;
            int hitT = first + i * every;
            if (i > 0) {
                // 친 자리에서 다음 적 위 (그 적 쪽에서 2칸 덜 간 곳 · 4.5칸 위) 로 다시 솟구침
                segs.add(JobMove.to(hitT - every + 1, hitT - 4, from -> {
                    pick(sp, j, ii, c0, f0, range, struck, tgt, spot);
                    Vec3 d = V.flat(V.sub(spot[ii], from));
                    double len = Math.hypot(d.x, d.z);
                    Vec3 back = len > 0.3 ? V.add(spot[ii], V.unit(d), -Math.min(2.0, len * 0.6)) : spot[ii];
                    return JobMove.upAt(sp, back, 4.5);
                }, JobMove.OUT));
                segs.add(JobMove.hold(hitT - 4, hitT - 3));
            }
            // 내리꽂음 (3틱 — 점점 빨라짐): 그 적의 지금 자리 앞
            segs.add(JobMove.to(hitT - 3, hitT, from -> {
                if (tgt[ii] == null && spot[ii] == null) pick(sp, j, ii, c0, f0, range, struck, tgt, spot);
                if (tgt[ii] != null && tgt[ii].isAlive() && !tgt[ii].isRemoved()) spot[ii] = landBefore(sp.level(), from, tgt[ii]);
                diveFx(sp, j, from, spot[ii]);
                return spot[ii];
            }, JobMove.IN));
            JobKit.motionLater(sp, k.id() + ":dive", hitT - 3, life);
            RuneKit.later(hitT, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 at = spot[ii] != null ? spot[ii] : ground(sp.level(), sp.position());
                strikeFx(sp, j, at, sr);
                for (LivingEntity e : Kit.around(sp, at, sr, 2.5)) {
                    JobKit.strike(sp, e, j, P, k.num("strike", 3), false, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.6));
                }
            });
        }
        // 마지막: 친 자리들의 가운데 위로 높이 솟아 머묾 (칼에 불길) → 떨어짐
        segs.add(JobMove.to(lastHit + 1, finalAt - 5, from -> {
            center[0] = middle(sp.level(), spot, c0);
            return JobMove.upAt(sp, center[0], k.num("finalRise", 7.5));
        }, JobMove.OUT));
        segs.add(JobMove.hold(finalAt - 5, finalAt - 3));
        segs.add(JobMove.to(finalAt - 3, finalAt, from -> {
            Vec3 c = center[0] != null ? center[0] : middle(sp.level(), spot, c0);
            diveFx(sp, j, from, c);
            return c;
        }, JobMove.IN));
        JobMove.run(sp, segs.toArray(new JobMove.Seg[0]));
        JobKit.motionLater(sp, k.id() + ":final", lastHit + 1, life);
        RuneKit.later(lastHit + 2, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 c = center[0] != null ? center[0] : middle(sp.level(), spot, c0);
            double fr = k.num("finalRadius", 6);
            int wait = finalAt - lastHit - 2;
            // 떨어질 자리: 땅의 불꽃 고리 (안으로 조여 듦) · 하늘에서 내려오는 금빛 기둥 · 칼끝으로 모이는 불길
            JobFx.ring(sp.level(), V.up(c, 0.1), fr, j, true, wait / 20.0 + 0.1, Opt.of("alpha", 0.8));
            RuneKit.godRays(sp, sp.level(), c, 2.4, 7, JobKit.glow(j), JobKit.core(j), Opt.of("h", 11.0, "life", wait / 20.0 + 0.15, "alpha", 0.55));
            JobFx.charge(sp, j, new Vec3(0.1, 2.3, 0.2), wait - 3, 1.3);
            SkillFx.play(sp.level(), "block.respawn_anchor.charge", c, 1.2, 0.8);
        });
        RuneKit.later(finalAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 c = center[0] != null ? center[0] : middle(sp.level(), spot, c0);
            finale(sp, j, k, P, c, life);
        });
        return true;
    }

    /**
     * 다음 혜성의 적 (i 번째): 쓴 자리 둘레 range 칸의 맞힐 수 있는 적 중 아직 안 친 적 (지금 몸에서 가까운 순) → 모두 쳤으면 가장 오래전에 친 적. 없으면 앞 부채꼴의
     * 땅 (EMPTY_SPOTS). 고른 적 · 내리꽂을 자리를 넣음
     */
    private static void pick(ServerPlayer sp, Jobs.Job j, int i, Vec3 c0, Vec3 f0, double range, java.util.Map<UUID, Integer> struck, LivingEntity[] tgt,
            Vec3[] spot) {
        ServerLevel level = sp.level();
        LivingEntity best = null;
        double bestKey = Double.MAX_VALUE;
        for (LivingEntity e : Kit.around(sp, c0, range, 6.0)) {
            if (!e.isAlive() || e.isRemoved()) continue;
            Integer when = struck.get(e.getUUID());
            double dist = Math.hypot(e.getX() - sp.getX(), e.getZ() - sp.getZ());
            double key = (when == null ? -1000 : when * 100.0) + dist;
            if (key < bestKey) {
                bestKey = key;
                best = e;
            }
        }
        tgt[i] = best;
        if (best != null) {
            struck.put(best.getUUID(), i);
            spot[i] = landBefore(level, sp.position(), best);
        } else {
            double[] a = EMPTY_SPOTS[i % EMPTY_SPOTS.length];
            spot[i] = ground(level, V.add(c0, V.turnY(f0, a[0]), a[1]));
        }
    }

    /** 적 앞 (from 쪽 — 몸 폭만큼 띄움) 의 땅: 혜성이 내리꽂히는 자리 (적 몸 속이 아니라 바로 앞에) */
    private static Vec3 landBefore(ServerLevel level, Vec3 from, LivingEntity e) {
        Vec3 d = V.flat(V.sub(from, e.position()));
        double len = Math.hypot(d.x, d.z);
        Vec3 u = len > 0.05 ? V.scale(d, 1 / len) : new Vec3(0, 0, 1);
        return ground(level, V.add(e.position(), u, 0.7 + Kit.bodyPad(e)));
    }

    /** 친 자리들의 가운데 (땅 — 없으면 쓴 자리) */
    private static Vec3 middle(ServerLevel level, Vec3[] spot, Vec3 c0) {
        double x = 0, z = 0;
        int m = 0;
        for (Vec3 p : spot) if (p != null) {
            x += p.x;
            z += p.z;
            m++;
        }
        return m == 0 ? c0 : ground(level, new Vec3(x / m, c0.y, z / m));
    }

    /** 혜성처럼 내리꽂힘 (3틱): 몸을 따라가는 금빛 혜성 머리 · 지나간 길의 노을빛 꼬리 · 바람 소리 */
    private static void diveFx(ServerPlayer sp, Jobs.Job j, Vec3 from, Vec3 to) {
        if (to == null) return;
        ServerLevel level = sp.level();
        Vec3 a = V.up(from, 1.0);
        Vec3 b = V.up(to, 0.9);
        Vec3 d = V.sub(b, a);
        double len = V.len(d);
        if (len < 0.3) return;
        Vec3 u = V.scale(d, 1 / len);
        JobFx.comet(level, a, u, 1.5, len / 0.15, 0.17, j, JobFx.hide1p(sp));
        JobFx.dash(level, a, b, j, 0.42, JobFx.hide1p(sp).with("alpha", 0.9));
        RuneKit.later(1, () -> SkillFx.sparks(level, V.lerp(a, b, 0.5), FIRE_CORE, 6, 3.0));
        SkillFx.play(level, "item.trident.riptide_1", a, 1.4, 0.6);
    }

    /** 혜성이 꽂힌 자리: 큰 타격 · 터짐 · 둘레 고리 · 충격파 · 불꽃 고리 · 별 모양 금 · 돌 · 불티 · 1인칭 세로 베기 · 소리 (화면은 흔들지 않음) */
    private static void strikeFx(ServerPlayer sp, Jobs.Job j, Vec3 at, double r) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        JobFx.two(level, "jimpact", JobImpact.IMPACT, V.up(at, 0.7), 0.3, Opt.of("size", 1.5, "spin", Kit.rand() * 360, "fade", 0.25), JobFx.outer(j), WHITE);
        JobFx.burst(level, V.up(at, 0.6), 1.7, j, 0.35, Opt.empty());
        JobFx.ring(level, V.up(at, 0.08), r, j, false, 0.4, Opt.of("alpha", 0.95));
        SkillFx.shock(level, V.up(at, 0.1), JobFx.outer(j), r * 1.1, 0.4);
        fireRing(level, at, r * 0.6, 0.45, j);
        starScar(level, at, j);
        JobFx.slashCard(level, V.up(at, 1.1), f, 90, 2.4, 0.22, V.ZERO, j, JobFx.hide1p(sp));
        JobFx.slash(sp, j, 1.9, 0.3, 2.3, 92.0, 0.22);
        RuneKit.rockBurst(level, V.up(at, 0.2), 6, 5.0, 0.16);
        SkillFx.sparks(level, V.up(at, 0.4), FIRE, 12, 6.0);
        SkillFx.sparks(level, V.up(at, 0.5), FIRE_CORE, 8, 4.0);
        SkillFx.play(level, "random.explode", at, 1.1, 0.55);
        SkillFx.play(level, "item.mace.smash_ground", at, 0.9, 0.9);
        SkillFx.play(level, "block.anvil.land", at, 1.5, 0.35);
    }

    /** 땅의 별 모양 금 (셋이 60 도씩 엇갈림 — 어두운 틈 · 직업 빛 · 흰 금) */
    private static void starScar(ServerLevel level, Vec3 at, Jobs.Job j) {
        double spin0 = Kit.rand() * 60;
        for (int q = 0; q < 3; q++) {
            Opt base = Opt.of("dir", V.UP, "size", 1.6, "life", 1.6, "fade", 0.35, "spin", spin0 + q * 60);
            RuneKit.rfx(level, "fissure", V.up(at, 0.04 + q * 0.004), base.with("cell", 1.0, "color", JobFx.outer(j), "alpha", 0.95));
            RuneKit.rfx(level, "fissure", V.up(at, 0.06 + q * 0.004), base.with("cell", 2.0, "color", WHITE, "alpha", 1.0));
        }
    }

    /**
     * 마지막 낙하 (finalAt): 거대한 터짐 · 내려찍기 · 둘레로 퍼지는 큰 검기 (카메라 쪽은 짧게) · 불꽃 고리 셋 · 충격파 · 하늘로 솟는 금빛 기둥 · 별 모양 금 · 돌 ·
     * 1인칭 눈앞 세로 베기 · 섬광 — 둘레 finalRadius 칸 final (큰 숫자) · 띄움
     */
    private static void finale(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 c, String life) {
        ServerLevel level = sp.level();
        double fr = k.num("finalRadius", 6);
        JobFx.two(level, "jimpact", JobImpact.IMPACT, V.up(c, 0.9), 0.4, Opt.of("size", 2.6, "spin", Kit.rand() * 360, "fade", 0.25), JobFx.outer(j), WHITE);
        JobFx.burst(level, V.up(c, 1.0), 4.2, j, 0.5, Opt.empty());
        JobFx.smash(level, V.up(c, 0.05), fr, j, 0.6, Opt.empty());
        JobFx.radialWaves(sp, j, c, 10, 1.2, fr, 1.0, 2.0, 0.24, 18);
        for (int q = 0; q < 3; q++) {
            int qq = q;
            RuneKit.later(q * 2, () -> {
                JobFx.ring(level, V.up(c, 0.08), 2.0 + qq * 2.0, j, qq % 2 == 1, 0.5, Opt.of("alpha", 1.0 - qq * 0.15));
                fireRing(level, c, 1.6 + qq * 1.8, 0.5, j);
            });
        }
        SkillFx.shock(level, V.up(c, 0.1), JobFx.outer(j), fr * 1.15, 0.6);
        RuneKit.godRays(sp, level, c, 3.2, 9, JobKit.glow(j), JobKit.core(j), Opt.of("h", 12.0, "life", 0.8, "alpha", 0.85, "skipNear", true));
        starScar(level, c, j);
        RuneKit.rockBurst(level, V.up(c, 0.3), 16, 8.0, 0.22);
        SkillFx.sparks(level, V.up(c, 0.8), FIRE, 30, 9.0);
        SkillFx.sparks(level, V.up(c, 1.0), FIRE_CORE, 20, 7.0);
        JobFx.slash(sp, j, 2.2, 0.25, 3.2, 92.0, 0.3);
        Ult.flash(level, c, JobFx.inner(j), Opt.empty(), sp);
        SkillFx.play(level, "random.explode", c, 0.7, 1.0);
        SkillFx.play(level, "item.mace.smash_ground_heavy", c, 0.8, 1.0);
        SkillFx.play(level, "item.trident.thunder", c, 1.1, 0.8);
        SkillFx.play(level, "entity.blaze.shoot", c, 0.6, 0.8);
        for (LivingEntity e : Kit.around(sp, c, fr, 3.0)) {
            JobKit.strike(sp, e, j, P, k.num("final", 22), false, true, life, 0, d -> {
                JobImpact.hit(sp, e, j, 2.4);
                if (JobKit.pushable(e)) Kit.launch(e, 0, 0.9, 0);
            });
        }
    }
}
