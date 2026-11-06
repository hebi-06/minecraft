package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Jobs;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-06 수호기사 각성 피닉스 가드 (다시 — 사용자 "레인보우 컨셉이 개짜치고 반전도 이렇다할 멋이 없음 … 나는 멋이 제일 중요함" → 고른 것: 푸른 불사조, jobs.json advance
 * phoenix — 푸른 불꽃 · 흰 불, 특성 「반격」 + 불사조의 재 — JobTraits4 · JobTraits15): 1차와 같은 막고 버티는 탱커 — 피닉스 배시 · 윙 커버 · 블루 인페르노 · 이그니션 ·
 * 피닉스 디센트. 몸 쓰는 직업이라 제 몸 · 방패 동작만 (불꽃 날개 · 불사조 · 불기둥은 그림). 단타는 크게 감았다가 큰 타격, 화면은 흔들지 않음.
 */
final class PhoenixSkills {
    private PhoenixSkills() {}

    static final double[] AZURE = JobTraits15.AZURE;
    static final double[] AZURE_CORE = JobTraits15.AZURE_CORE;
    static final double[] DEEP = JobTraits15.DEEP;
    static final double[] DEEP_CORE = JobTraits15.DEEP_CORE;
    /** 날아드는 동안 등의 날개 · 블루 인페르노의 휘도는 날개 둘 · 디센트의 불사조 (solo — 주인마다 하나씩 다시 그림) */
    static final double WING_SOLO = 94.0;
    static final double SWEEP_SOLO = 95.0;
    static final double BIRD_SOLO = 97.0;
    /** 디센트의 불사조: 가슴 높이 (불기둥 밑에서) · 날개 끝 사이 (칸) */
    static final double BIRD_UP = 8.0;
    static final double BIRD_SPAN = 10.5;

    static void register() {
        JobSystem.register("phoenix_bash", PhoenixSkills::bash);
        JobSystem.register("phoenix_wing", PhoenixSkills::wing);
        JobSystem.register("phoenix_inferno", PhoenixSkills::inferno);
        JobSystem.register("phoenix_ignition", PhoenixSkills::ignition);
        JobSystem.register("phoenix_ult", PhoenixSkills::ult);
    }

    /** 머리 위로 치켜든 방패 (발에서 위 2.5칸 · 앞 0.25칸) */
    private static Vec3 overhead(ServerPlayer sp) {
        return V.up(V.add(sp.position(), Kit.facingOf(sp), 0.25), 2.45);
    }

    /** 등 (두 날개의 뿌리 — 발에서 위 1.35칸 · 뒤 0.3칸) */
    static Vec3 back(ServerPlayer sp) {
        return V.up(V.add(sp.position(), Kit.facingOf(sp), -0.3), 1.35);
    }

    /** 1인칭 눈앞 위 (치켜든 방패의 날개 — 화면 위쪽) */
    private static Vec3 eyeTop(ServerPlayer sp, double ahead, double up) {
        Vec3 look = sp.getViewVector(1F);
        return V.add(V.add(sp.getEyePosition(), look, ahead), JobFx.screenUp(look), up);
    }

    /** 보호막 (최대 체력 × k · ticks 틱) + 감싸는 불꽃 날개 · 발밑 푸른 고리 */
    private static void wingShield(ServerPlayer o, ServerPlayer caster, double k, int ticks) {
        double cap = RpgHealth.maxOf(o) * k;
        RpgHealth.addShield(o, cap, ticks, cap);
        ServerLevel ol = o.level();
        Opt own = o == caster ? JobFx.hide1p(caster) : Opt.empty();
        JobFx15.fwings(ol, V.up(o.position(), 1.3), 2.4, 5, 2, 0.5, false, own.with("vel", RuneKit.bodyVel(o)), AZURE, AZURE_CORE);
        JobFx15.halo(ol, V.up(ThunderlordSkills.ground(ol, o.position()), 0.06), 0.9, ticks / 20.0, Opt.of("alpha", 0.6, "fade", 0.15, "vel", RuneKit.bodyVel(o)), AZURE,
                AZURE_CORE);
        SkillFx.sparks(ol, JobTraits15.chest(o), AZURE_CORE, 8, 3);
    }

    // ------------------------------------------------------------ 피닉스 배시

