package mmorpg.fabric.job;

import java.util.ArrayList;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-06 사제 반전 워록 (재설계안 2판 — 사용자 "다음 작업", jobs.json advance warlock — 핏빛 · 마녀의 금빛, 특성 「생명 착취」 JobTraits14): 아군에게 체력을 주는
 * 힐러를 뒤집어 팀의 체력을 빼앗아 쓰는 원거리 딜러 — 생귄 랜스 · 크림슨 마이어 · 블러드 노바 · 애시본 · 블러드 뱅큇. 몸 쓰는 직업이 아니라 핏빛 창 · 웅덩이 · 심장 · 불기둥은
 * 저절로 맺히고 터짐 (그림), 화면은 흔들지 않음. 피해 = 위력 P × 배율 (마법 — P 에 생명 착취 몫이 들어 있음).
 */
final class WarlockSkills {
    private WarlockSkills() {}

    static final double[] BLOOD = JobTraits14.BLOOD;
    static final double[] BLOOD_CORE = JobTraits14.BLOOD_CORE;
    static final double[] WGOLD = JobTraits14.WGOLD;
    static final double[] WGOLD_CORE = JobTraits14.WGOLD_CORE;
    static final double[] EMBER = JobTraits14.EMBER;
    static final double[] EMBER_CORE = JobTraits14.EMBER_CORE;
    static final double[] ASHEN = JobTraits14.ASHEN;
    static final double[] ASHEN_CORE = JobTraits14.ASHEN_CORE;

    static void register() {
        JobSystem.register("warlock_lance", WarlockSkills::lance);
        JobSystem.register("warlock_mire", WarlockSkills::mire);
        JobSystem.register("warlock_nova", WarlockSkills::nova);
        JobSystem.register("warlock_ash", WarlockSkills::ash);
        JobSystem.register("warlock_ult", WarlockSkills::ult);
    }

    /** 홀 끝 (손 앞) */
    static Vec3 tip(ServerPlayer sp) {
        RuneKit.Hand h = RuneKit.handOf(sp);
        return V.up(V.add(h.hand(), h.f(), 0.45), 0.25);
    }

    /** 땅 (턱 하나) */
    static Vec3 ground(ServerLevel level, Vec3 p) {
        return ThunderlordSkills.ground(level, p);
    }

    // ------------------------------------------------------------ 생귄 랜스

