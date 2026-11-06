package mmorpg.fabric.job;

import java.util.List;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-06 수호기사 반전 어벤저 (재설계안 2판 — 사용자 "다음 작업", jobs.json advance avenger — 불씨 주황 · 재, 특성 「앙갚음」 — JobTraits15): 1차의 막고 대신 맞는
 * 탱커를 뒤집어 막지 않고 다 맞은 뒤 한 번에 되갚는 근접 딜러 — 리프라이절 · 그러지 차지 · 레크닝 · 페인 이터 · 조브레이커. 앙갚음 (받은 피해를 쌓은 체력 양) 을 쓰는
 * 스킬은 그만큼을 고정 피해로 더함 (JobKit.strikeFlat). 몸 쓰는 직업이라 제 몸 · 방패 동작만 (늑대의 턱 · 불씨 기둥은 그림). 단타는 크게 감았다가 큰 타격, 화면은 흔들지
 * 않음.
 */
final class AvengerSkills {
    private AvengerSkills() {}

    static final double[] EMBER = JobTraits15.EMBER;
    static final double[] EMBER_CORE = JobTraits15.EMBER_CORE;
    static final double[] ASH = JobTraits15.ASH;
    static final double[] ASH_CORE = JobTraits15.ASH_CORE;
    static final double[] WHITE_HOT = JobTraits15.WHITE_HOT;

    static void register() {
        JobSystem.register("avenger_reprisal", AvengerSkills::reprisal);
        JobSystem.register("avenger_grudge", AvengerSkills::grudge);
        JobSystem.register("avenger_reckoning", AvengerSkills::reckoning);
        JobSystem.register("avenger_pain", AvengerSkills::pain);
        JobSystem.register("avenger_ult", AvengerSkills::ult);
    }

    /** 방패 앞 (가슴 높이 · 앞 ahead 칸) */
    private static Vec3 shieldFront(ServerPlayer sp, double ahead) {
        return V.up(V.add(sp.position(), Kit.facingOf(sp), ahead), 1.1);
    }