    /**
     * 피닉스 배시 (롤 판테온 W · 레오나 Q): 방패를 머리 위로 치켜들면 방패 양옆으로 푸른 불꽃 날개가 펼쳐짐 (bashAt 틱) → 앞 reach 칸 (arc°) 을 내리침 (bash · 띄움 lift ·
     * taunt 틱 도발) — 날개가 앞으로 휘몰아치며 불꽃 깃털로 흩어짐
     */
    static boolean bash(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int at = Math.max(2, (int) k.num("bashAt", 6));
        double reach = k.num("reach", 3.8);
        double arc = k.num("arc", 120);
        int taunt = (int) k.num("taunt", 80);
        RpgCombat.setSteadfast(sp, at + 2);
        // 치켜듦: 방패 양옆으로 날개가 활짝 펼쳐짐 (3인칭 머리 위 · 1인칭 화면 위 — 펼친 장 1 → 2 · 2026-11-06 영상 검토: 모이는 장 0 부터는 작고 옅어 날개가 잘 보이지 않았음)
        // · 방패에 모이는 불
        JobFx15.fwings(level0, overhead(sp), 3.6, 1, 2, at / 20.0 + 0.05, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), AZURE, AZURE_CORE);
        JobFx15.fwings(level0, eyeTop(sp, 1.8, 0.55), 2.2, 1, 2, at / 20.0 + 0.05, false, JobFx.only1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.85), AZURE,
                AZURE_CORE);
        JobFx.charge(sp, j, new Vec3(0.0, 2.4, 0.25), at, 0.7);
        SkillFx.play(level0, "item.firecharge.use", sp.position(), 0.9, 0.6);
        SkillFx.play(level0, "entity.ender_dragon.flap", sp.position(), 1.4, 0.6);
        RuneKit.later(at, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            Vec3 front = V.add(c, f, reach * 0.5);
            // 내리침: 눈앞 세로 큰 베기 · 땅의 푸른 불꽃 부채 (120° — 둘을 벌려) · 앞으로 휘몰아치는 날개 → 깃털 · 충격
            JobFx.slash(sp, j, 1.7, 0.1, 2.4, 95.0, 0.24);
            JobFx15.furyWide(lv, V.up(V.add(c, f, 0.3), 0.06), f, reach + 0.4, arc, 0, 6, 0.55, Opt.of("alpha", 0.95), AZURE, AZURE_CORE);
            JobFx15.fwings(lv, V.up(front, 1.2), 3.8, 7, 1, 0.45, false, Opt.of("alpha", 0.95), AZURE, AZURE_CORE);
            JobFx.bigWave(lv, sp, V.up(V.add(c, f, 0.8), 1.0), f, 12, 1.5, 90, 0.3, j, AZURE, AZURE_CORE, Opt.empty());
            JobFx.smash(lv, V.up(ThunderlordSkills.ground(lv, V.add(c, f, 1.4)), 0.05), 1.8, j, 0.4, Opt.empty());
            JobFx.feathers(lv, V.up(front, 1.3), 10, 1.4, 0.32, j, 1.3, Opt.empty());
            SkillFx.play(lv, "item.mace.smash_ground", c, 0.9, 0.9);
            SkillFx.play(lv, "entity.blaze.shoot", c, 0.8, 0.8);
            SkillFx.play(lv, "item.shield.block", c, 0.6, 0.9);
            for (LivingEntity e : RuneKit.coneTargets(sp, reach, arc, 6)) {
                JobTraits15.taunt(sp, e, taunt);
                JobKit.strike(sp, e, j, P, k.num("bash", 4), false, false, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 1.5);
                    if (e.level() instanceof ServerLevel el) JobFx.burst(el, Kit.bodyPoint(e), 1.0 + Kit.bodyPad(e) * 0.4, AZURE, AZURE_CORE, 0.3, Opt.of("vel",
                            RuneKit.entVel(e)));
                    if (JobKit.pushable(e)) Kit.launch(e, f.x * 0.15, k.num("lift", 0.4), f.z * 0.15);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 윙 커버

    /**
     * 윙 커버 (롤 타릭 W · 쉔 R): 등에서 푸른 불꽃 날개를 펼쳐 겨눈 아군 곁 (range 칸 — 그 앞 1.2칸, 없으면 앞 fallback 칸) 으로 glide 틱 날아들어 내려앉으며 둘레 radius
     * 칸을 내리침 (slam · 조금 밀쳐냄) → 날개가 그 아군을 감싸 shieldFor 틱 보호막 (최대 체력 × shield — 나도). 묶였거나 타고 있으면 그 자리에서
     */
    static boolean wing(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int glide = Math.max(4, (int) k.num("glide", 10));
        double r = k.num("radius", 3);
        ServerPlayer ally = JobTraits4.aimAlly(sp, k.num("range", 12));
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 land;
        if (ally != null) {
            double dist = V.hdist(ally.position(), c0);
            Vec3 u = dist > 0.1 ? V.flat(V.sub(ally.position(), c0)) : f;
            land = Kit.standSpot(level0, V.up(V.add(c0, u, Math.max(0, dist - 1.2)), 0.6), 2.5);
            if (land == null) land = Kit.standSpot(level0, V.up(ally.position(), 0.6), 2.5);
        } else {
            double can = RuneKit.dashDistance(sp, f, k.num("fallback", 5), 0.3, 1.5);
            land = Kit.standSpot(level0, V.up(V.add(c0, f, can), 0.6), 2.5);
        }
        if (land == null) land = c0;
        Vec3 to = land;
        boolean moving = !Status.isRooted(sp) && !Kit.ridingAny(sp) && V.hdist(to, c0) > 0.4;
        if (moving) JobMove.run(sp, JobMove.hop(0, glide, JobMove.at(to), JobMove.hopPeak(sp, c0, to, 1.3)));
        RpgCombat.setSteadfast(sp, glide + 2);
        Kit.noFall(sp, glide + 20);
        // 날아오름: 등의 불꽃 날개 (매 틱 다시 그림 — 날갯짓) · 발밑 푸른 고리 · 지나간 자리의 불꽃 깃털
        JobFx15.halo(level0, V.up(c0, 0.06), 1.4, 0.5, Opt.of("alpha", 0.8), AZURE, AZURE_CORE);
        SkillFx.sparks(level0, V.up(c0, 0.3), AZURE_CORE, 10, 4);
        SkillFx.play(level0, "entity.ender_dragon.flap", c0, 1.3, 0.8);
        SkillFx.play(level0, "item.firecharge.use", c0, 1.1, 0.5);
        Vec3[] last = {c0};
        Vec3[] prev = {c0};
        for (int t = 0; t < glide; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 p = sp.position();
                // 몸을 따라감: JobMove 는 자리를 옮겨 놓아 몸의 속도 (bodyVel) 가 0 — 지난 틱에 옮긴 만큼 (위아래 포함) 을 속도로, 날갯짓은 이어지게 (phase). 2026-11-06 영상
                // 검토: 2틱마다 몸의 속도로 그리니 앞 그림이 처진 자리에 남아 날개가 둘로 보였음
                Vec3 vel = V.scale(V.sub(p, prev[0]), 20);
                prev[0] = p;
                JobFx15.fwings(lv, back(sp), 3.4, tt == 0 ? 1 : 2, tt == 0 ? 1 : 4, 0.2, tt != 0, JobFx.hide1p(sp).with("vel", vel, "solo", WING_SOLO, "fade", 0.05,
                        "fps", 16.0, "phase", (tt * 16 / 20.0) % 4), AZURE, AZURE_CORE);
                if (tt % 2 != 0) return;
                if (V.len(V.sub(p, last[0])) > 0.3) {
                    JobFx.line(lv, V.up(last[0], 1.1), V.up(p, 1.1), AZURE, AZURE_CORE, 0.35, Opt.of("alpha", 0.75));
                    JobFx.feathers(lv, V.up(last[0], 1.2), 2, 0.4, 0.28, j, 0.9, Opt.empty());
                }
                last[0] = p;
            });
        }
        RuneKit.later(glide, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 g = ThunderlordSkills.ground(lv, sp.position());
            // 내려앉음: 접히는 날개 · 땅을 내리치는 충격 · 푸른 불꽃 고리 · 깃털
            JobFx15.fwings(lv, back(sp), 3.4, 6, 2, 0.35, false, JobFx.hide1p(sp).with("solo", WING_SOLO), AZURE, AZURE_CORE);
            JobFx.smash(lv, V.up(g, 0.05), r * 0.9, j, 0.45, Opt.empty());
            JobFx15.halo(lv, V.up(g, 0.06), r, 0.6, Opt.of("alpha", 0.9), AZURE, AZURE_CORE);
            RuneKit.novaRing(lv, g, 0.6, r / 0.6 - 1, AZURE, AZURE_CORE, Opt.of("life", 0.4));
            JobFx.feathers(lv, V.up(g, 1.4), 8, 1.2, 0.32, j, 1.2, Opt.empty());
            SkillFx.play(lv, "item.mace.smash_ground", g, 1.1, 0.8);
            SkillFx.play(lv, "entity.blaze.shoot", g, 1.0, 0.7);
            for (LivingEntity e : Kit.around(sp, g, r, 3)) {
                if (!e.isAlive() || e.isRemoved()) continue;
                JobKit.strike(sp, e, j, P, k.num("slam", 2.4), false, false, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 1.2);
                    if (JobKit.pushable(e)) RuneKit.knock(e, V.flat(V.sub(e.position(), g)), k.num("knock", 0.5), 0.2);
                });
            }
            // 감쌈: 그 아군 (곁에 있으면) 과 나 — 보호막 · 감싸는 날개
            int ticks = (int) k.num("shieldFor", 80);
            double sk = k.num("shield", 0.12);
            wingShield(sp, sp, sk, ticks);
            if (ally != null && !ally.isRemoved() && ally.isAlive() && ally.level() == lv && ally.distanceTo(sp) <= 6) {
                wingShield(ally, sp, sk, ticks);
                JobFx.beam(lv, JobTraits15.chest(sp), JobTraits15.chest(ally), AZURE, AZURE_CORE, 1.0, 0.4, Opt.of("alpha", 0.8));
                SkillFx.play(lv, "block.beacon.power_select", ally.position(), 1.6, 0.7);
                JobSystem.note(sp, "§b윙 커버 §7— " + ally.getName().getString() + " 보호막");
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 블루 인페르노

    /**
     * 블루 인페르노 (롤 갈리오 W · 브랜드 W): 방패를 높이 들어 둘레의 불꽃을 모았다가 (riseAt 틱) 땅에 박으면 둘레 radius 칸에 푸른 불꽃 고리가 솟음 (rise · taunt 틱 도발)
     * → 불꽃 날개 둘이 고리를 따라 한 바퀴 휘몰아친 뒤 burstAt 틱 바깥으로 터짐 (burst · 밀쳐냄 knock)
     */
    static boolean inferno(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int riseAt = Math.max(4, (int) k.num("riseAt", 10));
        int burstAt = Math.max(riseAt + 10, (int) k.num("burstAt", 30));
        double r = k.num("radius", 5);
        RpgCombat.setSteadfast(sp, burstAt + 2);
        Vec3 c0 = sp.position();
        // 치켜듦: 방패로 모이는 불 (둘레 땅에서 푸른 불줄기가 방패로) · 방패 위 작은 날개
        JobFx.charge(sp, j, new Vec3(0.0, 2.6, 0.2), riseAt, 0.9);
        JobFx15.fwings(level0, overhead(sp), 2.2, 0, 2, riseAt / 20.0 + 0.05, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.8), AZURE,
                AZURE_CORE);
        for (int t = 0; t < riseAt; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 top = overhead(sp);
                for (int i = 0; i < 3; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 from = V.up(V.add(sp.position(), new Vec3(Math.cos(a), 0, Math.sin(a)), r * (0.6 + Kit.rand() * 0.4)), 0.2);
                    JobFx.line(lv, from, top, AZURE, AZURE_CORE, 0.18, Opt.of("alpha", 0.8));
                }
            });
        }
        SkillFx.play(level0, "block.respawn_anchor.charge", c0, 1.2, 0.8);
        SkillFx.play(level0, "entity.blaze.ambient", c0, 0.8, 0.8);
        Vec3[] mid = {null};
        RuneKit.later(riseAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 g = ThunderlordSkills.ground(lv, sp.position());
            mid[0] = g;
            int hold = burstAt - riseAt;
            // 솟음: 방패가 땅을 침 · 고리를 따라 솟는 불꽃 기둥 열둘 · 땅의 푸른 고리 · 금
            JobFx.smash(lv, V.up(g, 0.05), 1.8, j, 0.45, Opt.empty());
            JobFx15.halo(lv, V.up(g, 0.06), r, hold / 20.0 + 0.3, Opt.of("alpha", 0.95, "fade", 0.1), AZURE, AZURE_CORE);
            JobFx15.halo(lv, V.up(g, 0.07), r * 0.55, hold / 20.0 + 0.3, Opt.of("alpha", 0.6, "fade", 0.1), DEEP, DEEP_CORE);
            RuneKit.glowCrack(lv, V.up(g, 0.04), r * 0.9, AZURE, AZURE_CORE, Opt.of("life", hold / 20.0 + 0.3));
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI * 2 / 12;
                Vec3 p = ThunderlordSkills.ground(lv, V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), r * 0.94));
                JobFx15.cinder(lv, p, 2.8, 0, 2, 0.12, false, Opt.of("alpha", 0.95), AZURE, AZURE_CORE);
                RuneKit.later(2, () -> JobFx15.cinder(lv, p, 2.8, 2, 4, (hold - 2) / 20.0, true, Opt.of("alpha", 0.9, "fade", 0.05), AZURE, AZURE_CORE));
            }
            Ult.flash(lv, g, AZURE, Opt.of("radius", 5.0, "time", new double[] {0.02, 0.05, 0.3}), sp);
            SkillFx.play(lv, "item.mace.smash_ground_heavy", g, 1.0, 0.8);
            SkillFx.play(lv, "entity.blaze.shoot", g, 0.8, 0.9);
            SkillFx.play(lv, "item.firecharge.use", g, 0.7, 0.9);
            for (LivingEntity e : Kit.around(sp, g, r, 4)) {
                if (!e.isAlive() || e.isRemoved()) continue;
                JobTraits15.taunt(sp, e, (int) k.num("taunt", 40));
                JobKit.strike(sp, e, j, P, k.num("rise", 4.8), false, false, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 1.4);
                    if (e.level() instanceof ServerLevel el) JobFx15.cinder(el, ThunderlordSkills.ground(el, e.position()), 2.0 + Kit.bodyPad(e), 0, 6, 0.5, false,
                            Opt.of("alpha", 0.9), AZURE, AZURE_CORE);
                });
            }
        });
        // 휘몰아침: 불꽃 날개 둘이 고리를 따라 한 바퀴 (2틱마다 다시 그림 — 주인마다 하나씩)
        for (int t = riseAt + 2; t < burstAt; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life) || mid[0] == null) return;
                ServerLevel lv = sp.level();
                Vec3 g = mid[0];
                double u = (tt - riseAt) / (double) (burstAt - riseAt);
                for (int w = 0; w < 2; w++) {
                    double a = u * Math.PI * 2 + w * Math.PI;
                    Vec3 rad = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 p = V.up(V.add(g, rad, r * 0.72), 1.5);
                    double sp2 = r * 0.72 * Math.PI * 2 / ((burstAt - riseAt) / 20.0);
                    Vec3 tan = new Vec3(-Math.sin(a) * sp2, 0, Math.cos(a) * sp2);
                    JobFx15.fwings(lv, p, 3.2, 2, 4, 0.2, true, Opt.of("vel", tan, "solo", SWEEP_SOLO + w, "owner", (double) sp.getId(), "fade", 0.05, "fps", 16.0,
                            "alpha", 0.95), AZURE, AZURE_CORE);
                    SkillFx.fx(lv, "rpg:arrow_spark", V.up(p, -0.6), Opt.of("dir", V.UP, "speed", 1.3, "size", 0.14, "grow", 0.8, "life", 0.4, "color", AZURE, "alpha",
                            0.95));
                }
                if ((tt - riseAt) % 6 == 0) SkillFx.play(lv, "entity.ender_dragon.flap", g, 1.5, 0.45);
            });
        }
        JobKit.motionLater(sp, "phoenix_inferno:burst", burstAt - 3, life);
        RuneKit.later(burstAt, () -> {
            if (!RuneKit.sameLife(sp, life) || mid[0] == null) return;
            ServerLevel lv = sp.level();
            Vec3 g = mid[0];
            // 터짐: 기둥이 무너지며 바깥으로 · 날개가 불꽃 깃털로 · 푸른 충격 고리 둘 · 깃털 비
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI * 2 / 12;
                Vec3 p = ThunderlordSkills.ground(lv, V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), r * 0.94));
                JobFx15.cinder(lv, p, 3.2, 6, 2, 0.45, false, Opt.of("alpha", 0.95), AZURE, AZURE_CORE);
            }
            for (int w = 0; w < 2; w++) {
                double a = Math.PI * 2 + w * Math.PI;
                Vec3 p = V.up(V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), r * 0.72), 1.5);
                JobFx15.fwings(lv, p, 3.4, 7, 1, 0.45, false, Opt.of("solo", SWEEP_SOLO + w, "owner", (double) sp.getId(), "alpha", 0.95), AZURE, AZURE_CORE);
            }
            JobFx15.fwings(lv, V.up(g, 1.6), r * 1.8, 7, 1, 0.55, false, Opt.of("alpha", 0.9), AZURE, AZURE_CORE);
            RuneKit.novaRing(lv, g, 1.0, r * 1.5 - 1, AZURE, AZURE_CORE, Opt.of("life", 0.45));
            RuneKit.later(2, () -> RuneKit.novaRing(lv, g, 0.8, r * 1.2 / 0.8 - 1, DEEP, DEEP_CORE, Opt.of("life", 0.4)));
            JobFx.burst(lv, V.up(g, 1.0), 2.4, AZURE, AZURE_CORE, 0.35, Opt.empty());
            JobFx.feathers(lv, V.up(g, 1.8), 16, r * 0.7, 0.36, j, 1.5, Opt.empty());
            JobFx.radialWaves(sp, j, g, 8, 1.0, r + 1.5, 0.9, 1.4, 0.3, 15);
            Ult.flash(lv, g, AZURE, Opt.of("radius", 6.0, "time", new double[] {0.02, 0.05, 0.3}), sp);
            SkillFx.play(lv, "entity.generic.explode", g, 1.2, 0.8);
            SkillFx.play(lv, "entity.blaze.shoot", g, 0.7, 1.0);
            for (LivingEntity e : Kit.around(sp, g, r, 4)) {
                if (!e.isAlive() || e.isRemoved()) continue;
                JobKit.strike(sp, e, j, P, k.num("burst", 2.4), false, false, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 1.5);
                    if (JobKit.pushable(e)) RuneKit.knock(e, V.flat(V.sub(e.position(), g)), k.num("knock", 0.9), 0.35);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 이그니션

    /**
     * 이그니션 (롤 람머스 W): dur 틱 태세 (강화 지속) — 등에 푸른 불꽃 날개가 활짝 펼쳐짐 (JobTraits15.stanceFx): 받는 피해 − cut · 밀려나지 않음 · 맞거나 막을 때마다
     * (every 틱에 한 번) 둘레 radius 칸 적에게 불꽃이 터짐 (위력 × burst) · 불사조의 재 × ash (JobTraits15)
     */
    static boolean ignition(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) JobUpgrades.num(sp, k, "dur", 160);
        JobTraits.startStance(sp, k, dur);
        RpgCombat.setSteadfast(sp, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 g = ThunderlordSkills.ground(level, c);
        // 불붙음: 등의 날개가 펼쳐짐 · 발밑에서 솟는 푸른 불꽃 기둥 · 고리 · 불티
        JobFx15.fwings(level, back(sp), 3.6, 0, 2, 0.35, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "solo", 92.0), AZURE, AZURE_CORE);
        JobFx15.cinder(level, g, 3.6, 0, 3, 0.5, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), AZURE, AZURE_CORE);
        RuneKit.novaRing(level, c, 0.6, 3.5, AZURE, AZURE_CORE, Opt.of("life", 0.45));
        JobFx15.halo(level, V.up(g, 0.06), 2.4, 0.7, Opt.of("alpha", 0.85), AZURE, AZURE_CORE);
        JobFx.burst(level, V.up(c, 1.2), 2.0, AZURE, AZURE_CORE, 0.35, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
        JobFx.aura(sp, j, 2.8, 1.0, 0.85);
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.45);
        SkillFx.play(level, "entity.ender_dragon.flap", c, 1.2, 0.8);
        SkillFx.play(level, "entity.blaze.ambient", c, 0.7, 0.9);
        SkillFx.play(level, "block.beacon.activate", c, 1.3, 0.7);
        Notify.notify(sp, "§b§l" + k.name() + " §7— 받는 피해 −" + Math.round(k.num("cut", 0.2) * 100) + "% · 밀려나지 않음 · 맞으면 불꽃 · 불사조의 재 × "
                + Math.round(k.num("ash", 2)) + " · " + dur / 20 + "초", 40);
        return true;
    }

    // ------------------------------------------------------------ 피닉스 디센트

    /** 디센트의 불사조가 내리꽂힐 자리 (겨눈 적 — 살아 있으면 지금 자리 · 아니면 정해 둔 땅) */
    private static Vec3 diveSpot(ServerLevel lv, LivingEntity target, Vec3 fixed) {
        if (target != null && target.isAlive() && !target.isRemoved() && target.level() == lv) return ThunderlordSkills.ground(lv, target.position());
        return fixed;
    }

    /**
     * 피닉스 디센트 (롤 애니비아 · 갈리오 R): 푸른 불사조의 하늘 (rpg:ult_phoenix) — 방패를 하늘로 치켜들면 pillarAt 틱에 푸른 불기둥이 솟아 둘레 pillarRadius 칸 적을 침
     * (pillar) · 둘레 tauntRadius 칸 taunt 틱 도발 → 불기둥 꼭대기에서 날개 끝 10.5칸의 거대한 푸른 불사조가 깨어나 하늘을 돌다 diveAt 틱에 겨눈 곳 (range 칸 — 겨눈 적 · 없으면
     * 앞 fallback 칸) 에 내리꽂힘 (둘레 diveRadius 칸 dive · 큰 숫자 · 띄움) → 펼친 날개가 땅을 덮으며 둘레 tauntRadius 칸 아군 (나 포함) shieldFor 틱 보호막 (최대 체력 ×
     * shield)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int pillarAt = Math.max(4, (int) k.num("pillarAt", 10));
        int diveAt = Math.max(pillarAt + 16, (int) k.num("diveAt", 40));
        double tauntR = k.num("tauntRadius", 8);
        int taunt = (int) k.num("taunt", 60);
        Vec3 c0 = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        Ult.sky(level0, c0, "rpg:ult_phoenix", diveAt + 30);
        RpgCombat.setSteadfast(sp, diveAt + 4);
        JobMove.run(sp, JobMove.hold(0, pillarAt + 4));
        // 겨눈 곳: 겨눈 적 (range 칸) — 없으면 앞 fallback 칸 땅
        LivingEntity target = Kit.pickTarget(sp, k.num("range", 14), 22);
        double can = RuneKit.dashDistance(sp, f0, k.num("fallback", 6), 0.3, 1.5);
        Vec3 fixed = ThunderlordSkills.ground(level0, V.add(c0, f0, can));
        // 치켜듦: 방패로 모이는 하늘의 푸른 불 · 발밑 고리
        JobFx.charge(sp, j, new Vec3(0.0, 2.8, 0.15), pillarAt, 1.0);
        JobFx15.halo(level0, V.up(c0, 0.06), 2.4, pillarAt / 20.0 + 0.3, Opt.of("alpha", 0.8, "fade", 0.1), AZURE, AZURE_CORE);
        for (int i = 0; i < 6; i++) {
            int ii = i;
            RuneKit.later(i, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double a = Math.toRadians(ii * 60 + 15);
                Vec3 sky = V.up(V.add(sp.position(), new Vec3(Math.cos(a), 0, Math.sin(a)), 4.5), 8.0);
                JobFx.line(sp.level(), sky, overhead(sp), AZURE, AZURE_CORE, 0.35, Opt.of("alpha", 0.9));
            });
        }
        SkillFx.play(level0, "block.beacon.power_select", c0, 1.2, 0.7);
        SkillFx.play(level0, "entity.blaze.ambient", c0, 0.8, 0.6);
        Vec3[] base = {null};
        Vec3[] bird = {null};
        RuneKit.later(pillarAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 g = ThunderlordSkills.ground(lv, sp.position());
            base[0] = g;
            double pr = k.num("pillarRadius", 6);
            // 불기둥: 가운데 큰 기둥 · 둘레 기둥 여섯 · 땅의 고리 · 금 · 번쩍
            JobFx15.cinder(lv, g, 9.0, 0, 2, 0.15, false, Opt.of("alpha", 1.0), AZURE, AZURE_CORE);
            RuneKit.later(3, () -> JobFx15.cinder(lv, g, 9.0, 2, 4, 0.9, true, Opt.of("alpha", 0.95, "fade", 0.2), AZURE, AZURE_CORE));
            RuneKit.later(21, () -> JobFx15.cinder(lv, g, 9.0, 6, 2, 0.5, false, Opt.of("alpha", 0.9), AZURE, AZURE_CORE));
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                Vec3 p = ThunderlordSkills.ground(lv, V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), 3.2));
                JobFx15.cinder(lv, p, 4.5, 0, 8, 0.9, false, Opt.of("alpha", 0.9), DEEP, DEEP_CORE);
            }
            JobFx15.halo(lv, V.up(g, 0.06), pr, 1.0, Opt.of("alpha", 0.95), AZURE, AZURE_CORE);
            RuneKit.novaRing(lv, g, 1.0, pr * 1.3 - 1, AZURE, AZURE_CORE, Opt.of("life", 0.5));
            RuneKit.glowCrack(lv, V.up(g, 0.04), pr, AZURE, AZURE_CORE, Opt.of("life", 1.2));
            Ult.flash(lv, g, AZURE, Opt.of("radius", 7.0, "time", new double[] {0.02, 0.06, 0.35}), sp);
            SkillFx.play(lv, "entity.blaze.shoot", g, 0.6, 1.0);
            SkillFx.play(lv, "item.firecharge.use", g, 0.5, 1.0);
            SkillFx.play(lv, "entity.generic.explode", g, 0.7, 0.8);
            for (LivingEntity e : Kit.around(sp, g, tauntR, 5)) {
                if (!e.isAlive() || e.isRemoved()) continue;
                JobTraits15.taunt(sp, e, taunt);
                if (V.hdist(e.position(), g) > pr + Kit.bodyPad(e)) continue;
                JobKit.strike(sp, e, j, P, k.num("pillar", 8), false, false, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 1.6);
                    if (JobKit.pushable(e)) Kit.launch(e, 0, 0.35, 0);
                });
            }
        });
        // 깨어남: 불기둥 꼭대기에 불사조가 생겨남 → 날개를 활짝 (울음). 2026-11-06 영상 검토: 가슴 높이 9.5칸 · 날개 끝 12칸 · 반지름 3.5칸으로 돌면 카메라 쪽으로 올 때
        // 볏 · 날개가 화면 위로 잘렸음 → 8칸 · 10.5칸 · 3칸
        int wake = pillarAt + 3;
        int circleFrom = pillarAt + 8;
        int diveFrom = diveAt - 6;
        RuneKit.later(wake, () -> {
            if (!RuneKit.sameLife(sp, life) || base[0] == null) return;
            ServerLevel lv = sp.level();
            Vec3 top = V.up(base[0], BIRD_UP);
            bird[0] = top;
            JobFx15.phoenix(lv, top, BIRD_SPAN, 0, 2, (circleFrom - wake) / 20.0 + 0.05, false, Opt.of("solo", BIRD_SOLO, "owner", (double) sp.getId(), "fade", 0.05),
                    AZURE, AZURE_CORE);
            JobFx.feathers(lv, top, 14, 4.0, 0.5, j, 1.8, Opt.empty());
            SkillFx.play(lv, "entity.ender_dragon.growl", top, 1.6, 0.8);
            SkillFx.play(lv, "entity.ender_dragon.flap", top, 1.0, 1.0);
        });
        // 하늘을 돎: 불기둥 위 반지름 3칸 (2틱마다 다시 그림 — 날갯짓은 이어지게 (phase) · 떨어지는 불꽃 깃털)
        for (int t = circleFrom; t < diveFrom; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life) || base[0] == null) return;
                ServerLevel lv = sp.level();
                double u = (tt - circleFrom) / (double) Math.max(1, diveFrom - circleFrom);
                double a = u * Math.PI * 2 + Math.atan2(f0.z, f0.x) + Math.PI;
                double rr = 3.0;
                Vec3 p = V.up(V.add(base[0], new Vec3(Math.cos(a), 0, Math.sin(a)), rr), BIRD_UP + Math.sin(u * Math.PI * 2) * 0.6);
                double w = Math.PI * 2 / ((diveFrom - circleFrom) / 20.0);
                Vec3 tan = new Vec3(-Math.sin(a) * rr * w, 0, Math.cos(a) * rr * w);
                bird[0] = p;
                JobFx15.phoenix(lv, p, BIRD_SPAN, 2, 3, 0.25, true, Opt.of("vel", tan, "solo", BIRD_SOLO, "owner", (double) sp.getId(), "fade", 0.05, "fps", 10.0,
                        "phase", ((tt - circleFrom) * 10 / 20.0) % 3), AZURE, AZURE_CORE);
                if ((tt - circleFrom) % 4 == 0) JobFx.feathers(lv, V.up(p, -1.0), 3, 2.5, 0.42, j, 1.4, Opt.empty());
                if ((tt - circleFrom) % 8 == 0) SkillFx.play(lv, "entity.ender_dragon.flap", p, 1.4, 0.9);
            });
        }
        // 내리꽂음: 머리를 아래로 · 지나간 하늘에 푸른 불줄기 (매 틱 다시 그림)
        Vec3[] spot = {null};
        for (int t = diveFrom; t < diveAt; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life) || base[0] == null) return;
                ServerLevel lv = sp.level();
                if (spot[0] == null) {
                    spot[0] = diveSpot(lv, target, fixed);
                    SkillFx.play(lv, "entity.ender_dragon.growl", bird[0] != null ? bird[0] : spot[0], 1.4, 1.0);
                    SkillFx.play(lv, "entity.phantom.swoop", spot[0], 1.2, 1.0);
                    // 떨어질 땅에 미리 푸른 고리 (어디에 꽂힐지)
                    JobFx15.halo(lv, V.up(spot[0], 0.06), k.num("diveRadius", 6), (diveAt - tt) / 20.0 + 0.15, Opt.of("alpha", 0.8, "fade", 0.1), DEEP, DEEP_CORE);
                }
                Vec3 from = bird[0] != null ? bird[0] : V.up(base[0], BIRD_UP);
                Vec3 end = V.up(spot[0], 2.8);
                double u = (tt - diveFrom + 1) / (double) (diveAt - diveFrom);
                Vec3 p = V.add(from, V.scale(V.sub(end, from), u * u));
                Vec3 prev = V.add(from, V.scale(V.sub(end, from), Math.max(0, (tt - diveFrom) / (double) (diveAt - diveFrom))
                        * Math.max(0, (tt - diveFrom) / (double) (diveAt - diveFrom))));
                JobFx15.phoenix(lv, p, BIRD_SPAN - 0.5, 5, 1, 0.1, false, Opt.of("solo", BIRD_SOLO, "owner", (double) sp.getId(), "fade", 0.0), AZURE, AZURE_CORE);
                JobFx.line(lv, V.up(prev, 1.5), V.up(p, 1.0), AZURE, AZURE_CORE, 0.4, Opt.of("alpha", 0.95));
                JobFx.line(lv, V.add(V.up(prev, 2.5), V.rightOf(f0), 1.4), V.add(V.up(p, 1.5), V.rightOf(f0), 1.0), DEEP, DEEP_CORE, 0.35, Opt.of("alpha", 0.8));
                JobFx.line(lv, V.add(V.up(prev, 2.5), V.rightOf(f0), -1.4), V.add(V.up(p, 1.5), V.rightOf(f0), -1.0), DEEP, DEEP_CORE, 0.35, Opt.of("alpha", 0.8));
            });
        }
        JobKit.motionLater(sp, "phoenix_ult:dive", diveFrom - 2, life);
        RuneKit.later(diveAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 g = spot[0] != null ? spot[0] : diveSpot(lv, target, fixed);
            double dr = k.num("diveRadius", 6);
            // 땅에 닿음: 불사조가 날개를 펼치며 크게 터짐 → 깃털로 흩어짐 · 둘레 불꽃 기둥 · 고리 · 금 · 깃털 비
            JobFx15.phoenix(lv, V.up(g, 3.8), BIRD_SPAN + 1.5, 6, 1, 0.25, false, Opt.of("solo", BIRD_SOLO, "owner", (double) sp.getId(), "fade", 0.0), AZURE,
                    AZURE_CORE);
            RuneKit.later(5, () -> JobFx15.phoenix(lv, V.up(g, 3.8), BIRD_SPAN + 1.5, 7, 1, 0.7, false, Opt.of("solo", BIRD_SOLO, "owner", (double) sp.getId(), "fade",
                    0.4), AZURE, AZURE_CORE));
            JobFx.smash(lv, V.up(g, 0.05), dr, j, 0.6, Opt.empty());
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 + Math.PI / 8;
                Vec3 p = ThunderlordSkills.ground(lv, V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), dr * 0.7));
                JobFx15.cinder(lv, p, 4.0, 0, 8, 1.0, false, Opt.of("alpha", 0.9), AZURE, AZURE_CORE);
            }
            JobFx15.halo(lv, V.up(g, 0.07), dr, 1.0, Opt.of("alpha", 1.0), AZURE, AZURE_CORE);
            RuneKit.novaRing(lv, g, 1.0, dr * 1.6 - 1, AZURE, AZURE_CORE, Opt.of("life", 0.55));
            RuneKit.later(2, () -> RuneKit.novaRing(lv, g, 0.8, dr * 1.3 / 0.8 - 1, DEEP, DEEP_CORE, Opt.of("life", 0.5)));
            RuneKit.glowCrack(lv, V.up(g, 0.04), dr * 1.1, AZURE, AZURE_CORE, Opt.of("life", 1.2));
            JobFx.burst(lv, V.up(g, 1.2), 3.4, AZURE, AZURE_CORE, 0.45, Opt.empty());
            JobFx.feathers(lv, V.up(g, 2.5), 24, dr * 0.8, 0.48, j, 2.0, Opt.empty());
            JobFx.radialWaves(sp, j, g, 10, 1.0, dr + 2, 1.0, 1.6, 0.35, 15);
            SkillFx.sparks(lv, V.up(g, 0.6), AZURE_CORE, 24, 9);
            Ult.flash(lv, g, AZURE, Opt.of("radius", 9.0, "time", new double[] {0.02, 0.07, 0.45}), sp);
            SkillFx.play(lv, "entity.generic.explode", g, 0.8, 1.0);
            SkillFx.play(lv, "item.mace.smash_ground_heavy", g, 0.7, 1.0);
            SkillFx.play(lv, "entity.ender_dragon.growl", g, 1.8, 0.6);
            for (LivingEntity e : Kit.around(sp, g, dr, 5)) {
                if (!e.isAlive() || e.isRemoved()) continue;
                JobKit.strike(sp, e, j, P, k.num("dive", 24), false, true, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 2.6);
                    if (JobKit.pushable(e)) Kit.launch(e, 0, 0.55, 0);
                });
            }
            // 펼친 날개가 땅을 덮음: 둘레 tauntRadius 칸 아군 (나 포함 — 멀리 있어도)
            int ticks = (int) k.num("shieldFor", 80);
            double sk = k.num("shield", 0.15);
            List<ServerPlayer> got = new ArrayList<>();
            for (ServerPlayer o : JobTraits4.allies(sp, g, tauntR)) {
                wingShield(o, sp, sk, ticks);
                got.add(o);
            }
            if (!got.contains(sp)) wingShield(sp, sp, sk, ticks);
            if (got.size() > 1 || (got.size() == 1 && !got.contains(sp))) JobSystem.note(sp, "§b피닉스 디센트 §7— 아군 " + (got.contains(sp) ? got.size() - 1 : got.size())
                    + " 보호막");
        });
        return true;
    }
}
