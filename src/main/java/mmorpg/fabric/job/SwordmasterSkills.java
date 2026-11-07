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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 검사 각성 소드마스터 — 재설계안 2판 (사용자 "각성은 스킬셋을 아예 바꿔도 된다니깐 매커니즘만 유지하고 컨셉만 유지하면" · "스킬 컨셉은 롤같은거
 * 많이 참고하면 좋음" · "이펙트가 전직업보다 안좋았음 타격감이 없었음 이펙트가 작았음"): 검사와 같은 매커니즘 (근접 · 소광역 · 연타 · 평타로 투지를 쌓아 씀),
 * 1차와 짝이 없는 새 스킬 다섯 — 라이트닝 드로우 (발도) · 리포스트 (롤 피오라 W) · 블레이드 에코 (2026-11-07 3판 — 인페르노 링 대신: 둘레 적 사이를 번개처럼 오가며
 * 칼자국만 남기고 칼집에 넣는 순간 한꺼번에 터짐) · 드래곤 블러드 (롤 마스터 이 R) · 엑스칼리버 (2026-11-07 3판 — 스타폴 블레이드 대신: 사용자 "전사 궁 같음 …
 * 엑스칼리버 같은 느낌으로 내리꽂는 커다란 검격" → 하늘에 닿는 빛의 대검을 내리쳐 앞을 휩쓰는 금빛 검격). 맞은 자리는 JobImpact (큰 타격 그림 · 멈칫 — 화면은
 * 흔들지 않음). 몸 쓰는 직업 기준 (제 몸 · 제 칼 동작만).
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
        JobSystem.register("swordmaster_echo", SwordmasterSkills::echo);
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

    // ------------------------------------------------------------ 블레이드 에코

    /** 적이 없을 때 옮겨 갈 별 꼴 꼭짓점 (쓴 자리에서 보는 쪽 기준으로 돈 각 — 도) */
    private static final double[] STAR_A = {0, 144, 288, 72, 216, 36};
    /** 칼자국 기울기 (도 — 가로 · 비스듬 · 세로가 섞임) */
    private static final double[] ECHO_TILT = {12, -38, 64, 160, -72, 28, 120, -15};

    /**
     * 블레이드 에코 (2026-11-07 — 인페르노 링 대신: 사용자 "멋이 안살고 컨셉하고 안어울림" → 칼의 달인답게 "이미 베었다", 롤 마스터 이 Q · 요네 R): stance 틱 칼집에 손을
     * 얹고 숨을 고름 → stance 부터 every 틱마다 둘레 radius 칸의 적 곁으로 번개처럼 옮겨 감 (blinks 번 — 가까운 적부터 돌아가며 · 적이 없으면 별 꼴 꼭짓점: 떠난 자리
     * 금빛 잔상 · 줄기 · 그 적의 허공에 걸린 칼자국 rfx_jecho — 아직 피해 없음) → home 틱에 처음 자리로 돌아옴 → sheath 틱에 칼을 칼집에 넣는 순간 둘레 radius 칸의
     * 적에게 total (한 번에 넣고 숫자 lines 줄) · 걸린 칼자국이 한꺼번에 갈라져 터짐. 투지 wave.need 이상이면 wave.use 써서 둘레 wave.radius 칸 · 숫자 wave.lines 줄 ·
     * 칼자국 두 배. 몸 쓰는 직업 기준 (제 몸 · 제 칼 — 잔상은 지나간 몸)
     */
    static boolean echo(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        int stance = Math.max(2, (int) k.num("stance", 6));
        int every = Math.max(1, (int) k.num("every", 2));
        int blinks = Math.max(1, (int) k.num("blinks", 6));
        int home = Math.max(stance + blinks * every + 1, (int) k.num("home", 20));
        int sheath = Math.max(home + 2, (int) k.num("sheath", 26));
        Obj wave = k.def().objOrNull("wave");
        boolean surge = wave != null && JobTraits.stacks(sp) >= wave.numOr("need", 3);
        if (surge) JobTraits.useStacks(sp, (int) wave.numOr("use", 3));
        double R = surge ? wave.numOr("radius", 6.5) : k.num("radius", 5);
        int lines = Math.max(1, surge ? (int) wave.numOr("lines", 14) : (int) k.num("lines", 9));
        int per = surge ? 3 : 2;
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 cg = ground(level0, c0);
        Vec3 f0 = Kit.facingOf(sp);
        Kit.noFall(sp, sheath + 20);
        RpgCombat.setSteadfast(sp, sheath + 2);
        // 숨을 고름: 칼집 쪽으로 모이는 금빛 · 둘레 범위 고리 (칼집에 넣을 때까지) · 칼집 소리
        JobFx.charge(sp, j, new Vec3(-0.35, 0.95, 0.15), stance, 0.55);
        JobFx.ring(level0, V.up(cg, 0.08), R, j, false, sheath / 20.0 + 0.1, Opt.of("alpha", 0.3));
        if (surge) {
            JobFx.aura(sp, j, 2.6, 0.45, 0.8);
            SkillFx.play(level0, "block.bell.hit", c0, 1.9, 0.35);
        }
        SkillFx.play(level0, "item.armor.equip_iron", c0, 1.4, 0.7);
        SkillFx.play(level0, "block.amethyst_block.resonate", c0, 1.6, 0.6);
        // 걸린 칼자국 (자리 · 기울어진 쪽) — sheath 에 터짐
        List<Vec3[]> cuts = new ArrayList<>();
        RuneKit.later(stance, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            List<LivingEntity> foes = new ArrayList<>();
            for (LivingEntity e : Kit.around(sp, V.up(cg, 1.0), R, 3.0)) if (Math.hypot(e.getX() - cg.x, e.getZ() - cg.z) <= R) foes.add(e);
            foes.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(cg.x, cg.y, cg.z)));
            int n = blinks;
            Vec3[] spot = new Vec3[n];
            LivingEntity[] tgt = new LivingEntity[n];
            double a0 = Math.atan2(f0.z, f0.x);
            Vec3 prev = cg;
            for (int i = 0; i < n; i++) {
                LivingEntity e = foes.isEmpty() ? null : foes.get(i % foes.size());
                Vec3 want;
                if (e != null) {
                    // 그 적 둘레를 돌아가며 (황금각 137.5° 씩 — 같은 적이면 다른 쪽에서 벰), 몸 폭만큼 띄움
                    Vec3 out = V.flat(V.sub(e.position(), cg));
                    Vec3 u = V.len(out) > 0.2 ? V.unit(out) : f0;
                    want = V.add(e.position(), V.turnY(u, 40 + i * 137.5), 1.25 + Kit.bodyPad(e));
                    tgt[i] = e;
                } else {
                    double a = a0 + Math.toRadians(STAR_A[i % STAR_A.length]);
                    want = new Vec3(cg.x + Math.cos(a) * R * 0.72, cg.y, cg.z + Math.sin(a) * R * 0.72);
                }
                // 벽 앞까지 (앞 자리에서 그 자리로 갈 수 있는 데까지)
                Vec3 d = V.flat(V.sub(want, prev));
                double len = Math.hypot(d.x, d.z);
                double free = len < 0.3 ? 0 : Kit.reachFlat(lv, prev, V.unit(d), len);
                Vec3 end = free > 0.3 ? V.add(prev, V.unit(d), Math.max(0, free - 0.2)) : prev;
                spot[i] = ground(lv, new Vec3(end.x, prev.y, end.z));
                prev = spot[i];
            }
            List<JobMove.Seg> segs = new ArrayList<>();
            for (int i = 0; i < n; i++) segs.add(JobMove.to(i * every, (i + 1) * every, JobMove.at(new Vec3(spot[i].x, spot[i].y, spot[i].z)), JobMove.OUT));
            segs.add(JobMove.to(n * every, home - stance, JobMove.at(new Vec3(c0.x, c0.y, c0.z)), JobMove.INOUT));
            JobMove.run(sp, segs.toArray(new JobMove.Seg[0]));
            for (int i = 0; i < n; i++) {
                int ii = i;
                Vec3 from = i == 0 ? c0 : spot[i - 1];
                RuneKit.later(i * every, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    Vec3 face = V.flat(V.sub(spot[ii], from));
                    RuneKit.shadeClone(sp, sp.level(), from, V.len(face) > 0.1 ? V.unit(face) : f0, 1, SHADE, JobKit.glow(j), Opt.of("life", 0.5, "fade", 0.6,
                            "alpha", 0.85));
                    SkillFx.play(sp.level(), "item.trident.riptide_1", from, 1.6 + 0.06 * ii, 0.45);
                });
                RuneKit.later((i + 1) * every, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    ServerLevel l2 = sp.level();
                    JobFx.dash(l2, V.up(from, 1.0), V.up(spot[ii], 1.0), j, 0.3, JobFx.hide1p(sp));
                    JobFx.slash(sp, j, 2.0, 0.3, 2.2, ECHO_TILT[ii % ECHO_TILT.length] + 180, 0.18);
                    // 그 적 (없으면 그 자리 앞) 의 허공에 걸린 칼자국 — sheath 까지
                    LivingEntity e = tgt[ii];
                    Vec3 mid = e != null && e.isAlive() ? Kit.bodyPoint(e) : V.up(V.add(spot[ii], f0, 1.0), 1.0);
                    for (int q = 0; q < per; q++) {
                        double tilt = Math.toRadians(ECHO_TILT[(ii * per + q) % ECHO_TILT.length] + Kit.rand() * 16 - 8);
                        Vec3 side = V.rightOf(V.flat(V.len(V.flat(V.sub(mid, spot[ii]))) > 0.1 ? V.sub(mid, spot[ii]) : f0));
                        Vec3 dir = V.unit(V.add(V.scale(side, Math.cos(tilt)), V.UP, Math.sin(tilt)));
                        Vec3 at = V.add(mid, new Vec3(Kit.rand() - 0.5, Kit.rand() - 0.5, Kit.rand() - 0.5), 0.35);
                        double len = 3.0 + Kit.rand() * 1.0 + (e != null ? Kit.bodyPad(e) : 0);
                        double hold = Math.max(0.1, (sheath - stance - (ii + 1) * every) / 20.0);
                        JobFx16.echo(l2, at, dir, len, 0, 1, 0.05, false, Opt.empty(), JobFx.outer(j), WHITE);
                        RuneKit.later(1, () -> JobFx16.echo(sp.level(), at, dir, len, 1, 4, hold - 0.05, true, Opt.of("fade", 0.0, "alpha", 1.0), JobFx.outer(j), WHITE));
                        cuts.add(new Vec3[] {at, dir, new Vec3(len, 0, 0)});
                    }
                    JobFx.glint(l2, mid, 0.5, j, 0.2, Opt.empty());
                    SkillFx.play(l2, "entity.player.attack.sweep", mid, 1.5 + 0.07 * ii, 0.55);
                    SkillFx.play(l2, "block.amethyst_block.hit", mid, 1.9, 0.35);
                });
            }
        });
        // 돌아와 칼을 칼집에 넣음 (sheath 1틱 앞 — 금빛이 칼집으로)
        RuneKit.later(home, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 hip = V.up(V.add(sp.position(), V.rightOf(Kit.facingOf(sp)), -0.35), 0.95);
            JobFx.glint(sp.level(), hip, 0.6, j, (sheath - home) / 20.0 + 0.1, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
        });
        RuneKit.later(sheath, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = ground(lv, sp.position());
            // 철컥 — 걸린 칼자국이 한꺼번에 갈라져 터짐
            SkillFx.play(lv, "item.armor.equip_iron", c, 1.8, 1.0);
            SkillFx.play(lv, "block.iron_trapdoor.close", c, 1.7, 0.8);
            SkillFx.play(lv, "entity.player.attack.sweep", c, 1.3, 1.0);
            SkillFx.play(lv, "block.amethyst_block.break", c, 1.6, 1.0);
            for (Vec3[] cut : cuts) {
                JobFx16.echo(lv, cut[0], cut[1], cut[2].x * 1.3, 5, 3, 0.3, false, Opt.empty(), JobFx.outer(j), WHITE);
                JobFx.glint(lv, cut[0], 0.7, j, 0.2, Opt.empty());
            }
            JobFx.ring(lv, V.up(c, 0.1), R, j, false, 0.35, Opt.of("alpha", 1.0));
            SkillFx.shock(lv, V.up(c, 0.1), JobFx.outer(j), R * 1.05, 0.35);
            JobFx.slash(sp, j, 2.2, 0.25, 2.8, 4.0, 0.25);
            for (LivingEntity e : Kit.around(sp, V.up(c, 1.0), R, 3.0)) {
                if (Math.hypot(e.getX() - c.x, e.getZ() - c.z) > R + Kit.bodyPad(e)) continue;
                Vec3 b = Kit.bodyPoint(e);
                JobFx.two(lv, "jcuts", CUTS, b, 0.35, JobFx.hide1p(sp).with("size", 1.8 + Kit.bodyPad(e), "spin", Kit.rand() * 360, "fade", 0.3), JobFx.outer(j),
                        JobFx.inner(j));
                JobKit.strikeSplit(sp, e, j, P, k.total(), lines, 1, false, true, life, 0, d -> JobImpact.hit(sp, e, j, 1.4));
            }
        });
        return true;
    }

    /** 고리를 따라 서 있는 불꽃 (rfx_jflame — 이어 돌림): life 초 · 불티 (드래곤 블러드) */
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

    /** 땅 높이로 내린 자리 */
    private static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, V.up(p, 0.6), p.y), p.z);
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

    // ------------------------------------------------------------ 엑스칼리버

    /** 빛의 대검 solo 번호 (주인마다 하나 — 몸을 따라 매 틱 다시 그림) */
    private static final double EXCAL_SOLO = 161.0;
    /** 빛의 대검이 다 자란 길이 (칸) */
    private static final double EXCAL_LEN = 15.0;

    /** 치켜든 칼의 손 자리 (발 기준 — 두 손이 머리 위) */
    private static Vec3 raisedHands(ServerPlayer sp) {
        return V.up(V.add(sp.position(), Kit.facingOf(sp), 0.12), 2.25);
    }

    /**
     * 엑스칼리버 (2026-11-07 — 스타폴 블레이드 대신: 사용자 "전사 궁 같음 소드마스터라는 이름에 맞게 페이트 시리즈에 세이버 아서처럼 엑스칼리버 같은 느낌으로 내리꽂는
     * 커다란 검격"): 새벽빛 하늘 · 칼을 두 손으로 하늘 높이 치켜들면 (0 ~ swingAt) 둘레의 금빛 알갱이가 칼로 빨려 들며 칼날이 하늘에 닿는 빛의 대검 (rfx_jexcal — 위로
     * EXCAL_LEN 칸) 으로 자람 · 밀려나지 않음 → swingAt ~ hitAt 내리침 (빛의 대검이 위 → 앞 아래로 돎 · 늘어진 빛) → hitAt 에 앞 range 칸 (폭 width · 발 앞 둘레 near
     * 칸) 을 거대한 금빛 검격이 휩씀 (세로 큰 검기 셋 · 땅을 달리는 빛의 길 · beam — 큰 숫자) → afterAt 에 검격이 지나간 자리에서 빛의 검이 솟구침 (after). 화면은
     * 흔들지 않음. 몸 쓰는 직업 기준 (빛은 제 칼에서 자람)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int swingAt = Math.max(8, (int) k.num("swingAt", 22));
        int hitAt = Math.max(swingAt + 2, (int) k.num("hitAt", 26));
        int afterAt = Math.max(hitAt + 10, (int) k.num("afterAt", 37));
        double range = k.num("range", 24);
        double half = k.num("width", 4) / 2;
        double near = k.num("near", 3);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        double[] gold = JobFx.outer(j);
        double[] ivory = JobFx.inner(j);
        RpgCombat.setSteadfast(sp, afterAt + 4);
        Ult.sky(level0, c0, "rpg:ult_excalibur", afterAt + 40);
        Ult.flash(level0, c0, gold, Opt.of("radius", 8.0, "time", new double[] {0.03, 0.05, 0.3}), sp);
        SkillFx.play(level0, "block.beacon.power_select", c0, 1.2, 0.9);
        SkillFx.play(level0, "block.beacon.activate", c0, 1.4, 0.7);
        SkillFx.play(level0, "block.amethyst_block.resonate", c0, 0.9, 0.8);
        // 치켜듦: 발밑 금빛 고리 셋 (안으로 조여 듦) · 하늘에서 내려오는 빛줄기 · 칼끝으로 빨려 드는 금빛 알갱이
        for (int q = 0; q < 3; q++) {
            int qq = q;
            RuneKit.later(q * 5, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                JobFx.ring(sp.level(), V.up(sp.position(), 0.08), 3.6 - qq * 0.9, j, true, (swingAt - qq * 5) / 20.0 + 0.05, Opt.of("alpha", 0.9 - qq * 0.15));
            });
        }
        RuneKit.godRays(sp, level0, c0, 1.8, 7, JobKit.glow(j), JobKit.core(j), JobFx.hide1p(sp).with("h", 14.0, "life", swingAt / 20.0, "alpha", 0.6));
        JobFx.charge(sp, j, new Vec3(0.0, 2.3, 0.12), swingAt - 2, 1.2);
        for (int t = 1; t < swingAt; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 hands = raisedHands(sp);
                // 빛의 대검: 0 ~ 3 모여 듦 → 4 ~ 11 자라남 → 12 번쩍 → 빛남 (이어 돌림)
                int grow0 = 4;
                int full = Math.min(swingAt - 2, 12);
                double len = tt < grow0 ? 3.0 : Math.min(EXCAL_LEN, 3.0 + (EXCAL_LEN - 3.0) * (tt - grow0) / (double) Math.max(1, full - grow0));
                int frame = tt < grow0 ? 0 : tt < full ? 1 : tt == full ? 2 : 3 + (tt % 3);
                JobFx16.excal(lv, hands, V.UP, len, frame, 1, 0.12, false, Opt.of("solo", EXCAL_SOLO, "owner", (double) sp.getId(), "fade", 0.0, "vel",
                        RuneKit.bodyVel(sp), "alpha", tt < grow0 ? 0.55 : 1.0), gold, ivory);
                // 둘레 6칸에서 칼날로 빨려 드는 금빛 알갱이 (2틱마다)
                if (tt % 2 == 0) {
                    for (int i = 0; i < 5; i++) {
                        double a = Kit.rand() * Math.PI * 2;
                        Vec3 from = V.up(V.add(sp.position(), new Vec3(Math.cos(a), 0, Math.sin(a)), 4.5 + Kit.rand() * 2.5), 0.3 + Kit.rand() * 2.5);
                        Vec3 to = V.up(hands, Kit.rand() * len * 0.8);
                        Vec3 d = V.sub(to, from);
                        SkillFx.fx(lv, "rpg:arrow_spark", from, Opt.of("dir", V.unit(d), "speed", V.len(d) / 0.35, "size", 0.16, "grow", 0.4, "life", 0.35, "color",
                                i % 2 == 0 ? ivory : gold, "alpha", 1.0));
                    }
                }
                if (tt == full) {
                    JobFx.glint(lv, V.up(hands, len), 2.4, j, 0.3, Opt.empty());
                    SkillFx.play(lv, "block.respawn_anchor.set_spawn", sp.position(), 1.4, 0.9);
                    SkillFx.play(lv, "block.bell.resonate", sp.position(), 1.6, 0.7);
                }
            });
        }
        // 내리침: swingAt ~ hitAt — 빛의 대검이 위 → 앞 아래로 돎 (늘어진 빛) · 큰 세로 참격 판
        int swing = hitAt - swingAt;
        for (int t = 0; t <= swing; t++) {
            int tt = t;
            RuneKit.later(swingAt + t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 f = Kit.facingOf(sp);
                double u = JobMove.ease(tt / (double) swing, JobMove.IN);
                double ang = Math.toRadians(90 - 118 * u); // 위 (90°) → 앞 아래 (−28°)
                Vec3 dir = V.unit(V.add(V.scale(f, Math.cos(ang)), V.UP, Math.sin(ang)));
                Vec3 hands = V.up(V.add(sp.position(), f, 0.12 + 0.55 * u), 2.25 - 1.0 * u);
                JobFx16.excal(lv, hands, dir, EXCAL_LEN, 6, 1, 0.12, false, Opt.of("solo", EXCAL_SOLO, "owner", (double) sp.getId(), "fade", 0.0),
                        gold, ivory);
                if (tt == 0) {
                    SkillFx.play(lv, "item.trident.riptide_3", sp.position(), 0.9, 1.0);
                    SkillFx.play(lv, "entity.player.attack.sweep", sp.position(), 0.6, 1.0);
                }
            });
        }
        RuneKit.later(hitAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            beamStrike(sp, j, k, P, range, half, near, life);
        });
        RuneKit.later(afterAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 f = Kit.facingOf(sp);
            Vec3 g0 = ground(lv, sp.position());
            double L = Math.max(2.0, RuneKit.rayLength(lv, V.up(g0, 1.0), f, range));
            // 검격이 지나간 자리에서 빛의 검이 솟구침 (2칸마다 · 좌우로 엇갈림) · 소리
            int i = 0;
            for (double s = 1.6; s <= L; s += 2.0, i++) {
                double side = (i % 2 == 0 ? 1 : -1) * (0.4 + Kit.rand() * (half - 0.4));
                Vec3 g = ground(lv, V.add(V.add(g0, f, s), V.rightOf(f), side));
                int delay = (int) (s / 8.0);
                RuneKit.later(delay, () -> {
                    JobFx16.risingBlade(sp.level(), g, 4.5 + Kit.rand() * 2.0, 0.55, Opt.empty(), gold, ivory);
                    SkillFx.sparks(sp.level(), V.up(g, 0.4), ivory, 4, 3.5);
                });
            }
            SkillFx.play(lv, "block.beacon.deactivate", g0, 1.6, 0.8);
            SkillFx.play(lv, "item.trident.thunder", V.add(g0, f, L / 2), 1.6, 0.6);
            for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(g0, 1.0), f, L, half)) {
                LivingEntity e = h.e();
                JobKit.strike(sp, e, j, P, k.num("after", 12), false, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.4));
            }
        });
        return true;
    }

    /**
     * 엑스칼리버의 검격 (hitAt): 발 앞 터짐 · 앞으로 달리는 거대한 금빛 세로 검기 (셋이 겹쳐 — 가운데 가장 큼) · 땅을 달리는 빛의 길 (넓은 빛줄기) · 길 끝 하늘로
     * 빛살 · 1인칭 눈앞 세로 베기 · 섬광 — 앞 range 칸 (폭 2 × half) · 발 앞 둘레 near 칸의 적에게 beam (큰 숫자)
     */
    private static void beamStrike(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, double range, double half, double near, String life) {
        ServerLevel lv = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 g0 = ground(lv, sp.position());
        Vec3 hit = V.add(g0, f, 1.8);
        double[] gold = JobFx.outer(j);
        double[] ivory = JobFx.inner(j);
        double L = Math.max(2.0, RuneKit.rayLength(lv, V.up(g0, 1.0), f, range));
        double speed = 60.0;
        Vec3 bladeDir = V.unit(V.add(V.scale(f, Math.cos(Math.toRadians(-28))), V.UP, Math.sin(Math.toRadians(-28))));
        JobFx16.excal(lv, V.up(V.add(g0, f, 0.67), 1.25), bladeDir, EXCAL_LEN, 7, 1, 0.3, false, Opt.of("solo", EXCAL_SOLO, "owner", (double) sp.getId()), gold,
                ivory);
        // 발 앞: 큰 타격 · 터짐 · 금빛 고리 · 충격파
        JobFx.two(lv, "jimpact", JobImpact.IMPACT, V.up(hit, 0.6), 0.35, Opt.of("size", 2.6, "spin", Kit.rand() * 360, "fade", 0.25), gold, WHITE);
        JobFx.burst(lv, V.up(hit, 0.8), 3.2, j, 0.45, Opt.empty());
        JobFx.ring(lv, V.up(hit, 0.08), near, j, false, 0.45, Opt.of("alpha", 1.0));
        SkillFx.shock(lv, V.up(hit, 0.1), gold, near * 1.3, 0.45);
        // 앞으로 달리는 거대한 금빛 세로 검기 (가운데 크게 · 양옆 조금 작게 0.8칸 비껴 — 영상 1 뒤 키움: 가운데 반 크기 4.6 → 6.4)
        JobFx.bigWave(lv, sp, V.up(V.add(g0, f, 1.2), 4.6), f, speed, 6.4, 90, L / speed + 0.18, j, gold, ivory, Opt.empty());
        for (int s = -1; s <= 1; s += 2) {
            Vec3 at = V.up(V.add(V.add(g0, f, 1.0), V.rightOf(f), 0.8 * s), 3.3);
            JobFx.bigWave(lv, null, at, f, speed * 0.96, 4.6, 90 + 6 * s, L / speed + 0.18, j, gold, ivory, JobFx.hide1p(sp).with("alpha", 0.6));
        }
        // 땅을 달리는 빛의 길 (넓은 빛줄기 — 앞에서부터 차례로 켜짐) · 길 끝 빛살
        for (double s = 1.0; s < L; s += 2.4) {
            double ss = s;
            int delay = (int) (s / speed * 20);
            RuneKit.later(delay, () -> {
                Vec3 a = V.up(V.add(g0, f, ss), 0.15);
                Vec3 b = V.up(V.add(g0, f, Math.min(L, ss + 2.4)), 0.15);
                JobFx.beam(sp.level(), a, b, gold, ivory, 2.6, 0.7, Opt.of("alpha", 1.0));
                SkillFx.sparks(sp.level(), V.up(a, 0.3), ivory, 3, 3.0);
            });
        }
        RuneKit.later((int) (L / speed * 20) + 1, () -> {
            Vec3 end = V.add(g0, f, L);
            JobFx.burst(sp.level(), V.up(end, 1.5), 3.6, j, 0.5, Opt.empty());
            RuneKit.godRays(sp, sp.level(), end, 2.6, 8, JobKit.glow(j), JobKit.core(j), Opt.of("h", 12.0, "life", 0.7, "alpha", 0.8, "skipNear", true));
        });
        JobFx.slash(sp, j, 2.2, 0.25, 3.4, 92.0, 0.3);
        Ult.flash(lv, g0, ivory, Opt.empty(), sp);
        SkillFx.play(lv, "item.trident.thunder", g0, 1.2, 1.0);
        SkillFx.play(lv, "item.mace.smash_ground_heavy", g0, 0.9, 1.0);
        SkillFx.play(lv, "entity.generic.explode", hit, 1.3, 0.7);
        SkillFx.play(lv, "block.beacon.activate", hit, 1.8, 1.0);
        java.util.Set<UUID> seen = new HashSet<>();
        for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(g0, 1.0), f, L, half)) {
            LivingEntity e = h.e();
            if (!seen.add(e.getUUID())) continue;
            int delay = (int) Math.max(0, Math.floor(h.t() / speed * 20));
            JobKit.strike(sp, e, j, P, k.num("beam", 28), false, true, life, delay, d -> JobImpact.hit(sp, e, j, 2.4));
        }
        for (LivingEntity e : Kit.around(sp, V.up(hit, 1.0), near, 3.0)) {
            if (!seen.add(e.getUUID())) continue;
            JobKit.strike(sp, e, j, P, k.num("beam", 28), false, true, life, 0, d -> JobImpact.hit(sp, e, j, 2.4));
        }
    }
}