    /** 가장 가까운 적 (range 칸 — 없으면 null) */
    private static LivingEntity nearest(ServerPlayer sp, double range) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : Kit.around(sp, sp.position(), range, 4)) {
            if (!e.isAlive() || e.isRemoved()) continue;
            double d = e.distanceToSqr(sp);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** 앙갚음을 실은 타격의 불씨 (맞은 적 — 실은 양이 클수록 크게) */
    private static void vengeanceFx(ServerPlayer sp, LivingEntity e, double flat, double P) {
        if (!(flat > 0) || !(e.level() instanceof ServerLevel el)) return;
        double q = Math.min(1, flat / Math.max(1, P * 4));
        Vec3 b = Kit.bodyPoint(e);
        JobFx.burst(el, b, 1.0 + q * 1.4 + Kit.bodyPad(e) * 0.4, EMBER, EMBER_CORE, 0.3 + q * 0.15, Opt.of("vel", RuneKit.entVel(e)));
        SkillFx.sparks(el, b, EMBER_CORE, 6 + (int) (q * 12), 3 + q * 3);
        if (q > 0.4) SkillFx.play(el, "entity.blaze.shoot", b, 0.6, 0.8);
    }

    // ------------------------------------------------------------ 리프라이절

    /**
     * 리프라이절 (롤 세트 Q): 방패를 뒤로 크게 당겼다가 (bashAt 틱 — 방패에 불씨가 모임) 앞 reach 칸 (arc°) 의 적 (가까운 셋) 을 후려침 (bash + 앙갚음의 use 몫 — 맞힐 적이
     * 있을 때만 씀)
     */
    static boolean reprisal(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int bashAt = Math.max(2, (int) k.num("bashAt", 6));
        RpgCombat.setSteadfast(sp, bashAt + 2);
        JobFx.charge(sp, j, new Vec3(-0.45, 1.1, -0.25), bashAt, 0.65);
        SkillFx.play(level0, "item.firecharge.use", sp.position(), 0.7, 0.5);
        SkillFx.play(level0, "item.armor.equip_netherite", sp.position(), 0.8, 0.8);
        RuneKit.later(bashAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            List<LivingEntity> foes = RuneKit.coneTargets(sp, k.num("reach", 3.5), k.num("arc", 100), 3);
            double flat = foes.isEmpty() ? 0 : JobTraits15.vengeanceTake(sp, k.num("use", 0.3));
            // 후려침: 눈앞 가로 큰 베기 · 앞의 불씨 부채 · 불씨 검기 · 방패 앞 터짐
            JobFx.slash(sp, j, 1.6, 0.3, 2.2, 15.0, 0.22);
            JobFx15.fury(lv, V.up(V.add(c, f, 0.3), 0.06), f, k.num("reach", 3.5) + 0.5, 0, 5, 0.45, Opt.of("alpha", 0.95), EMBER, EMBER_CORE);
            JobFx.bigWave(lv, sp, V.up(V.add(c, f, 0.8), 1.1), f, 12, 1.4, 10, 0.3, j, EMBER, EMBER_CORE, Opt.empty());
            JobFx.burst(lv, shieldFront(sp, 1.1), 1.3 + Math.min(1.2, flat / Math.max(1, P * 4)), EMBER, EMBER_CORE, 0.28, JobFx.hide1p(sp));
            SkillFx.play(lv, "entity.player.attack.knockback", c, 0.7, 1.0);
            SkillFx.play(lv, "item.shield.block", c, 0.6, 0.9);
            if (flat > 0) SkillFx.play(lv, "entity.blaze.shoot", c, 0.7, 0.7);
            for (LivingEntity e : foes) {
                JobKit.strikeFlat(sp, e, j, P, k.num("bash", 4), flat, false, false, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 1.5);
                    vengeanceFx(sp, e, flat, P);
                    if (JobKit.pushable(e)) RuneKit.knock(e, f, 0.45, 0.15);
                });
            }
            if (flat >= 1) JobSystem.note(sp, "§6앙갚음 §7+" + Math.round(flat));
        });
        return true;
    }

    // ------------------------------------------------------------ 그러지 차지

    /**
     * 그러지 차지 (롤 알리스타 W): memory 틱 동안 나를 가장 많이 때린 적 (range 칸 — 없으면 겨눈 적 · 가장 가까운 적 · 앞 fallback 칸) 머리 위에 불씨 과녁 → 2틱 뒤 불씨를
     * 끌며 그 앞으로 돌진해 hitAt 틱 방패로 들이받음 (둘레 radius 칸 ram · 멀리 밀쳐냄). 묶였거나 타고 있으면 쓰지 않음
     */
    static boolean grudge(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int hitAt = Math.max(4, (int) k.num("hitAt", 8));
        double range = k.num("range", 12);
        LivingEntity e = JobTraits15.topAttacker(sp, range, (int) k.num("memory", 160));
        boolean grudge = e != null;
        if (e == null) e = Kit.pickTarget(sp, range, 20);
        if (e == null) e = nearest(sp, k.num("fallback", 6) + 2);
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 land;
        Vec3 dir;
        if (e != null) {
            Vec3 back = V.flat(V.sub(c0, e.position()));
            Vec3 u = V.len(back) > 0.1 ? back : V.scale(f, -1);
            dir = V.scale(u, -1);
            Vec3 s = JobTraits12.beyond(sp, e.position(), u, 0.9 + Kit.bodyPad(e));
            land = s != null ? s : c0;
        } else {
            dir = f;
            double can = RuneKit.dashDistance(sp, f, k.num("fallback", 6), 0.3, 1.5);
            land = V.add(c0, f, can);
        }
        LivingEntity target = e;
        Vec3 to = land;
        RpgCombat.setSteadfast(sp, hitAt + 3);
        // 노림: 그 적 머리 위 불씨 과녁 · 내 발밑 불씨
        if (target != null) {
            JobFx.target(level0, RuneKit.overHead(target, 0.6), 0.45 + Kit.bodyPad(target) * 0.2, j, V.scale(dir, -1), 0, hitAt / 20.0 + 0.1, Opt.of("vel",
                    RuneKit.entVel(target)));
            if (grudge) SkillFx.play(level0, "entity.polar_bear.warning", c0, 0.7, 0.9);
        }
        SkillFx.sparks(level0, V.up(c0, 0.3), EMBER, 10, 3);
        SkillFx.play(level0, "entity.ravager.step", c0, 0.8, 1.0);
        if (V.hdist(to, c0) > 0.4) JobMove.run(sp, JobMove.hold(0, 2), JobMove.to(2, hitAt, JobMove.at(to), JobMove.IN));
        // 돌진: 불씨를 끄는 길 (매 틱 앞 자리 → 지금 자리)
        Vec3[] last = {c0};
        for (int t = 3; t <= hitAt; t++) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 p = sp.position();
                if (V.hdist(p, last[0]) > 0.3) {
                    JobFx.line(lv, V.up(last[0], 0.9), V.up(p, 0.9), EMBER, EMBER_CORE, 0.3, Opt.of("alpha", 0.9));
                    SkillFx.fx(lv, "rpg:arrow_spark", V.up(p, 0.4), Opt.of("dir", V.UP, "speed", 1.4, "size", 0.14, "grow", 0.8, "life", 0.4, "color", EMBER, "alpha", 0.95));
                    // 끄는 불씨: 지나간 땅에 낮은 불길 (영상 1: 가는 줄만으로는 돌진 길이 보이지 않았음)
                    JobFx15.cinder(lv, ThunderlordSkills.ground(lv, last[0]), 1.3, 1, 5, 0.4, false, Opt.of("alpha", 0.85, "fade", 0.3), EMBER, EMBER_CORE);
                }
                last[0] = p;
            });
        }
        JobFx.speed(sp, j, hitAt / 20.0, 0.6);
        RuneKit.later(hitAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 hit = V.add(c, dir, 0.9);
            // 들이받음: 눈앞 큰 베기 · 앞의 불씨 터짐 · 충격 고리 · 불씨 검기
            JobFx.slash(sp, j, 1.5, 0.3, 2.0, 190.0, 0.22);
            JobFx.burst(lv, V.up(hit, 1.0), 2.0, EMBER, EMBER_CORE, 0.32, JobFx.hide1p(sp));
            JobFx.smash(lv, V.up(hit, 0.05), 1.8, j, 0.4, Opt.empty());
            RuneKit.novaRing(lv, hit, 0.6, 3.0, EMBER, EMBER_CORE, Opt.of("life", 0.35));
            JobFx.bigWave(lv, sp, V.up(hit, 1.0), dir, 10, 1.2, 90, 0.25, j, EMBER, EMBER_CORE, Opt.empty());
            SkillFx.play(lv, "entity.goat.ram_impact", c, 0.7, 1.0);
            SkillFx.play(lv, "item.shield.block", c, 0.55, 0.9);
            for (LivingEntity x : Kit.around(sp, hit, k.num("radius", 1.8), 3)) {
                if (!x.isAlive() || x.isRemoved()) continue;
                JobKit.strike(sp, x, j, P, k.num("ram", 2.4), false, false, life, 0, d -> {
                    JobImpact.hit(sp, x, j, 1.6);
                    if (JobKit.pushable(x)) RuneKit.knock(x, dir, k.num("knock", 1.4), 0.4);
                });
            }
            if (target != null && target.isAlive() && !target.isRemoved() && V.hdist(target.position(), hit) > k.num("radius", 1.8) + Kit.bodyPad(target)
                    && target.distanceTo(sp) <= 3.0 + Kit.bodyPad(target)) {
                JobKit.strike(sp, target, j, P, k.num("ram", 2.4), false, false, life, 0, d -> {
                    JobImpact.hit(sp, target, j, 1.6);
                    if (JobKit.pushable(target)) RuneKit.knock(target, dir, k.num("knock", 1.4), 0.4);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 레크닝

    /**
     * 레크닝 (롤 세트 W): 방패를 허리 뒤로 끌어당겨 (blastAt 틱 — 몸에서 불씨가 방패로 빨려 들고 발밑 불씨 기둥) 앞 reach 칸 부채꼴 (arc°) 로 내지름 (blast · 큰 숫자) —
     * 앙갚음을 모두 씀: 가운데 (폭 core × 2칸) 적은 방어구를 무시하고 (고정 피해) 앙갚음 전부를, 옆의 적은 × side 를 더함. 맞힐 적이 없으면 앙갚음은 그대로
     */
    static boolean reckoning(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int blastAt = Math.max(4, (int) k.num("blastAt", 12));
        double reach = k.num("reach", 7.5);
        RpgCombat.setSteadfast(sp, blastAt + 3);
        Vec3 c0 = sp.position();
        // 모음: 방패 뒤의 빛 · 몸에서 방패로 빨려 드는 불씨 · 발밑 불씨 기둥 · 땅의 금
        JobFx.charge(sp, j, new Vec3(0.35, 1.0, -0.45), blastAt, 1.0);
        JobFx15.cinder(level0, ThunderlordSkills.ground(level0, c0), 2.6, 0, 2, 0.15, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), EMBER, EMBER_CORE);
        RuneKit.later(3, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx15.cinder(sp.level(), ThunderlordSkills.ground(sp.level(), sp.position()), 2.6, 2, 4, (blastAt - 3) / 20.0, true,
                    JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "fade", 0.1), EMBER, EMBER_CORE);
        });
        RuneKit.glowCrack(level0, V.up(c0, 0.04), 1.8, EMBER, EMBER_CORE, Opt.of("life", blastAt / 20.0 + 0.4));
        for (int t = 0; t < blastAt; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 f = Kit.facingOf(sp);
                Vec3 shield = V.up(V.add(V.add(sp.position(), V.rightOf(f), 0.35), f, -0.45), 1.0);
                for (int i = 0; i < 3; i++) {
                    Vec3 from = V.up(V.add(sp.position(), new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), 0.8), 0.4 + Kit.rand() * 1.4);
                    JobFx.line(lv, from, shield, EMBER, EMBER_CORE, 0.15, Opt.of("alpha", 0.8));
                }
            });
        }
        SkillFx.play(level0, "block.respawn_anchor.charge", c0, 0.7, 0.8);
        SkillFx.play(level0, "entity.blaze.ambient", c0, 0.6, 0.8);
        RuneKit.later(blastAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            Vec3 along = V.rightOf(f);
            List<LivingEntity> foes = RuneKit.coneTargets(sp, reach, k.num("arc", 80), 12);
            double v = foes.isEmpty() ? 0 : JobTraits15.vengeanceTake(sp, 1.0);
            double q = Math.min(1, v / Math.max(1, P * 6));
            // 내지름: 땅의 불씨 부채 (가운데 흰 불길) · 커다란 불씨 검기 · 가운데 줄의 흰 불 · 눈앞 큰 베기
            JobFx.slash(sp, j, 1.8, 0.25, 2.8, 5.0, 0.26);
            JobFx15.fury(lv, V.up(V.add(c, f, 0.4), 0.06), f, reach + 0.5, 0, 8, 0.9, Opt.of("alpha", 1.0), EMBER, EMBER_CORE);
            JobFx.bigWave(lv, sp, V.up(V.add(c, f, 0.9), 1.1), f, 22, 2.2 + q * 0.8, 0, 0.42, j, EMBER, EMBER_CORE, Opt.empty());
            for (int i = 0; i < 4; i++) {
                int ii = i;
                RuneKit.later(i, () -> {
                    Vec3 p = V.up(V.add(c, f, 1.5 + ii * 1.6), 1.0);
                    JobFx.burst(lv, p, 1.2 + q * 0.8, WHITE_HOT, EMBER_CORE, 0.3, Opt.empty());
                    SkillFx.sparks(lv, p, EMBER, 6, 4);
                });
            }
            JobFx.groundWave(lv, sp, V.add(c, f, 0.6), f, 1.6, reach, 20, j);
            Ult.flash(lv, V.add(c, f, 2), EMBER, Opt.of("radius", 5.0, "time", new double[] {0.02, 0.05, 0.3}), sp);
            SkillFx.play(lv, "entity.generic.explode", c, 0.9, 0.9);
            SkillFx.play(lv, "item.firecharge.use", c, 0.6, 1.0);
            SkillFx.play(lv, "item.mace.smash_ground_heavy", c, 0.9, 0.8);
            if (v > 0) SkillFx.play(lv, "entity.blaze.shoot", c, 0.5, 1.0);
            for (LivingEntity e : foes) {
                double side = Math.abs(V.dot(V.sub(e.position(), c), along));
                boolean core = side <= k.num("core", 1.0) + Kit.bodyPad(e) * 0.5;
                double flat = v * (core ? 1.0 : k.num("side", 0.5));
                JobKit.strikeFlat(sp, e, j, P, k.num("blast", 7.2), flat, core, true, life, 0, d -> {
                    JobImpact.hit(sp, e, j, core ? 2.4 : 1.8);
                    vengeanceFx(sp, e, flat, P);
                    if (JobKit.pushable(e)) RuneKit.knock(e, f, core ? 1.0 : 0.6, 0.3);
                });
            }
            if (v >= 1) JobSystem.note(sp, "§6앙갚음 §7+" + Math.round(v) + " (가운데 · 옆 절반)");
        });
        return true;
    }

    // ------------------------------------------------------------ 페인 이터

    /**
     * 페인 이터 (롤 알리스타 R — 대신 받기는 롤에 없는 장치): dur 틱 태세 (강화 지속) — 둘레 radius 칸 아군이 받을 피해의 share 를 내가 대신 받음 (앙갚음으로 쌓임) · 내
     * 받는 피해 − cut (JobTraits15). 그림: 발밑에서 솟는 불씨 기둥 · 둘레 아군에게서 불씨 줄 · 포효
     */
    static boolean pain(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) JobUpgrades.num(sp, k, "dur", 160);
        JobTraits.startStance(sp, k, dur);
        JobTraits15.painStart(sp);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 g = ThunderlordSkills.ground(level, c);
        JobFx15.cinder(level, g, 3.2, 0, 3, 0.5, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), EMBER, EMBER_CORE);
        RuneKit.novaRing(level, c, 0.6, 3.5, EMBER, EMBER_CORE, Opt.of("life", 0.45));
        RuneKit.glowCrack(level, V.up(c, 0.04), 2.2, EMBER, EMBER_CORE, Opt.of("life", 1.2));
        JobFx.smoke(level, V.up(c, 1.4), 1.4, ASH, ASH_CORE, 0.9, JobFx.hide1p(sp).with("dir", V.UP, "speed", 0.8, "alpha", 0.55));
        int n = 0;
        for (ServerPlayer o : JobTraits4.allies(sp, c, k.num("radius", 8))) {
            if (o == sp) continue;
            n++;
            JobFx.beam(level, JobTraits15.chest(o), JobTraits15.chest(sp), EMBER, EMBER_CORE, 1.2, 0.5, Opt.of("alpha", 0.9));
            SkillFx.sparks(level, JobTraits15.chest(o), EMBER, 6, 2.5);
        }
        JobFx.aura(sp, j, 2.8, 1.0, 0.9);
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.45);
        SkillFx.play(level, "entity.ravager.roar", c, 0.7, 0.9);
        SkillFx.play(level, "entity.blaze.ambient", c, 0.5, 0.8);
        Notify.notify(sp, "§6§l" + k.name() + " §7— 아군 피해의 " + Math.round(k.num("share", 0.5) * 100) + "% 를 대신 받음" + (n > 0 ? " (" + n + ")" : "") + " · " + dur / 20
                + "초", 40);
        return true;
    }

    // ------------------------------------------------------------ 조브레이커

    /**
     * 조브레이커 (롤 세트 R): 늑대의 턱을 찢다 (rpg:ult_fenrir) — 겨눈 적 (range 칸 — 없으면 가장 가까운 적 · 앞 fallback 칸) 에게 달려들어 grabAt 틱에 붙잡고 그 적을
     * 끌어안은 채 하늘 높이 (height 칸) 뛰어올랐다 앞 ahead 칸 땅에 slamAt 틱 내리꽂음 (slam + 앙갚음 전부 + 그 적 최대 체력 × maxHp (위력 × maxHpCap 까지) · 큰 숫자 ·
     * stun 틱 기절) → 거대한 늑대의 턱이 찢어지며 둘레 radius 칸 다른 적에게 여파 (그 피해의 splash). 보스 · 큰 몹 · 허수아비는 들지 않고 그 위로 뛰어올라 내리찍음,
     * 묶였거나 타고 있으면 뛰지 않고 그 자리에서
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int grabAt = Math.max(2, (int) k.num("grabAt", 6));
        int slamAt = Math.max(grabAt + 10, (int) k.num("slamAt", 26));
        double height = k.num("height", 4.5);
        LivingEntity e = Kit.pickTarget(sp, k.num("range", 8), 22);
        if (e == null) e = nearest(sp, k.num("range", 8));
        Vec3 c0 = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        boolean free = !Status.isRooted(sp) && !Kit.ridingAny(sp);
        Ult.sky(level0, c0, "rpg:ult_fenrir", slamAt + 30);
        RpgCombat.setSteadfast(sp, slamAt + 4);
        LivingEntity target = e;
        boolean carry = target != null && JobKit.pushable(target) && target.getBbHeight() <= 2.6;
        Vec3 dir;
        Vec3 grab;
        if (target != null) {
            Vec3 back = V.flat(V.sub(c0, target.position()));
            Vec3 u = V.len(back) > 0.1 ? back : V.scale(f0, -1);
            dir = V.scale(u, -1);
            Vec3 s = V.hdist(c0, target.position()) > 1.6 + Kit.bodyPad(target) ? JobTraits12.beyond(sp, target.position(), u, 1.0 + Kit.bodyPad(target)) : null;
            grab = s != null ? s : c0;
        } else {
            dir = f0;
            grab = c0;
        }
        // 내리꽂을 자리: 붙잡은 자리에서 앞 ahead 칸 (들지 못하면 그 적 앞)
        double can = carry ? RuneKit.dashDistance(sp, dir, k.num("ahead", 3), 0.3, 1.5) : 0;
        Vec3 landFeet = V.add(grab, dir, can);
        Vec3 standLand = Kit.standSpot(level0, V.up(landFeet, 0.6), 3);
        Vec3 land = standLand != null ? standLand : grab;
        // 달려듦: 불씨 발자국 · 포효 (몸의 불꽃은 짧게 — 영상 1: 1초 불꽃이 달려 나간 뒤 처음 자리에 벽처럼 남았음)
        if (free && V.hdist(grab, c0) > 0.4) JobMove.run(sp, JobMove.to(0, grabAt, JobMove.at(grab), JobMove.OUT));
        JobFx.aura(sp, j, 3.0, 0.3, 1.0);
        JobFx.eyeRing(sp, j, 2.4, 1.1, 0.5);
        SkillFx.play(level0, "entity.warden.roar", c0, 0.6, 0.9);
        SkillFx.play(level0, "entity.ravager.roar", c0, 0.6, 0.8);
        if (target != null) JobFx.target(level0, RuneKit.overHead(target, 0.6), 0.55 + Kit.bodyPad(target) * 0.2, j, V.scale(dir, -1), 0, grabAt / 20.0 + 0.1, Opt.of("vel",
                RuneKit.entVel(target)));
        Vec3[] heldAt = {null};
        // 붙잡음 → 뛰어오름
        RuneKit.later(grabAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            if (target != null && target.isAlive() && !target.isRemoved()) {
                RuneKit.effect(target, "slowness", slamAt - grabAt + 6, JobKit.isBoss(target) ? 1 : 10);
                if (carry) JobTraits4.stun(target, j, slamAt - grabAt + 2);
                JobFx.burst(lv, Kit.bodyPoint(target), 1.3 + Kit.bodyPad(target) * 0.4, EMBER, EMBER_CORE, 0.3, Opt.of("vel", RuneKit.entVel(target)));
                SkillFx.play(lv, "entity.iron_golem.attack", target.position(), 0.6, 1.0);
            }
            if (free) {
                Vec3 to = carry ? land : (target != null ? c : land);
                double peak = JobMove.hopPeak(sp, c, to, height);
                JobMove.run(sp, JobMove.hop(0, slamAt - grabAt, JobMove.at(to), peak));
            }
            JobFx.speed(sp, j, (slamAt - grabAt) / 20.0, 0.7);
            SkillFx.play(lv, "entity.player.attack.sweep", c, 0.5, 0.9);
        });
        // 끌어안고 감 (들 수 있는 적): 내 앞 위에 붙임 · 불씨 꼬리
        double[] lastY = {Double.NaN};
        for (int t = grabAt + 1; t < slamAt; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 p = sp.position();
                double vy = Double.isNaN(lastY[0]) ? 0 : p.y - lastY[0];
                lastY[0] = p.y;
                if (carry && target != null && target.isAlive() && !target.isRemoved()) {
                    // 클라이언트의 몹 자리는 서버보다 2 ~ 3틱 늦게 따라옴 (보간) — 오를 때는 위로 · 내릴 때는 아래로 앞서 둠 (영상 1: 내려오는 동안 적이 내 머리 위에 떠 보였음)
                    double lead = Math.max(-1.0, Math.min(1.0, vy * 2.5));
                    Vec3 hold = V.add(p, dir, 0.75 + target.getBbWidth() * 0.5);
                    Vec3 want = V.up(hold, lead); // 내 발 높이 (영상 2: 0.35칸 위면 적의 머리가 내 머리 위로 솟아 어깨에 얹힌 것처럼 보였음)
                    if (Kit.moveTo(target, want)) heldAt[0] = want;
                    else if (Kit.moveTo(target, V.up(hold, 0.35))) heldAt[0] = V.up(hold, 0.35);
                    target.resetFallDistance();
                }
                if (tt % 2 == 0) {
                    SkillFx.fx(lv, "rpg:arrow_spark", V.up(p, 0.6), Opt.of("dir", V.DOWN, "speed", 1.6, "size", 0.16, "grow", 0.8, "life", 0.45, "color", EMBER, "alpha",
                            0.95));
                    JobFx.smoke(lv, V.up(p, 0.9), 0.6, ASH, ASH_CORE, 0.5, JobFx.hide1p(sp).with("alpha", 0.45));
                }
            });
        }
        // 떨어지기 전에: 내리꽂을 땅 위에 늑대의 턱이 벌어짐
        RuneKit.later(Math.max(grabAt + 1, slamAt - 8), () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 g = ThunderlordSkills.ground(sp.level(), carry ? land : target != null ? target.position() : land);
            JobFx15.jaw(sp.level(), V.up(g, 1.4), 1.6, 0, 3, 0.42, Opt.of("alpha", 0.95), EMBER, EMBER_CORE);
            SkillFx.play(sp.level(), "entity.polar_bear.warning", g, 0.5, 1.0);
        });
        JobKit.motionLater(sp, "avenger_ult:slam", slamAt - 4, life);
        RuneKit.later(slamAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 g;
            if (target != null && target.isAlive() && !target.isRemoved()) {
                if (carry && heldAt[0] != null) {
                    Vec3 put = ThunderlordSkills.ground(lv, V.add(c, dir, 0.9 + target.getBbWidth() * 0.5));
                    if (!Kit.moveTo(target, put)) Kit.moveTo(target, ThunderlordSkills.ground(lv, target.position()));
                    target.resetFallDistance();
                }
                g = ThunderlordSkills.ground(lv, target.position());
            } else g = ThunderlordSkills.ground(lv, V.add(c, dir, 1.0));
            double r = k.num("radius", 3);
            double v = JobTraits15.vengeanceTake(sp, target != null ? 1.0 : 0);
            // 내리꽂음: 늑대의 턱이 찢어짐 · 불씨 폭발 · 땅의 금 · 충격 고리 · 재 연기
            JobFx15.jaw(lv, V.up(g, 1.4), 1.8, 3, 5, 0.6, Opt.of("alpha", 1.0), EMBER, EMBER_CORE);
            JobFx.smash(lv, V.up(g, 0.05), r * 1.3, j, 0.6, Opt.empty());
            JobFx.burst(lv, V.up(g, 1.0), 3.0, EMBER, EMBER_CORE, 0.4, Opt.empty());
            JobFx15.cinder(lv, g, 3.4, 0, 8, 0.9, false, Opt.of("alpha", 0.9), EMBER, EMBER_CORE);
            RuneKit.novaRing(lv, g, 1.0, r * 2.4 - 1, EMBER, EMBER_CORE, Opt.of("life", 0.5));
            RuneKit.glowCrack(lv, V.up(g, 0.04), r * 1.5, EMBER, EMBER_CORE, Opt.of("life", 1.1));
            JobFx.smoke(lv, V.up(g, 0.8), r * 0.8, ASH, ASH_CORE, 1.2, Opt.of("alpha", 0.55, "dir", V.UP, "speed", 0.7));
            JobFx.slash(sp, j, 2.0, 0.6, 3.2, 270.0, 0.3);
            Ult.flash(lv, g, EMBER, Opt.of("radius", 7.0, "time", new double[] {0.02, 0.06, 0.4}), sp);
            SkillFx.sparks(lv, V.up(g, 0.5), EMBER_CORE, 20, 8);
            SkillFx.play(lv, "item.mace.smash_ground_heavy", g, 0.7, 1.0);
            SkillFx.play(lv, "entity.generic.explode", g, 0.8, 1.0);
            SkillFx.play(lv, "entity.ravager.hurt", g, 0.5, 1.0);
            double mult = k.num("slam", 32);
            if (target != null && target.isAlive() && !target.isRemoved() && target.distanceTo(sp) <= 4.5 + Kit.bodyPad(target)) {
                double hp = Math.min(target.getMaxHealth() * k.num("maxHp", 0.08), P * k.num("maxHpCap", 8));
                double flat = v + hp;
                JobKit.strikeFlat(sp, target, j, P, mult, flat, false, true, life, 0, d -> {
                    JobTraits4.stun(target, j, (int) k.num("stun", 20));
                    JobImpact.hit(sp, target, j, 2.6);
                    vengeanceFx(sp, target, flat, P);
                });
            }
            double splash = k.num("splash", 0.5);
            for (LivingEntity x : Kit.around(sp, g, r, 3)) {
                if (x == target || !x.isAlive() || x.isRemoved()) continue;
                JobKit.strikeFlat(sp, x, j, P, mult * splash, v * splash, false, false, life, 0, d -> {
                    JobImpact.hit(sp, x, j, 1.6);
                    if (JobKit.pushable(x)) RuneKit.knock(x, V.flat(V.sub(x.position(), g)), 0.8, 0.35);
                });
            }
            if (v >= 1) JobSystem.note(sp, "§6앙갚음 §7+" + Math.round(v));
        });
        return true;
    }
}
