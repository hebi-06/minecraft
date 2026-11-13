package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Jobs;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneMob;
import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 성기사 반전 타이런트 (재설계안 2판 — 2026-11-06 사용자 "다음 작업", jobs.json advance tyrant — 훔친 번개 보랏빛 · 빼앗은 힘 핏빛, 특성 「강탈」 — JobTraits13):
 * 1차의 아군에게 보호를 주는 광역 제어 탱커를 뒤집어 적의 힘을 빼앗아 홀로 커지는 단일 브루저 — 필리지 · 섀클 볼트 · 킹스 그립 · 콜로서스 · 스톨른 썬더. 몸 쓰는 직업이
 * 아니라 거인 왕의 손 · 번개 사슬 · 거대한 번개 망치는 저절로 (그림). 단타는 크게 감았다가 큰 타격, 화면은 흔들지 않음.
 */
final class TyrantSkills {
    private TyrantSkills() {}

    static final double[] VIOLET = JobTraits13.VIOLET;
    static final double[] VIOLET_CORE = JobTraits13.VIOLET_CORE;
    static final double[] CRIMSON = JobTraits13.CRIMSON;
    static final double[] CRIMSON_CORE = JobTraits13.CRIMSON_CORE;

    static void register() {
        JobSystem.register("tyrant_pillage", TyrantSkills::pillage);
        JobSystem.register("tyrant_shackle", TyrantSkills::shackle);
        JobSystem.register("tyrant_grip", TyrantSkills::grip);
        JobSystem.register("tyrant_colossus", TyrantSkills::colossus);
        JobSystem.register("tyrant_ult", TyrantSkills::ult);
    }