    /**
     * 생귄 랜스 (롤 블라디미르 Q): 홀을 치켜들면 둘레의 핏방울 (생명 착취로 빼앗은 피) 이 홀 위로 모여 핏빛 창이 맺힘 (formAt 틱 — 과녁을 가리킴) → 날아가 hitAt 틱 겨눈 적
     * (range 칸 — 없으면 앞 fallback 칸으로 날아가 흩어짐) 을 꿰뚫음 (lance — 마법) · 맞히면 이번에 빼앗은 체력 × return 을 빼앗긴 사람마다 되돌림 (JobTraits14)
     */
    static boolean lance(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int formAt = Math.max(2, (int) k.num("formAt", 8));
        int hitAt = Math.max(formAt + 1, (int) k.num("hitAt", 12));
        LivingEntity e = Kit.pickTarget(sp, k.num("range", 16), 12);
        double fallback = k.num("fallback", 12);
        java.util.function.Supplier<Vec3> hold = () -> V.up(V.add(tip(sp), Kit.facingOf(sp), 0.25), 0.75);
        java.util.function.Supplier<Vec3> aim = () -> e != null && e.isAlive() && !e.isRemoved() ? Kit.bodyPoint(e) : V.add(sp.getEyePosition(), sp.getViewVector(1F),
                fallback);
        JobFx.charge(sp, j, new Vec3(0.35, 1.9, 0.3), formAt, 0.6);
        SkillFx.play(level0, "entity.warden.heartbeat", sp.position(), 1.4, 0.8);
        SkillFx.play(level0, "block.bubble_column.upwards_inside", sp.position(), 1.2, 0.5);
        for (int t = 0; t < formAt; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 at = hold.get();
                for (int i = 0; i < 3; i++) {
                    double a = Kit.rand() * JobTraits14.TAU;
                    Vec3 s = V.add(at, new Vec3(Math.cos(a) * 1.4, -0.6 - Kit.rand() * 1.2, Math.sin(a) * 1.4));
                    JobFx.soulFly(lv, s, at, 9 + Kit.rand() * 4, 0.2, BLOOD, BLOOD_CORE, Opt.empty());
                }
                if (tt == 2) {
                    Vec3 u = V.unit(V.sub(aim.get(), at));
                    JobFx14.lanceHold(lv, at, u, 0.95, (formAt - 2) / 20.0, Opt.of("alpha", 0.95, "vel", RuneKit.bodyVel(sp)), BLOOD, BLOOD_CORE);
                    JobFx14.heart(lv, at, 0.35, 0, 4, (formAt - 2) / 20.0, false, Opt.of("vel", RuneKit.bodyVel(sp)), BLOOD, BLOOD_CORE);
                }
            });
        }
        RuneKit.later(formAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 from = hold.get();
            Vec3 to = aim.get();
            JobFx14.lance(lv, from, to, hitAt - formAt, 1.15, Opt.empty(), BLOOD, BLOOD_CORE); // 영상 1: 0.8 은 가는 붉은 줄로만 보였음
            JobFx.line(lv, from, to, WGOLD, WGOLD_CORE, 0.25, Opt.of("alpha", 0.5));
            JobFx.burst(lv, from, 0.6, BLOOD, BLOOD_CORE, 0.2, JobFx.hide1p(sp));
            SkillFx.play(lv, "item.trident.throw", sp.position(), 0.8, 1.0);
            SkillFx.play(lv, "entity.player.attack.strong", sp.position(), 0.7, 0.6);
        });
        RuneKit.later(hitAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            if (e == null || !e.isAlive() || e.isRemoved()) {
                Vec3 at = aim.get();
                JobFx14.heart(lv, at, 0.5, 6, 2, 0.35, false, Opt.empty(), BLOOD, BLOOD_CORE);
                SkillFx.play(lv, "entity.generic.splash", at, 1.4, 0.5);
                return;
            }
            JobKit.strike(sp, e, j, P, k.num("lance", 3.0), true, false, life, 0, d -> {
                JobImpact.hit(sp, e, j, 1.2);
                if (e.level() instanceof ServerLevel el) {
                    Vec3 b = Kit.bodyPoint(e);
                    JobFx14.geyser(el, ground(el, e.position()), 1.6 + Kit.bodyPad(e), 0, 7, 0.55, Opt.of("vel", RuneKit.entVel(e)), BLOOD, BLOOD_CORE);
                    JobFx14.heart(el, b, 0.55 + Kit.bodyPad(e) * 0.3, 6, 2, 0.35, false, Opt.of("vel", RuneKit.entVel(e)), BLOOD, BLOOD_CORE);
                    JobFx.burst(el, b, 0.9 + Kit.bodyPad(e) * 0.4, BLOOD, BLOOD_CORE, 0.3, Opt.of("vel", RuneKit.entVel(e)));
                    SkillFx.sparks(el, b, BLOOD_CORE, 10, 4.0);
                    SkillFx.play(el, "item.trident.hit", b, 0.8, 1.0);
                    SkillFx.play(el, "entity.player.hurt_sweet_berry_bush", b, 0.7, 0.8);
                    JobTraits14.giveBack(sp, b, JobUpgrades.num(sp, k, "return", 0.5));
                }
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 크림슨 마이어

    /**
     * 크림슨 마이어 (롤 블라디미르 W): 몸이 핏물 웅덩이가 됨 (dur 틱 — 받는 피해 없음 · 2틱 뒤 보이지 않음: 동작이 먼저 몸 · 홀을 숨김) → travel 틱 동안 겨눈 적 앞 · 조준한
     * 땅 (range 칸 — 없으면 앞 fallback 칸) 으로 흘러감 → 그 뒤 움직이는 대로 따라감 (이동 속도 + speed · 강화) · first 틱부터 every 틱마다 pulses 번 웅덩이 둘레 radius 칸 적
     * pulse (마법) · 둔화 → dur 틱 핏물 기둥과 함께 솟아오름
     */
    static boolean mire(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int dur = Math.max(12, (int) k.num("dur", 40));
        int travel = Math.max(1, Math.min(dur - 2, (int) k.num("travel", 10)));
        int first = Math.max(1, (int) k.num("first", 10));
        int every = Math.max(10, (int) k.num("every", 10));
        int pulses = Math.max(1, (int) k.num("pulses", 4));
        double radius = k.num("radius", 2.2);
        double range = k.num("range", 10);
        Vec3 start = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        Vec3 dest = null;
        LivingEntity foe = Kit.pickTarget(sp, range, 20);
        if (foe != null) {
            Vec3 back = V.flat(V.sub(start, foe.position()));
            Vec3 u = V.len(back) > 0.1 ? V.unit(back) : V.scale(f0, -1);
            dest = JobTraits12.beyond(sp, foe.position(), u, 0.9 + Kit.bodyPad(foe));
        }
        if (dest == null) {
            Vec3 aim = RuneKit.groundAim(sp, range);
            if (aim != null && V.hdist(aim, start) > 1.0) dest = Kit.standSpot(level0, V.up(aim, 0.6), 2.5);
        }
        if (dest == null) {
            double can = RuneKit.dashDistance(sp, f0, k.num("fallback", 6), 0.3, 1.0);
            if (can >= 1.0) dest = Kit.standSpot(level0, V.up(V.add(start, f0, can), 0.6), 2.5);
        }
        Vec3 land = dest != null ? dest : start;
        JobTraits13.guardFor(sp, dur + 2);
        RpgCombat.setSteadfast(sp, dur + 2);
        Kit.noFall(sp, dur + 20);
        // 무너져 내림: 발밑으로 핏물이 튀며 웅덩이
        JobFx14.pool(level0, V.up(start, 0.05), 1.5, 0, 2, 0.3, false, Opt.of("alpha", 0.95), BLOOD, BLOOD_CORE);
        JobFx14.geyser(level0, start, 2.0, 4, 3, 0.35, JobFx.hide1p(sp), BLOOD, BLOOD_CORE);
        SkillFx.sparks(level0, V.up(start, 0.6), BLOOD_CORE, 12, 3.5);
        SkillFx.play(level0, "entity.generic.splash", start, 0.8, 0.9);
        SkillFx.play(level0, "block.bubble_column.whirlpool_inside", start, 1.0, 0.6);
        RuneKit.later(2, () -> {
            if (RuneKit.sameLife(sp, life)) sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, dur - 1, 0, false, false, false));
        });
        for (int t = 1; t <= travel; t++) {
            double s = t / (double) travel;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Kit.moveTo(sp, V.lerp(start, land, s));
                sp.resetFallDistance();
            });
        }
        RuneKit.later(travel, () -> {
            if (RuneKit.sameLife(sp, life)) JobTraits14.mireSpeed(sp, JobUpgrades.num(sp, k, "speed", 0.3));
        });
        // 웅덩이 (3틱마다 — 몸을 따라감): 물결 · 거품 · 튀는 방울
        for (int t = 0; t <= dur; t += 3) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 g = V.up(ground(lv, sp.position()), 0.05);
                JobFx14.pool(lv, g, 1.6, 2, 4, 0.3, true, Opt.of("alpha", 0.95), BLOOD, BLOOD_CORE);
                for (int b = 0; b < 2; b++) {
                    Vec3 p = V.add(g, new Vec3((Kit.rand() - 0.5) * 2.2, 0.05, (Kit.rand() - 0.5) * 2.2));
                    SkillFx.fx(lv, "rpg:arrow_spark", p, Opt.of("dir", V.UP, "speed", 1.2, "size", 0.12, "grow", 0.3, "life", 0.4, "color", b == 0 ? BLOOD_CORE : WGOLD,
                            "alpha", 0.9));
                }
            });
        }
        for (int i = 0; i < pulses; i++) {
            int at = first + i * every;
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 g = ground(lv, sp.position());
                JobFx14.pool(lv, V.up(g, 0.07), radius, 5, 1, 0.25, false, Opt.of("alpha", 0.6), WGOLD, WGOLD_CORE);
                SkillFx.play(lv, "block.bubble_column.bubble_pop", g, 0.8, 0.8);
                for (LivingEntity e : Kit.around(sp, g, radius, 2.5)) {
                    if (!e.isAlive() || e.isRemoved()) continue;
                    JobKit.strike(sp, e, j, P, k.num("pulse", 0.45), true, false, life, 0, d -> {
                        RuneKit.effect(e, "slowness", every + 6, (int) k.num("slow", 1));
                        if (e.level() instanceof ServerLevel el) {
                            JobFx14.geyser(el, ground(el, e.position()), 1.1 + Kit.bodyPad(e) * 0.5, 0, 4, 0.3, Opt.of("vel", RuneKit.entVel(e)), BLOOD, BLOOD_CORE);
                            SkillFx.sparks(el, Kit.bodyPoint(e), BLOOD_CORE, 5, 2.5);
                        }
                    });
                }
            });
        }
        JobKit.motionLater(sp, "warlock_mire:rise", dur - 3, life);
        RuneKit.later(dur, () -> {
            JobTraits14.mireSpeed(sp, 0);
            if (!RuneKit.sameLife(sp, life)) return;
            MobEffectInstance inv = sp.getEffect(MobEffects.INVISIBILITY);
            if (inv != null && !inv.isInfiniteDuration() && inv.getDuration() <= dur + 2) sp.removeEffect(MobEffects.INVISIBILITY);
            ServerLevel lv = sp.level();
            Vec3 g = ground(lv, sp.position());
            JobFx14.geyser(lv, g, 3.2, 0, 7, 0.7, JobFx.hide1p(sp), BLOOD, BLOOD_CORE);
            JobFx14.pool(lv, V.up(g, 0.05), 1.8, 6, 2, 0.4, false, Opt.of("alpha", 0.9), BLOOD, BLOOD_CORE);
            JobFx.burst(lv, V.up(g, 1.0), 1.2, BLOOD, BLOOD_CORE, 0.3, JobFx.hide1p(sp));
            SkillFx.sparks(lv, V.up(g, 1.0), BLOOD_CORE, 16, 5.0);
            SkillFx.play(lv, "entity.generic.splash", g, 0.6, 1.0);
            SkillFx.play(lv, "block.bubble_column.upwards_inside", g, 0.8, 0.8);
        });
        return true;
    }

    // ------------------------------------------------------------ 블러드 노바

    /**
     * 블러드 노바 (롤 블라디미르 E): 홀을 머리 위로 들면 머리 위에 피의 심장이 맺히고 발밑에 핏빛 원 (radius 칸 · 강화) → first 틱부터 every 틱마다 drains 번 둘레 적의 피를
     * 빨아들임 (drain — 마법 · 핏줄이 심장으로 · 심장이 자라며 고동침) → burstAt 틱 심장이 터짐: 피의 고리 · 핏물 기둥 · 둘레 적 burst (마법) · 밀쳐냄
     */
    static boolean nova(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int drains = Math.max(0, (int) k.num("drains", 3));
        int first = Math.max(2, (int) k.num("first", 10));
        int every = Math.max(10, (int) k.num("every", 10));
        int burstAt = Math.max(first + Math.max(0, drains - 1) * every + 10, (int) k.num("burstAt", 40));
        double r = JobUpgrades.num(sp, k, "radius", 8);
        java.util.function.Supplier<Vec3> orb = () -> V.up(sp.position(), sp.getBbHeight() + 1.5);
        Vec3 look = sp.getViewVector(1F);
        Vec3 orb1p = V.add(V.add(sp.getEyePosition(), look, 2.0), JobFx.screenUp(look), 0.8);
        Vec3 g0 = ground(level0, sp.position());
        JobFx14.pool(level0, V.up(g0, 0.05), r, 0, 2, 0.4, false, Opt.of("alpha", 0.55, "coreAlpha", 0.5), BLOOD, BLOOD_CORE);
        RuneKit.later(8, () -> {
            // 터지는 틱보다 4틱 더 · 끝 8% 만 흐려짐 (영상 1: 20% 가 흐려져 터지기 전에 웅덩이가 사라졌음)
            if (RuneKit.sameLife(sp, life)) JobFx14.pool(sp.level(), V.up(g0, 0.05), r, 2, 4, (burstAt - 8 + 4) / 20.0, true, Opt.of("alpha", 0.5, "coreAlpha", 0.45,
                    "fade", 0.08), BLOOD, BLOOD_CORE);
        });
        JobFx14.heart(level0, orb.get(), 0.7, 0, 1, 0.3, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), BLOOD, BLOOD_CORE);
        JobFx14.heart(level0, orb1p, 0.3, 0, 1, 0.3, false, JobFx.only1p(sp), BLOOD, BLOOD_CORE);
        JobFx.aura(sp, j, 2.6, burstAt / 20.0, 0.8);
        SkillFx.play(level0, "entity.warden.heartbeat", sp.position(), 1.0, 1.0);
        SkillFx.play(level0, "block.bubble_column.whirlpool_inside", sp.position(), 0.7, 0.6);
        for (int t = 6; t < burstAt; t += every) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                double size = 0.8 + 0.35 * Math.min(3, (tt - 6) / every + 1);
                JobFx14.heart(lv, orb.get(), size, 1, 4, every / 20.0 + 0.05, true, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), BLOOD, BLOOD_CORE);
                JobFx14.heart(lv, orb1p, 0.3 + 0.06 * Math.min(3, (tt - 6) / every + 1), 1, 4, every / 20.0 + 0.05, true, JobFx.only1p(sp), BLOOD, BLOOD_CORE);
            });
        }
        for (int i = 0; i < drains; i++) {
            RuneKit.later(first + i * every, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 o = orb.get();
                SkillFx.play(lv, "entity.warden.heartbeat", sp.position(), 1.2, 1.0);
                int[] cnt = {0};
                for (LivingEntity e : Kit.around(sp, sp.position(), r, 4)) {
                    if (!e.isAlive() || e.isRemoved()) continue;
                    JobKit.strike(sp, e, j, P, k.num("drain", 0.6), true, false, life, 0, d -> {
                        if (cnt[0]++ < 8 && e.level() instanceof ServerLevel el) {
                            Vec3 b = Kit.bodyPoint(e);
                            JobFx.beam(el, b, o, BLOOD, BLOOD_CORE, 2.0, 0.3, Opt.of("alpha", 0.7));
                            for (int q = 0; q < 3; q++) JobFx.soulFly(el, V.add(b, new Vec3((Kit.rand() - 0.5) * 0.5, (Kit.rand() - 0.5) * 0.6, (Kit.rand() - 0.5) * 0.5)), o,
                                    10 + q * 2, 0.22, BLOOD, BLOOD_CORE, Opt.empty());
                            JobFx.burst(el, b, 0.6 + Kit.bodyPad(e) * 0.3, BLOOD, BLOOD_CORE, 0.25, Opt.of("vel", RuneKit.entVel(e)));
                        }
                    });
                }
            });
        }
        JobKit.motionLater(sp, "warlock_nova:burst", burstAt - 4, life);
        RuneKit.later(burstAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 g = ground(lv, c);
            JobFx14.heart(lv, orb.get(), 2.0, 5, 3, 0.45, false, JobFx.hide1p(sp), BLOOD, BLOOD_CORE);
            JobFx14.heart(lv, orb1p, 0.5, 5, 3, 0.4, false, JobFx.only1p(sp), BLOOD, BLOOD_CORE);
            RuneKit.novaRing(lv, g, 0.8, r / 0.8 - 1, BLOOD, BLOOD_CORE, Opt.of("life", 0.5));
            RuneKit.novaRing(lv, V.up(g, 0.2), 0.6, r * 0.75 / 0.6 - 1, WGOLD, WGOLD_CORE, Opt.of("life", 0.4));
            JobFx14.pool(lv, V.up(g, 0.06), r, 6, 2, 0.5, false, Opt.of("alpha", 0.8), BLOOD, BLOOD_CORE);
            Ult.flash(lv, c, BLOOD_CORE, Opt.of("radius", 5.0, "time", new double[] {0.02, 0.05, 0.25}), sp);
            // 핏물 기둥 아홉: 앞 220° (뒤 — 3인칭 카메라 쪽은 비움) · 4.5칸 안 (영상 1: 6칸 기둥이 영상 카메라 바로 앞에서 화면을 덮었음)
            Vec3 fw = Kit.facingOf(sp);
            double base = Math.atan2(fw.z, fw.x);
            for (int i = 0; i < 9; i++) {
                double a = base + Math.toRadians(-110 + i * 27.5);
                double rr = Math.min(4.5, r * (i % 2 == 0 ? 0.35 : 0.55));
                Vec3 p = ground(lv, V.add(c, new Vec3(Math.cos(a), 0, Math.sin(a)), rr));
                int delay = i % 3;
                RuneKit.later(delay, () -> JobFx14.geyser(lv, p, 2.2 + Kit.rand() * 0.8, 0, 7, 0.6, Opt.empty(), BLOOD, BLOOD_CORE));
            }
            SkillFx.sparks(lv, orb.get(), BLOOD_CORE, 24, 7.0);
            SkillFx.play(lv, "entity.generic.explode", c, 0.8, 0.7);
            SkillFx.play(lv, "entity.generic.splash", c, 0.6, 1.0);
            SkillFx.play(lv, "entity.warden.sonic_boom", c, 1.6, 0.35);
            int[] cnt = {0};
            for (LivingEntity e : Kit.around(sp, c, r, 4)) {
                if (!e.isAlive() || e.isRemoved()) continue;
                JobKit.strike(sp, e, j, P, k.num("burst", 3.8), true, false, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 1.3);
                    if (JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), c), k.num("knock", 0.6), 0.3);
                    if (cnt[0]++ < 6 && e.level() instanceof ServerLevel el) JobFx.burst(el, Kit.bodyPoint(e), 1.0 + Kit.bodyPad(e) * 0.4, BLOOD, BLOOD_CORE, 0.3,
                            Opt.of("vel", RuneKit.entVel(e)));
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 애시본

    /**
     * 애시본 (롤 스웨인 R): dur 틱 태세 (강화로 늘어남) — 세 번 불타고 되살아남: 핏빛 불기둥이 세 번 몸을 삼킴 (0 · 6 · 12 틱 — 갈수록 크게) 뒤 타오르는 몸 (JobTraits14 가
     * 8틱마다 발밑 불길 · 도는 불티): 생명 착취가 아군 대신 내 체력만 씀 (self — 체력 1 에서 멈춤) · 직업 스킬 위력 + power
     */
    static boolean ash(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) JobUpgrades.num(sp, k, "dur", 200);
        JobTraits.startStance(sp, k, dur);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        double[] heights = {2.4, 3.2, 4.0};
        for (int i = 0; i < 3; i++) {
            int ii = i;
            RuneKit.later(i * 6, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 g = ground(lv, sp.position());
                JobFx14.pyre(lv, g, heights[ii], 0, 7, 0.75, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), BLOOD, BLOOD_CORE);
                JobFx14.pyre(lv, g, heights[ii] * 0.75, 1, 6, 0.6, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.8), WGOLD, WGOLD_CORE);
                RuneKit.novaRing(lv, g, 0.5, 2.0 + ii, EMBER, EMBER_CORE, Opt.of("life", 0.35));
                SkillFx.sparks(lv, V.up(g, 1.0), EMBER_CORE, 10 + ii * 6, 4.0 + ii);
                JobFx.eyeRing(sp, j, 2.4, 0.8 + ii * 0.15, 0.35);
                SkillFx.play(lv, "item.firecharge.use", g, 0.8 + ii * 0.15, 0.9);
                SkillFx.play(lv, "entity.blaze.shoot", g, 0.6 + ii * 0.1, 0.6);
                if (ii == 2) {
                    Ult.flash(lv, g, WGOLD, Opt.of("radius", 4.0, "time", new double[] {0.03, 0.05, 0.3}), sp);
                    JobFx.smoke(lv, V.up(g, 2.4), 1.6, ASHEN, ASHEN_CORE, 1.0, JobFx.hide1p(sp).with("dir", V.UP, "speed", 1.0, "alpha", 0.6));
                    SkillFx.play(lv, "entity.wither.spawn", g, 1.8, 0.25);
                }
            });
        }
        JobFx.aura(sp, j, 2.8, 1.0, 0.9);
        Notify.notify(sp, "§6§l" + k.name() + " §7— 내 체력만 씀 · 위력 +" + Math.round(k.num("power", 0.4) * 100) + "% " + dur / 20 + "초", 40);
        return true;
    }

    // ------------------------------------------------------------ 블러드 뱅큇

    /**
     * 블러드 뱅큇 (롤 블라디미르 R): 피의 잔치 (rpg:ult_feast) — 겨눈 곳 (range 칸 — 겨눈 적 · 조준한 땅 · 없으면 앞 fallback 칸) 둘레 radius 칸에 핏물이 번지고 first
     * 틱 그 안의 적들에게 피의 역병 (plague 틱 받는 피해 + vuln · 머리 위 피의 심장) → first 틱부터 every 틱마다 beats 번 심장이 고동침 (beat — 마법 · 새로 들어온 적도 역병)
     * → feastAt 틱 둘레 feastRadius 칸 아군 (나 빼고) 의 체력 feast 씩 빼앗아 (floor 아래는 빼지 않음) 가운데 큰 심장으로 → burstAt 틱 모든 심장이 한꺼번에 터짐 (burst ×
     * (1 + 빼앗은 아군 × feastUp · 강화) — 큰 숫자 · 핏물 기둥)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int beats = Math.max(1, (int) k.num("beats", 3));
        int first = Math.max(4, (int) k.num("first", 10));
        int every = Math.max(10, (int) k.num("every", 20));
        int burstAt = Math.max(first + (beats - 1) * every + 10, (int) k.num("burstAt", 70));
        int feastAt = Math.max(first + 1, Math.min(burstAt - 4, (int) k.num("feastAt", 62)));
        double r = k.num("radius", 5);
        double range = k.num("range", 14);
        Vec3 c0 = sp.position();
        Vec3 center = null;
        LivingEntity aimed = Kit.pickTarget(sp, range, 14);
        if (aimed != null) center = ground(level0, aimed.position());
        if (center == null) {
            Vec3 aim = RuneKit.groundAim(sp, range);
            if (aim != null) center = ground(level0, aim);
        }
        if (center == null) center = ground(level0, V.add(c0, Kit.facingOf(sp), k.num("fallback", 6)));
        Vec3 mid = center;
        Vec3 bigHeart = V.up(mid, 3.4);
        Ult.sky(level0, c0, "rpg:ult_feast", burstAt + 30);
        JobFx14.pool(level0, V.up(mid, 0.05), r, 0, 2, 0.5, false, Opt.of("alpha", 0.95), BLOOD, BLOOD_CORE);
        RuneKit.later(10, () -> {
            // 터지는 틱보다 4틱 더 · 끝 8% 만 흐려짐 (영상 1: 터지기 전에 웅덩이가 사라졌음)
            if (RuneKit.sameLife(sp, life)) JobFx14.pool(sp.level(), V.up(mid, 0.05), r, 2, 4, (burstAt - 10 + 4) / 20.0, true, Opt.of("alpha", 0.9, "fade", 0.08), BLOOD,
                    BLOOD_CORE);
        });
        JobFx.smoke(level0, V.up(mid, 0.8), r * 0.6, BLOOD, BLOOD_CORE, 1.4, Opt.of("alpha", 0.45, "dir", V.UP, "speed", 0.6));
        JobFx.line(level0, tip(sp), V.up(mid, 0.5), BLOOD, BLOOD_CORE, 0.35, Opt.of("alpha", 0.8));
        JobFx.aura(sp, j, 3.0, 1.0, 1.0);
        JobFx.eyeRing(sp, j, 2.4, 1.1, 0.5);
        SkillFx.play(level0, "entity.warden.heartbeat", c0, 0.8, 1.0);
        SkillFx.play(level0, "entity.wither.ambient", mid, 0.6, 0.5);
        List<LivingEntity> marked = new ArrayList<>();
        int plague = (int) k.num("plague", 72);
        double vuln = k.num("vuln", 0.1);
        for (int i = 0; i < beats; i++) {
            int ii = i;
            RuneKit.later(first + i * every, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                SkillFx.play(lv, "entity.warden.heartbeat", mid, 0.9 - ii * 0.05, 1.0);
                JobFx14.pool(lv, V.up(mid, 0.07), r, 5, 1, 0.3, false, Opt.of("alpha", 0.6), WGOLD, WGOLD_CORE);
                int left = burstAt - (first + ii * every);
                for (LivingEntity e : Kit.around(sp, mid, r, 4)) {
                    if (!e.isAlive() || e.isRemoved()) continue;
                    if (!marked.contains(e)) {
                        marked.add(e);
                        JobTraits14.plague(e, Math.max(left + 4, plague - ii * every), vuln);
                    }
                    if (e.level() instanceof ServerLevel el) JobFx14.heart(el, RuneKit.overHead(e, 0.7), 0.42 + Kit.bodyPad(e) * 0.2, 1, 4, Math.min(every, left) / 20.0,
                            true, Opt.of("vel", RuneKit.entVel(e)), BLOOD, BLOOD_CORE);
                    JobKit.strike(sp, e, j, P, k.num("beat", 2), true, false, life, 0, d -> {
                        if (e.level() instanceof ServerLevel el) {
                            JobFx.burst(el, Kit.bodyPoint(e), 0.6 + Kit.bodyPad(e) * 0.3, BLOOD, BLOOD_CORE, 0.25, Opt.of("vel", RuneKit.entVel(e)));
                            JobFx.beam(el, Kit.bodyPoint(e), V.up(mid, 0.3), BLOOD, BLOOD_CORE, 2.0, 0.25, Opt.of("alpha", 0.6));
                        }
                    });
                }
            });
        }
        int[] fed = {0};
        RuneKit.later(feastAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            fed[0] = JobTraits14.feast(sp, j, bigHeart, k.num("feastRadius", 10), k.num("feast", 0.15));
            JobFx14.heart(lv, bigHeart, 2.0 + fed[0] * 0.3, 0, 5, (burstAt - feastAt) / 20.0, false, Opt.of("alpha", 1.0), BLOOD, BLOOD_CORE); // 영상 1: 1.4 는 과녁만 했음
            JobFx.line(lv, tip(sp), bigHeart, BLOOD, BLOOD_CORE, 0.3, Opt.of("alpha", 0.8));
            SkillFx.play(lv, "entity.warden.heartbeat", mid, 0.6, 1.0);
            if (fed[0] > 0) JobSystem.note(sp, "§4블러드 뱅큇 §7— 아군 " + fed[0] + " · 터짐 +" + Math.round(fed[0] * JobUpgrades.num(sp, k, "feastUp", 0.15) * 100) + "%");
        });
        JobKit.motionLater(sp, "warlock_ult:burst", burstAt - 4, life);
        RuneKit.later(burstAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            double mult = k.num("burst", 18) * (1 + fed[0] * JobUpgrades.num(sp, k, "feastUp", 0.15));
            JobFx14.heart(lv, bigHeart, 3.2 + fed[0] * 0.35, 5, 3, 0.5, false, Opt.empty(), BLOOD, BLOOD_CORE);
            JobFx14.pool(lv, V.up(mid, 0.06), r, 6, 2, 0.6, false, Opt.of("alpha", 0.9), BLOOD, BLOOD_CORE);
            RuneKit.novaRing(lv, mid, 0.8, r * 1.4 / 0.8 - 1, BLOOD, BLOOD_CORE, Opt.of("life", 0.5));
            Ult.flash(lv, mid, BLOOD_CORE, Opt.empty(), sp);
            SkillFx.sparks(lv, bigHeart, BLOOD_CORE, 30, 8.0);
            SkillFx.play(lv, "entity.generic.explode", mid, 0.7, 1.0);
            SkillFx.play(lv, "entity.warden.sonic_boom", mid, 1.2, 0.5);
            SkillFx.play(lv, "entity.generic.splash", mid, 0.5, 1.0);
            for (int i = 0; i < 6; i++) {
                double a = i * JobTraits14.TAU / 6 + 0.3;
                Vec3 p = ground(lv, V.add(mid, new Vec3(Math.cos(a), 0, Math.sin(a)), r * 0.6));
                JobFx14.geyser(lv, p, 3.0 + Kit.rand(), 0, 7, 0.7, Opt.empty(), BLOOD, BLOOD_CORE);
            }
            int[] cnt = {0};
            for (LivingEntity e : Kit.around(sp, mid, r, 4)) {
                if (!e.isAlive() || e.isRemoved()) continue;
                JobKit.strike(sp, e, j, P, mult, true, true, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 1.5);
                    if (cnt[0]++ < 8 && e.level() instanceof ServerLevel el) {
                        JobFx14.geyser(el, ground(el, e.position()), 3.6 + Kit.bodyPad(e), 0, 7, 0.7, Opt.of("vel", RuneKit.entVel(e)), BLOOD, BLOOD_CORE);
                        JobFx14.heart(el, RuneKit.overHead(e, 0.7), 0.7 + Kit.bodyPad(e) * 0.3, 6, 2, 0.4, false, Opt.of("vel", RuneKit.entVel(e)), BLOOD, BLOOD_CORE);
                        JobFx.burst(el, Kit.bodyPoint(e), 1.3 + Kit.bodyPad(e) * 0.5, BLOOD, BLOOD_CORE, 0.35, Opt.of("vel", RuneKit.entVel(e)));
                    }
                });
            }
        });
        return true;
    }
}
