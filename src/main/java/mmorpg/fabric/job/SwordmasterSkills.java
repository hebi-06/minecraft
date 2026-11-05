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
 * 1차와 짝이 없는 새 스킬 다섯 — 칼집 섬광 (발도) · 받아넘기기 (롤 피오라 W) · 불의 울타리 (롤 자르반 R + 이렐리아 Q) · 용의 피 (롤 마스터 이 R) · 다시 벼린 칼
 * (롤 리븐 R — 다시 누르면 검풍). 맞은 자리는 JobImpact (큰 타격 그림 · 멈칫 — 화면은 흔들지 않음). 몸 쓰는 직업 기준 (제 몸 · 제 칼 동작만).
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

    // ------------------------------------------------------------ 다시 벼린 칼

    /**
     * 다시 벼린 칼 (레긴이 부러진 칼을 다시 벼림 — 롤 리븐 R): 처음 누름 — 칼을 하늘로 들어 벼림 (burstAt 틱에 둘레 radius 칸 금빛 충격 burst) · dur 틱 동안 벼린 칼
     * (JobTraits5.forge — 피해 + power · 평타 사거리 + reach · 참격 범위). 그동안 다시 누르면 (JobRecast) slashAt 틱에 앞으로 거대한 검풍 세 갈래 (가운데 · ± fan 도 —
     * range 칸 · 폭 width · speed 칸/초, 적마다 한 번 slash × (1 + 잃은 체력 × missing — 최대 + missingMax)). 다시 누르지 않으면 dur 이 끝날 때 저절로 날아감
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (JobRecast.stage(sp, k.id()) >= 1) {
            JobRecast.close(sp, k.id());
            JobRecast.motion(sp, k.id() + ":slash");
            windSlash(sp, j, k, P, RuneKit.lifeToken(sp));
            return true;
        }
        int dur = (int) k.num("dur", 200);
        int burstAt = Math.max(2, (int) k.num("burstAt", 6));
        String life = RuneKit.lifeToken(sp);
        ServerLevel level = sp.level();
        Vec3 c0 = sp.position();
        // 칼을 하늘로: 하늘에서 내려오는 금빛 기둥 · 칼끝의 불티 · 모루 소리
        RuneKit.godRays(sp, level, c0, 1.8, 7, JobKit.glow(j), JobKit.core(j), Opt.of("h", 9.0, "life", 1.0, "alpha", 0.85));
        JobFx.aura(sp, j, 3.2, 0.8, 1.0);
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.45);
        SkillFx.play(level, "block.anvil.use", c0, 0.9, 0.9);
        SkillFx.play(level, "block.beacon.power_select", c0, 1.4, 0.7);
        SkillFx.play(level, "item.trident.riptide_3", c0, 1.1, 0.7);
        for (int i = 0; i < 3; i++) {
            RuneKit.later(1 + i * 2, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 tip = V.up(sp.position(), 3.0);
                JobFx.glint(sp.level(), tip, 0.9, j, 0.35, Opt.of("vel", RuneKit.bodyVel(sp)));
                SkillFx.sparks(sp.level(), tip, JobFx.inner(j), 10, 4.0);
                SkillFx.play(sp.level(), "block.anvil.land", tip, 1.6, 0.35);
            });
        }
        RuneKit.later(burstAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            double r = k.num("radius", 4);
            JobFx.smash(lv, V.up(c, 0.05), r, j, 0.55, Opt.empty());
            JobFx.ring(lv, V.up(c, 0.08), r, j, false, 0.5, Opt.of("alpha", 1.0));
            JobFx.burst(lv, V.up(c, 1.0), 2.0, j, 0.4, Opt.empty());
            RuneKit.rockBurst(lv, V.up(c, 0.2), 8, 4.0, 0.17);
            SkillFx.sparks(lv, V.up(c, 0.3), DUST, 12, 4.5);
            SkillFx.play(lv, "item.mace.smash_ground", c, 0.9, 1.0);
            SkillFx.play(lv, "block.anvil.land", c, 1.1, 0.7);
            for (LivingEntity e : Kit.around(sp, c, r, 2.5)) JobKit.strike(sp, e, j, P, k.num("burst", 8), false, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.5));
            JobTraits5.forge(sp, dur, k.num("power", 0.2), k.num("reach", 1.5));
        });
        JobRecast.open(sp, k, 1, dur, (int) k.num("gap", 14), () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobKit.motion(sp, k.id() + ":slash");
            windSlash(sp, j, k, P, life);
        });
        JobSystem.note(sp, "§e다시 누르면 검풍 §7(" + dur / 20 + "초)");
        return true;
    }

    /** 검풍: slashAt 틱에 앞으로 세 갈래 (가운데가 가장 큼) — 갈래마다 길 가까이 적 · 적마다 한 번 (가장 먼저 닿는 갈래) · 잃은 체력만큼 셈 · 땅의 금빛 베인 자국 */
    private static void windSlash(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life) {
        int at = Math.max(1, (int) k.num("slashAt", 3));
        RuneKit.later(at, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 f = Kit.facingOf(sp);
            Vec3 r = V.rightOf(f);
            double fan = Math.toRadians(k.num("fan", 25));
            double range = k.num("range", 12);
            double half = k.num("width", 3.2) / 2;
            double speed = Math.max(8, k.num("speed", 30));
            Vec3 from = V.up(V.add(sp.position(), f, 0.6), 1.1);
            Vec3[] dirs = {f, V.unit(V.add(V.scale(f, Math.cos(fan)), r, Math.sin(fan))), V.unit(V.add(V.scale(f, Math.cos(fan)), r, -Math.sin(fan)))};
            // 1인칭 눈앞 큰 베기 · 3인칭 가로 판
            JobFx.slash(sp, j, 2.4, 0.2, 3.4, -2.0, 0.34);
            JobFx.slashCard(level, V.up(V.add(sp.position(), f, 1.3), 1.15), f, -2.0, 5.2, 0.32, V.ZERO, j, JobFx.hide1p(sp));
            SkillFx.play(level, "item.trident.throw", from, 0.8, 1.0);
            SkillFx.play(level, "entity.player.attack.sweep", from, 0.6, 1.0);
            SkillFx.play(level, "item.trident.riptide_3", from, 1.3, 0.8);
            Set<UUID> done = new HashSet<>();
            for (int i = 0; i < dirs.length; i++) {
                Vec3 d = dirs[i];
                double len = Math.max(2, RuneKit.rayLength(level, from, d, range));
                double fl = len / speed;
                double size = i == 0 ? 4.6 : 3.4;
                // 0.51.0 거대한 초승달 검기 (rfx_jwave — 가운데가 가장 큼)
                JobFx.bigWave(level, sp, V.add(from, d, 1.0), d, speed, size * 0.62, 178.0, fl + 0.12, j, Opt.empty());
                bladeScar(level, from, d, len, speed, j);
                for (Kit.LineHit h : RuneKit.lineTargets(sp, from, d, len, i == 0 ? half : half * 0.8)) {
                    LivingEntity e = h.e();
                    if (!done.add(e.getUUID())) continue;
                    double lost = 1 - e.getHealth() / Math.max(1, e.getMaxHealth());
                    double mult = k.num("slash", 32) * (1 + Math.min(k.num("missingMax", 0.75), Math.max(0, lost) * k.num("missing", 1.0)));
                    JobKit.strike(sp, e, j, P, mult, false, true, life, (int) Math.round(h.t() / speed * 20), dd -> JobImpact.hit(sp, e, j, 2.2));
                }
            }
        });
    }

    /** 검풍이 지나간 땅의 금빛 베인 자국 (조각마다 닿는 때) */
    private static void bladeScar(ServerLevel level, Vec3 from, Vec3 d, double len, double speed, Jobs.Job j) {
        double spin = Math.toDegrees(Math.atan2(d.z, -d.x));
        for (double s = 1.0; s < len; s += 2.2) {
            double ss = s;
            RuneKit.later((int) Math.round(s / speed * 20), () -> {
                Vec3 m = V.add(from, V.flat(d), ss);
                Vec3 g = new Vec3(m.x, RuneKit.groundAt(level, m, from.y - 1.1), m.z);
                Opt base = Opt.of("dir", V.UP, "size", 1.4, "life", 1.6, "fade", 0.35, "spin", spin);
                RuneKit.rfx(level, "fissure", V.up(g, 0.04), base.with("cell", 1.0, "color", JobFx.outer(j), "alpha", 0.95));
                RuneKit.rfx(level, "fissure", V.up(g, 0.06), base.with("cell", 2.0, "color", WHITE, "alpha", 1.0));
                SkillFx.sparks(level, V.up(g, 0.2), JobFx.inner(j), 3, 2.5);
            });
        }
    }
}