    /** 앞 reach 칸 · arc° 의 적 하나: 겨눈 적 → 가장 가까운 적 (없으면 null) */
    private static LivingEntity front(ServerPlayer sp, double reach, double arc) {
        List<LivingEntity> cone = RuneKit.coneTargets(sp, reach, arc, 10);
        LivingEntity aim = Kit.pickTarget(sp, reach + 0.5, arc / 2);
        if (aim != null && cone.contains(aim)) return aim;
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : cone) {
            double d = e.distanceToSqr(sp);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** 망치를 치켜든 자리 (머리 위 — 그림의 시작점) */
    private static Vec3 raised(ServerPlayer sp) {
        return V.up(sp.position(), sp.getBbHeight() + 0.9);
    }

    // ------------------------------------------------------------ 필리지

    /**
     * 필리지 (롤 트런들 Q): 망치를 머리 위로 높이 치켜들었다가 (보랏빛 번개가 망치로 모임) smashAt 틱에 앞 reach 칸 (arc°) 의 적 하나 (겨눈 적 — 없으면 가장 가까운 적)
     * 를 내려침 (smash · 큰 숫자) · 힘 steal 을 빼앗음. 적이 없으면 헛내려침 (땅이 갈라짐)
     */
    static boolean pillage(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int smashAt = Math.max(1, (int) k.num("smashAt", 8));
        double reach = k.num("reach", 4.5);
        double arc = k.num("arc", 60);
        RpgCombat.setSteadfast(sp, smashAt + 3);
        // 치켜듦: 머리 위 망치로 모이는 보랏빛 · 망치에 감기는 번개 · 발밑 보랏빛 고리
        JobFx.charge(sp, j, new Vec3(0.2, 2.75, 0.1), smashAt, 1.0);
        JobFx.halo(level0, V.up(sp.position(), 0.08), 1.7, j, smashAt / 20.0 + 0.15, Opt.of("alpha", 0.75));
        SkillFx.play(level0, "item.trident.thunder", sp.position(), 0.8, 0.35);
        SkillFx.play(level0, "item.trident.return", sp.position(), 0.6, 0.8);
        for (int t = 1; t < smashAt; t += 2) {
            RuneKit.later(t, () -> {
                if (RuneKit.sameLife(sp, life)) ThunderlordSkills.crackle(sp.level(), raised(sp), 0.9, 2, VIOLET, VIOLET_CORE, 0.12, JobFx.hide1p(sp).with("vel",
                        RuneKit.bodyVel(sp)));
            });
        }
        RuneKit.later(smashAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            LivingEntity e = front(sp, reach, arc);
            Vec3 g = e != null ? ThunderlordSkills.ground(lv, e.position()) : ThunderlordSkills.ground(lv, V.add(c, f, 2.4));
            // 내려침: 눈앞 세로 큰 베기 · 내려친 자리에 보랏빛 벼락 · 땅이 갈라짐 · 큰 검기
            JobFx.slash(sp, j, 2.0, 0.5, 3.0, 270.0, 0.24);
            JobFx.slashCard(lv, V.up(V.add(c, f, 1.8), 1.1), f, 270.0, 5.0, 0.28, Vec3.ZERO, j, JobFx.hide1p(sp));
            JobFx.groundWave(lv, sp, V.add(c, f, 0.8), f, 1.6, reach + 1.5, 18, j);
            JobFx.thunder(lv, g, 9, VIOLET, VIOLET_CORE, 0.32, Opt.empty());
            JobFx.smash(lv, V.up(g, 0.05), 2.4, j, 0.45, Opt.empty());
            JobFx.volt(lv, V.up(g, 0.08), 1.8, j, 0.4, Opt.empty());
            SkillFx.play(lv, "entity.generic.explode", g, 1.3, 0.5);
            SkillFx.play(lv, "entity.lightning_bolt.impact", g, 0.9, 0.6);
            if (e == null) return;
            JobKit.strike(sp, e, j, P, k.num("smash", 5.5), false, true, life, 0, d -> {
                JobTraits13.steal(sp, e, j, (int) k.num("steal", 2));
                JobImpact.hit(sp, e, j, 1.8);
                if (e.level() instanceof ServerLevel el) {
                    Vec3 b = Kit.bodyPoint(e);
                    JobFx.burst(el, b, 1.3 + Kit.bodyPad(e) * 0.5, CRIMSON, CRIMSON_CORE, 0.3, Opt.of("vel", RuneKit.entVel(e)));
                    ThunderlordSkills.crackle(el, b, 1.0 + Kit.bodyPad(e), 3, VIOLET, VIOLET_CORE, 0.3, Opt.of("vel", RuneKit.entVel(e)));
                }
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 섀클 볼트

    /**
     * 섀클 볼트 (롤 사일러스 E): 보랏빛 번개 사슬을 겨눈 적 (range 칸 — 없으면 쓰지 않음) 에게 던져 latchAt 틱에 감음 → 사슬을 당겨 그 적 앞으로 끌려가 slamAt 틱에 어깨로
     * 들이받음 (slam · stun 틱 기절 — 강화 · 힘 steal). 묶였거나 타고 있으면 쓰지 않음
     */
    static boolean shackle(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        LivingEntity e = Kit.pickTarget(sp, k.num("range", 10), 20);
        if (e == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int latchAt = Math.max(1, (int) k.num("latchAt", 4));
        int slamAt = Math.max(latchAt + 2, (int) k.num("slamAt", 10));
        Vec3 start = sp.position();
        Vec3 back = V.flat(V.sub(start, e.position()));
        Vec3 u = V.len(back) > 0.1 ? V.unit(back) : V.scale(Kit.facingOf(sp), -1);
        double pad = Kit.bodyPad(e);
        Vec3 dest = JobTraits12.beyond(sp, e.position(), u, 1.2 + pad);
        if (dest == null) dest = start;
        Vec3 land = dest;
        RpgCombat.setSteadfast(sp, slamAt + 3);
        // 던짐: 왼손에서 보랏빛 번개 사슬이 날아감 (latchAt 틱에 닿음)
        Vec3 hand = V.up(V.add(start, V.rightOf(Kit.facingOf(sp)), -0.35), 1.3);
        double dist = V.len(V.sub(Kit.bodyPoint(e), hand));
        JobFx.bolt(level0, hand, Kit.bodyPoint(e), VIOLET, VIOLET_CORE, dist / Math.max(0.05, latchAt / 20.0), 0.55, Opt.empty());
        SkillFx.play(level0, "block.chain.place", start, 0.8, 1.0);
        SkillFx.play(level0, "item.trident.throw", start, 1.2, 0.8);
        RuneKit.later(latchAt, () -> {
            if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
            ServerLevel lv = sp.level();
            Vec3 b = Kit.bodyPoint(e);
            // 감음: 그 적에게 보랏빛 벼락이 떨어지며 사슬이 감김 · 둘레의 번개 · 발밑 고리 (영상 1: 작은 터짐뿐이라 감긴 때가 보이지 않았음)
            JobFx.burst(lv, b, 1.4 + pad * 0.4, VIOLET, VIOLET_CORE, 0.3, Opt.of("vel", RuneKit.entVel(e)));
            JobFx.thunder(lv, e.position(), 7 + e.getBbHeight(), VIOLET, VIOLET_CORE, 0.3, Opt.of("vel", RuneKit.entVel(e)));
            JobFx.volt(lv, V.up(e.position(), 0.08), 1.2 + pad * 0.5, j, 0.4, Opt.of("vel", RuneKit.entVel(e)));
            ThunderlordSkills.crackle(lv, b, 1.2 + pad, 4, VIOLET, VIOLET_CORE, 0.3, Opt.of("vel", RuneKit.entVel(e)));
            SkillFx.play(lv, "block.chain.hit", b, 0.9, 1.0);
            SkillFx.play(lv, "entity.lightning_bolt.impact", b, 1.6, 0.4);
        });
        // 끌려감: latchAt → slamAt (사슬이 줄어듦 — 2틱마다 다시 그림)
        JobMove.run(sp, JobMove.hold(0, latchAt), JobMove.to(latchAt, slamAt, JobMove.at(land), JobMove.IN));
        for (int t = latchAt; t < slamAt; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                ServerLevel lv = sp.level();
                Vec3 h = V.up(V.add(sp.position(), Kit.facingOf(sp), 0.4), 1.2);
                Vec3 b = Kit.bodyPoint(e);
                JobFx.chain(lv, h, b, j, 1.25, 0.12, Opt.of("alpha", 1.0)); // 영상 1: 0.8칸 마디는 가늘어 보이지 않았음
                JobFx.arc(lv, h, b, VIOLET, VIOLET_CORE, 0.12, Opt.empty());
                RuneKit.shadeClone(sp, lv, sp.position(), Kit.facingOf(sp), 1, VIOLET, VIOLET_CORE, Opt.of("life", 0.25, "fade", 0.7, "alpha", 0.55));
            });
        }
        RuneKit.later(slamAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            JobFx.slash(sp, j, 1.6, 0.3, 2.2, 200.0, 0.22);
            // 들이받음: 앞의 보랏빛 충격 · 땅을 긁는 고리 · 핏빛 터짐 (영상 1: 작은 고리뿐)
            Vec3 hit = V.up(V.add(c, Kit.facingOf(sp), 1.0), 1.0);
            JobFx.burst(lv, hit, 1.8, VIOLET, VIOLET_CORE, 0.3, JobFx.hide1p(sp));
            JobFx.smash(lv, V.up(V.add(c, Kit.facingOf(sp), 0.8), 0.05), 2.0, j, 0.4, Opt.empty());
            RuneKit.novaRing(lv, V.add(c, Kit.facingOf(sp), 0.8), 0.6, 3.0, CRIMSON, CRIMSON_CORE, Opt.of("life", 0.35));
            SkillFx.play(lv, "entity.goat.ram_impact", c, 0.8, 1.0);
            SkillFx.play(lv, "entity.lightning_bolt.impact", c, 1.3, 0.5);
            if (e.isRemoved() || !e.isAlive() || e.distanceTo(sp) > 3.5 + pad) return;
            JobKit.strike(sp, e, j, P, k.num("slam", 3.3), false, false, life, 0, d -> {
                JobTraits4.stun(e, j, (int) JobUpgrades.num(sp, k, "stun", 10));
                JobTraits13.steal(sp, e, j, (int) k.num("steal", 1));
                JobImpact.hit(sp, e, j, 1.4);
                if (e.level() instanceof ServerLevel el) {
                    Vec3 b = Kit.bodyPoint(e);
                    JobFx.burst(el, b, 1.1 + Kit.bodyPad(e) * 0.4, VIOLET, VIOLET_CORE, 0.28, Opt.of("vel", RuneKit.entVel(e)));
                    JobFx.volt(el, V.up(e.position(), 0.08), 1.2 + Kit.bodyPad(e) * 0.5, j, 0.35, Opt.of("vel", RuneKit.entVel(e)));
                }
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 킹스 그립

    /**
     * 킹스 그립 (롤 트런들 R): 왼손을 뻗으면 겨눈 적 (range 칸 — 없으면 쓰지 않음) 위로 거대한 거인 왕의 손 (보랏빛 망령) 이 내려와 grabAt 틱에 움켜쥐어 들어 올림 (lift 칸
     * — 밀 수 있는 적만 · 묶음) → first 틱부터 every 틱마다 squeezes 번 쥐어짬 (squeeze · 힘 steal 씩 · 준 피해 × leech 회복) → slamAt 틱 그 적을 내 앞 땅에 내리꽂음
     * (slam · stun 틱 기절). 보스 · 큰 몹은 들지 않고 그 자리에서
     */
    static boolean grip(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        LivingEntity e = Kit.pickTarget(sp, k.num("range", 8), 20);
        if (e == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int grabAt = Math.max(2, (int) k.num("grabAt", 6));
        int first = Math.max(grabAt + 1, (int) k.num("first", 10));
        int every = Math.max(10, (int) k.num("every", 10));
        int squeezes = Math.max(1, (int) k.num("squeezes", 3));
        int slamAt = Math.max(first + (squeezes - 1) * every + 10, (int) k.num("slamAt", 40));
        double pad = Kit.bodyPad(e);
        double size = 1.45 + pad * 0.8; // 영상 1: 1.25 는 쥔 적에 손가락이 묻혀 분홍 고치처럼 보였음
        boolean lift = JobKit.pushable(e) && e.getBbHeight() <= 2.6;
        double liftH = k.num("lift", 1.2);
        Vec3 base = e.position();
        Vec3[] held = {null};
        RpgCombat.setSteadfast(sp, slamAt + 3);
        // 뻗음: 거대한 손이 그 적 위에서 내려옴 · 내 왼손에서 그 적으로 보랏빛 번개
        Vec3 hb = Kit.bodyPoint(e);
        // 2026-11-13 3D 왕의 손 (직업 도감 메모 "왕의 손을 3d로" — 2D 손 그림 대신): 그 적 머리 위 높이에서 손가락을 펼치며 나타나 (손가락이 나를 봄 — 쥐면 적의 앞쪽으로
        // 감김) grabAt 에 머리 위로 내려와 움켜쥠 → 들어 올린 적을 따라감 · 쥐어짤 때마다 꽉 → slamAt 에 내 앞 땅으로 내리꽂고 놓으며 흩어짐
        Vec3 toMe = V.flat(V.sub(sp.position(), e.position()));
        double hs = 1.35 + pad * 0.9;
        double over = Math.max(0, e.getBbHeight() - 1.95) + 0.12;
        RuneMob hand = RuneMob.spawn(HAND, level0, V.up(e.position(), over + 2.6), toMe, slamAt + 40, hs, true);
        if (hand != null) {
            hand.hold("summon");
            hand.moveTo(() -> V.up(e.isRemoved() ? base : e.position(), over), grabAt, 0, RuneMob.IN, false);
        } else JobFx.grip(level0, V.up(hb, 1.4 + size * 0.4), size, 0, 2, grabAt / 20.0, false, Opt.of("dir", V.DOWN, "speed", (1.4 + size * 0.4) / (grabAt / 20.0)),
                VIOLET, CRIMSON);
        JobFx.arc(level0, V.up(V.add(sp.position(), V.rightOf(Kit.facingOf(sp)), -0.4), 1.4), hb, VIOLET, VIOLET_CORE, grabAt / 20.0, Opt.empty());
        SkillFx.play(level0, "entity.warden.sonic_charge", sp.position(), 1.4, 0.6);
        SkillFx.play(level0, "block.beacon.deactivate", hb, 0.7, 0.7);
        // 움켜쥠 · 들어 올림 · 묶음
        RuneKit.later(grabAt, () -> {
            if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !e.isAlive()) return;
            ServerLevel lv = sp.level();
            RuneKit.effect(e, "slowness", slamAt - grabAt + 4, JobKit.isBoss(e) ? 1 : 10);
            if (hand != null && hand.alive()) {
                hand.hold("grab");
                hand.follow(() -> e.isRemoved() ? null : V.up(e.position(), over), 0.7, slamAt - grabAt - 4);
            } else JobFx.grip(lv, V.up(ThunderlordSkills.inFront(sp, e, 0.15), e.getBbHeight() * 0.55), size, 2, 1, 0.15, false, Opt.of("vel", RuneKit.entVel(e)), VIOLET,
                    CRIMSON);
            JobFx.burst(lv, Kit.bodyPoint(e), 1.2 + pad * 0.4, VIOLET, VIOLET_CORE, 0.25, Opt.of("vel", RuneKit.entVel(e)));
            SkillFx.play(lv, "entity.iron_golem.attack", e.position(), 0.6, 0.9);
        });
        for (int t = grabAt + 1; t < slamAt - 1; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !e.isAlive()) return;
                ServerLevel lv = sp.level();
                if (lift) {
                    double u = Math.min(1, (tt - grabAt) / 4.0);
                    Vec3 p = new Vec3(base.x, base.y + liftH * u + Math.sin(tt * 0.9) * 0.04, base.z);
                    if (Kit.moveTo(e, p)) held[0] = p;
                    e.resetFallDistance();
                }
                if ((tt - grabAt) % 6 == 1) {
                    if (hand == null || !hand.alive()) JobFx.grip(lv, V.up(ThunderlordSkills.inFront(sp, e, 0.15), e.getBbHeight() * 0.55), size, 3, 2, 0.32, true, Opt.empty(),
                            VIOLET, CRIMSON);
                    JobFx.arc(lv, V.up(V.add(sp.position(), V.rightOf(Kit.facingOf(sp)), -0.4), 1.4), Kit.bodyPoint(e), VIOLET, VIOLET_CORE, 0.3, Opt.empty());
                }
            });
        }
        // 쥐어짬: 힘을 빼앗음 · 회복
        for (int i = 0; i < squeezes; i++) {
            RuneKit.later(first + i * every, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !e.isAlive()) return;
                ServerLevel lv = sp.level();
                Vec3 b = Kit.bodyPoint(e);
                if (hand != null && hand.alive()) hand.hold("squeeze");
                JobFx.burst(lv, b, 1.0 + pad * 0.4, CRIMSON, CRIMSON_CORE, 0.25, Opt.empty());
                SkillFx.play(lv, "entity.warden.heartbeat", b, 0.9, 0.9);
                JobKit.strike(sp, e, j, P, k.num("squeeze", 1.65), true, false, life, 0, d -> {
                    JobTraits13.steal(sp, e, j, (int) k.num("steal", 1));
                    double healed = RpgHealth.healBy(sp, d * k.num("leech", 0.3));
                    if (healed >= 0.05) DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
                    JobImpact.fx(sp, e, j, 1.0);
                });
            });
        }
        JobKit.motionLater(sp, "tyrant_grip:slam", slamAt - 4, life);
        RuneKit.later(slamAt - 4, () -> {
            if (hand == null || !hand.alive() || !RuneKit.sameLife(sp, life)) return;
            Vec3 c = sp.position();
            Vec3 g = held[0] != null ? ThunderlordSkills.ground(sp.level(), V.add(c, Kit.facingOf(sp), 1.9 + pad)) : ThunderlordSkills.ground(sp.level(), e.position());
            hand.hold("slam");
            hand.moveTo(V.up(g, over - 0.3), 4, 0.8, RuneMob.IN, false);
            RuneKit.later(10, () -> {
                if (hand.alive()) hand.hold("release");
            });
            RuneKit.later(16, () -> hand.leave("vanish", 8));
        });
        // 내리꽂음: 손이 그 적을 내 앞 땅으로
        RuneKit.later(slamAt, () -> {
            if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !e.isAlive()) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            Vec3 g = held[0] != null ? ThunderlordSkills.ground(lv, V.add(c, f, 1.9 + pad)) : ThunderlordSkills.ground(lv, e.position());
            if (held[0] != null) {
                if (!Kit.moveTo(e, g)) Kit.moveTo(e, new Vec3(e.getX(), base.y, e.getZ()));
                e.resetFallDistance();
            }
            Vec3 b = Kit.bodyPoint(e);
            if (hand == null || !hand.alive()) JobFx.grip(lv, V.up(ThunderlordSkills.inFront(sp, e, 0.15), e.getBbHeight() * 0.55 + 0.3), size, 5, 3, 0.45, false,
                    Opt.empty(), VIOLET, CRIMSON);
            JobFx.smash(lv, V.up(e.position(), 0.05), 2.4, j, 0.5, Opt.empty());
            JobFx.volt(lv, V.up(e.position(), 0.08), 2.0, j, 0.45, Opt.empty());
            JobFx.thunder(lv, e.position(), 8, VIOLET, VIOLET_CORE, 0.3, Opt.empty());
            SkillFx.play(lv, "entity.generic.explode", b, 1.2, 0.6);
            SkillFx.play(lv, "item.mace.smash_ground", b, 0.8, 0.8);
            JobKit.strike(sp, e, j, P, k.num("slam", 4.95), false, true, life, 0, d -> {
                JobTraits4.stun(e, j, (int) k.num("stun", 10));
                JobImpact.hit(sp, e, j, 2.0);
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 콜로서스

    /**
     * 콜로서스 (롤 초가스 R · 나서스 R): dur 틱 태세 (강화 지속) — 몸이 거대해짐 (크기 + grow — 강탈 몫에 더함 · 평타 사거리 + reach) · 받는 피해 − reduce · 빼앗는 힘
     * × stealMult · 밀려나지 않음 · 머리 위 보랏빛 번개 왕관 (JobTraits13). 그림: 내게 내리치는 보랏빛 벼락 · 번개 고리 · 연기 · 몸의 빛 · 포효
     */
    static boolean colossus(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) JobUpgrades.num(sp, k, "dur", 200);
        JobTraits.startStance(sp, k, dur);
        RpgCombat.setSteadfast(sp, dur);
        JobTraits13.applyScale(sp, j);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        JobFx.crown(level, V.up(c, sp.getBbHeight() + 0.45), 0.5, 0, 2, 0.3, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), VIOLET, VIOLET_CORE);
        JobFx.thunder(level, c, sp.getBbHeight() + 7, VIOLET, VIOLET_CORE, 0.35, Opt.empty());
        JobFx.volt(level, V.up(c, 0.08), 2.6, j, 0.5, Opt.empty());
        JobFx.smoke(level, V.up(c, 0.6), 1.6, VIOLET, CRIMSON, 0.6, JobFx.hide1p(sp).with("alpha", 0.65));
        RuneKit.novaRing(level, c, 0.7, 3.0, VIOLET, VIOLET_CORE, Opt.of("life", 0.45));
        JobFx.aura(sp, j, 3.0, 0.9, 1.0);
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.5);
        Ult.flash(level, c, VIOLET, Opt.of("radius", 4.0, "time", new double[] {0.03, 0.05, 0.3}), sp);
        SkillFx.play(level, "entity.ravager.roar", c, 0.9, 1.0);
        SkillFx.play(level, "entity.lightning_bolt.impact", c, 0.8, 0.5);
        Notify.notify(sp, "§5§l" + k.name() + " §7— 몸이 거대해짐 · 빼앗는 힘 두 배", 40);
        return true;
    }

    // ------------------------------------------------------------ 스톨른 썬더

    /** 2026-11-13 킹스 그립의 3D 왕의 손 (tools/job_mobs_art.py job_hand — 보랏빛 망령 갑주 손 · 금 테 · 핏빛 발톱, 손가락이 앞) */
    private static final String HAND = "rpg:job_hand";

    /** 2026-11-13 스톨른 썬더의 3D 망치 크기: 머리 위 (빨아들일 때마다 — 길이 약 2.3 · 3.0 · 3.6칸) · 떨어지는 거대한 망치 (약 6.7칸) */
    private static final double[] HAMMER_UP = {0.9, 1.15, 1.4};
    private static final double HAMMER_BIG = 2.6;

    /**
     * 스톨른 썬더 (롤 사일러스 R): 빼앗긴 하늘 (rpg:ult_usurp) — 망치를 하늘로 치켜들면 first 틱부터 every 틱마다 drains 번 둘레 radius 칸 적들 (targets 까지 — 가까운
     * 차례) 의 힘이 보랏빛 번개 줄을 타고 망치로 빨려 듦 (drain · 적마다 힘 +1 · 빨아들인 힘마다 최대 체력 × heal 회복 — 강화) · 몸과 머리 위의 거대한 망치가 자라남 →
     * leapAt 틱 겨눈 적 (range 칸 — 없으면 가장 가까운 적 · 없으면 앞 6칸) 앞으로 뛰어올라 slamAt 틱 내려침 (둘레 slamRadius 칸 slam · 띄움). 묶였거나 타고 있으면
     * 뛰지 않고 그 자리에서
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int first = Math.max(2, (int) k.num("first", 10));
        int every = Math.max(10, (int) k.num("every", 10));
        int drains = Math.max(1, (int) k.num("drains", 3));
        int lastDrain = first + (drains - 1) * every;
        int leapAt = Math.max(lastDrain + 1, (int) k.num("leapAt", 32));
        int slamAt = Math.max(Math.max(leapAt + 6, lastDrain + 10), (int) k.num("slamAt", 44));
        double radius = k.num("radius", 10);
        int targets = Math.max(1, (int) k.num("targets", 8));
        Vec3 c0 = sp.position();
        Ult.sky(level0, c0, "rpg:ult_usurp", slamAt + 30);
        RpgCombat.setSteadfast(sp, slamAt + 4);
        JobTraits13.ultGrow(sp, j, 0.15);
        // 치켜듦: 하늘의 보랏빛 먹구름 · 망치로 모이는 보랏빛 · 발밑 고리
        JobFx.charge(sp, j, new Vec3(0.2, 2.8, 0.15), first, 1.0);
        Vec3 sky = V.up(c0, 9.5);
        JobFx.cloud(level0, sky, 5.0, 0, 2, 0.4, false, Opt.empty(), VIOLET, CRIMSON);
        RuneKit.later(8, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.cloud(sp.level(), sky, 5.4, 2, 4, (slamAt - 8) / 20.0, true, Opt.empty(), VIOLET, CRIMSON);
        });
        JobFx.volt(level0, V.up(c0, 0.08), 2.6, j, first / 20.0 + 0.2, Opt.empty());
        JobFx.aura(sp, j, 3.2, 1.0, 1.0);
        SkillFx.play(level0, "entity.lightning_bolt.thunder", c0, 0.5, 0.8);
        SkillFx.play(level0, "entity.ravager.roar", c0, 0.7, 0.8);
        // 2026-11-13 3D 망치 (직업 도감 메모 "머리 위 망치를 3d로" — 2D 망치 판 대신, rune_hammer · 보랏빛 룬): first 에 머리 위에 나타나 천천히 돌며 몸을 따라감 ·
        // 빨아들일 때마다 커짐 → leapAt 에 겨눈 곳 하늘로 솟아 거대해짐 (머리가 아래) → slamAt − 6 에 떨어져 slamAt 에 땅에 박힘
        Vfx.Fx hfx = RuneProps.fx(level0, c0, slamAt + 24);
        Vfx.Prop[] ham = {null};
        double[] hs = {HAMMER_UP[0]};
        RuneKit.later(first, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 top = V.up(sp.position(), sp.getBbHeight() + 0.45);
            ham[0] = RuneProps.prop(hfx, RuneProps.HAMMER, top, RuneProps.HAMMER_GRIP, RuneProps.along(V.UP, new Vec3(1, 0, 0)), hs[0] * 0.3, VIOLET);
            ham[0].size(hs[0], 4);
        });
        hfx.each(first + 1, leapAt - 1, 1, a -> {
            if (ham[0] == null) return;
            if (!RuneKit.sameLife(sp, life)) {
                hfx.end();
                return;
            }
            double yaw = Math.toRadians(a * 7.0);
            ham[0].to(V.up(sp.position(), sp.getBbHeight() + 0.45), RuneProps.along(V.UP, new Vec3(Math.cos(yaw), 0, Math.sin(yaw))), hs[0], 1);
        });
        // 빨아들임: 적마다 보랏빛 번개 줄 · 핏빛 기운이 망치로 · 머리 위 망치가 자라남
        int[] sucked = {0};
        for (int i = 0; i < drains; i++) {
            int ii = i;
            RuneKit.later(first + i * every, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 top = V.up(sp.position(), sp.getBbHeight() + 0.7); // 영상 1: + 1.2 는 3인칭 화면 위 끝에 망치가 잘렸음
                List<LivingEntity> foes = new ArrayList<>(Kit.around(sp, sp.position(), radius, 4));
                foes.removeIf(e -> !e.isAlive() || e.isRemoved());
                foes.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
                if (foes.size() > targets) foes = new ArrayList<>(foes.subList(0, targets));
                JobTraits13.ultGrow(sp, j, 0.15 + 0.15 * (ii + 1));
                hs[0] = HAMMER_UP[Math.min(ii, HAMMER_UP.length - 1)];
                JobFx.glint(lv, V.up(top, 2.0 + hs[0]), 0.9 + 0.3 * ii, VIOLET, VIOLET_CORE, 0.3, Opt.empty());
                SkillFx.play(lv, "entity.warden.heartbeat", sp.position(), 0.8 + 0.1 * ii, 1.0);
                SkillFx.play(lv, "entity.lightning_bolt.impact", sp.position(), 1.6 - 0.15 * ii, 0.5);
                for (LivingEntity e : foes) {
                    Vec3 b = Kit.bodyPoint(e);
                    JobFx.arc(lv, b, top, VIOLET, VIOLET_CORE, 0.3, Opt.of("flash", 1.0));
                    JobFx.soulFly(lv, b, top, 10, 0.3, CRIMSON, CRIMSON_CORE, Opt.empty());
                    JobKit.strike(sp, e, j, P, k.num("drain", 4), true, false, life, 0, d -> {
                        int got = JobTraits13.steal(sp, e, j, 1);
                        sucked[0]++;
                        double healed = RpgHealth.healBy(sp, RpgHealth.maxOf(sp) * JobUpgrades.num(sp, k, "heal", 0.01) * Math.max(1, got));
                        if (healed >= 0.05) DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
                        JobImpact.fx(sp, e, j, 0.9);
                    });
                }
            });
        }
        // 뛰어오름: 겨눈 적 앞 (없으면 가장 가까운 적 · 없으면 앞)
        Vec3[] spot = {null};
        RuneKit.later(leapAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            LivingEntity aim = Kit.pickTarget(sp, k.num("range", 12), 25);
            if (aim == null) {
                double best = Double.MAX_VALUE;
                for (LivingEntity x : Kit.around(sp, c, k.num("range", 12), 4)) {
                    if (!x.isAlive() || x.isRemoved()) continue;
                    double d = x.distanceToSqr(sp);
                    if (d < best) {
                        best = d;
                        aim = x;
                    }
                }
            }
            Vec3 g;
            Vec3 land = c;
            if (aim != null) {
                Vec3 back = V.flat(V.sub(c, aim.position()));
                Vec3 u = V.len(back) > 0.1 ? V.unit(back) : V.scale(f, -1);
                g = ThunderlordSkills.ground(lv, aim.position());
                Vec3 s = JobTraits12.beyond(sp, aim.position(), u, 1.4 + Kit.bodyPad(aim));
                if (s != null) land = s;
            } else {
                double can = RuneKit.dashDistance(sp, f, 6, 0.3, 1.5);
                g = ThunderlordSkills.ground(lv, V.add(c, f, can + 1.2));
                Vec3 s = Kit.standSpot(lv, V.up(V.add(c, f, can), 0.6), 2.5);
                if (s != null) land = s;
            }
            spot[0] = g;
            if (!Status.isRooted(sp) && !Kit.ridingAny(sp) && V.hdist(land, c) > 0.4) {
                double peak = JobMove.hopPeak(sp, c, land, 2.6);
                JobMove.run(sp, JobMove.hop(0, slamAt - leapAt, JobMove.at(land), peak));
            }
            JobFx.speed(sp, j, (slamAt - leapAt) / 20.0, 0.7);
            SkillFx.play(lv, "entity.ravager.roar", c, 1.0, 0.7);
            // 3D 망치가 겨눈 곳 하늘로 솟아 거대해짐 (머리가 아래 · 22° 기움) → 6틱 동안 떨어져 slamAt 에 머리가 땅에 박힘
            Vec3 lean = V.add(V.scale(V.DOWN, Math.cos(Math.toRadians(22))), V.rightOf(f), -Math.sin(Math.toRadians(22)));
            org.joml.Quaternionf down = RuneProps.along(lean, f);
            double reach = (RuneProps.HAMMER_HEAD[1] - RuneProps.HAMMER_GRIP[1]) / 16.0 * HAMMER_BIG;
            Vec3 headSky = V.up(g, 10.0);
            if (ham[0] != null) ham[0].to(V.add(headSky, lean, -reach), down, HAMMER_BIG, Math.max(2, slamAt - leapAt - 6));
            RuneKit.later(slamAt - leapAt - 6, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 gg = spot[0];
                if (ham[0] != null) {
                    ham[0].to(V.add(V.up(gg, 1.0), lean, -reach), down, HAMMER_BIG, 6);
                    Vfx.Prop hp = ham[0];
                    RuneKit.later(6 + 8, () -> hp.size(HAMMER_BIG * 0.08, 6));
                    RuneKit.later(6 + 8 + 6, hp::remove);
                }
                SkillFx.play(sp.level(), "item.trident.thunder", gg, 0.7, 0.6);
            });
        });
        JobKit.motionLater(sp, "tyrant_ult:slam", slamAt - 5, life);
        RuneKit.later(slamAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 g = spot[0] != null ? spot[0] : sp.position();
            double r = k.num("slamRadius", 3);
            JobFx.thunder(lv, g, 16, VIOLET, VIOLET_CORE, 0.5, Opt.empty());
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + Kit.rand();
                JobFx.thunder(lv, V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), r * 0.9), 10, j, 0.35, Opt.of("alpha", 0.85));
            }
            JobFx.smash(lv, V.up(g, 0.05), r * 1.2, j, 0.6, Opt.empty());
            JobFx.volt(lv, V.up(g, 0.1), r, j, 0.5, Opt.empty());
            JobFx.burst(lv, V.up(g, 1.0), 3.2, CRIMSON, CRIMSON_CORE, 0.4, Opt.empty());
            RuneKit.novaRing(lv, g, 1.0, r * 2.2, VIOLET, VIOLET_CORE, Opt.of("life", 0.5));
            RuneKit.glowCrack(lv, V.up(g, 0.04), r * 1.4, VIOLET, VIOLET_CORE, Opt.of("life", 0.9));
            JobFx.slash(sp, j, 2.0, 0.6, 3.2, 270.0, 0.3);
            Ult.flash(lv, g, VIOLET, Opt.of("radius", 7.0, "time", new double[] {0.02, 0.06, 0.4}), sp);
            SkillFx.sparks(lv, V.up(g, 0.5), CRIMSON, 18, 8);
            SkillFx.play(lv, "entity.lightning_bolt.thunder", g, 0.6, 1.0);
            SkillFx.play(lv, "entity.generic.explode", g, 0.8, 1.0);
            SkillFx.play(lv, "item.mace.smash_ground", g, 0.6, 1.0);
            for (LivingEntity e : Kit.around(sp, g, r, 3.5)) {
                if (!e.isAlive() || e.isRemoved()) continue;
                JobKit.strike(sp, e, j, P, k.num("slam", 32), false, true, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 2.4);
                    if (JobKit.pushable(e)) Kit.launch(e, 0, k.num("lift", 0.6), 0);
                });
            }
            if (sucked[0] > 0) JobSystem.note(sp, "§5스톨른 썬더 §7— 빨아들인 힘 " + sucked[0]);
        });
        RuneKit.later(slamAt + 8, () -> {
            if (!sp.isRemoved()) JobTraits13.ultGrow(sp, JobTraits13.jobIf(sp, JobTraits13.TY), 0);
        });
        return true;
    }
}
