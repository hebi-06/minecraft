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
 * 2026-11-06 수호기사 각성 센티널 (재설계안 2판 — 사용자 "다음 작업", jobs.json advance sentinel — 강철 하늘빛 · 진주빛 · 무지개, 특성 「반격」 + 파수꾼의 위압 —
 * JobTraits4 · JobTraits15): 1차와 같은 막고 버티는 탱커 — 배스천 차지 · 헤이븐 리프 · 레인보우 램파트 · 나인 타이즈 · 클라리온 콜. 몸 쓰는 직업이라 제 몸 · 방패 동작만
 * (장벽 · 피난처 · 뿔피리 · 물결은 그림). 단타는 크게 감았다가 큰 타격, 화면은 흔들지 않음.
 */
final class SentinelSkills {
    private SentinelSkills() {}

    static final double[] SKY = JobTraits15.SKY;
    static final double[] SKY_CORE = JobTraits15.SKY_CORE;
    static final double[] GOLD = JobTraits15.GOLD;
    static final double[] GOLD_CORE = JobTraits15.GOLD_CORE;
    static final double[] SEA = JobTraits15.SEA;
    static final double[] SEA_CORE = JobTraits15.SEA_CORE;

    static void register() {
        JobSystem.register("sentinel_charge", SentinelSkills::charge);
        JobSystem.register("sentinel_haven", SentinelSkills::haven);
        JobSystem.register("sentinel_rampart", SentinelSkills::rampart);
        JobSystem.register("sentinel_tides", SentinelSkills::tides);
        JobSystem.register("sentinel_ult", SentinelSkills::ult);
    }

    /** 방패 앞 (가슴 높이 · 앞 ahead 칸) */
    private static Vec3 shieldFront(ServerPlayer sp, double ahead) {
        return V.up(V.add(sp.position(), Kit.facingOf(sp), ahead), 1.1);
    }

    /** 앞 reach 칸 · arc° 의 가장 가까운 적 (없으면 null) */
    private static LivingEntity nearestFront(ServerPlayer sp, double reach, double arc) {
        List<LivingEntity> cone = RuneKit.coneTargets(sp, reach, arc, 4);
        return cone.isEmpty() ? null : cone.get(0);
    }

    /** 헤이븐 리프 돔의 solo 번호 (주인마다 하나 — 막을 때 다시 그리면 앞 돔은 다음 틱에 사라짐) */
    static final double HAVEN_SOLO = 86.0;

    /**
     * 피난처 돔 (솟은 뒤 일렁임 — life 초). 돔은 카메라를 보는 판 하나 (가운데를 지남) 라 먼저 그린 돔이 그 뒤에 나중에 생긴 그림을 깊이로 가렸음 (영상 3: 피난처가 막은
     * 평타의 프리즘 · 빛 터짐이 돔 가운데 뒤쪽이면 보이지 않았음) → solo 로 두고 막을 때 (JobTraits15.havenReduce) 그 그림들 뒤에 다시 그림
     */
    static void havenDome(ServerPlayer sp, ServerLevel lv, Vec3 g, double r, double life) {
        JobFx15.haven(lv, g, r, 2, 4, Math.max(0.1, life), true, JobFx.hide1p(sp).with("fade", 0.05, "solo", HAVEN_SOLO), SKY, SKY_CORE);
    }

    /** 무지개 줄 (a → b — 다섯 빛을 위아래로 겹친 무지개 다리 줄기 · 땅을 따라 달린 길) */
    static void rainbowTrail(ServerLevel level, Vec3 a, Vec3 b, double life, Opt o) {
        JobFx.bifrost(level, a, b, 0.11, life, o);
    }

    /**
     * 무지개 다리 (뛰는 길 — from 에서 to 로 높이 peak 칸 포물선, RuneKit.leapPoint · JobMove.hop 과 같은 길): 길이를 따라 약 2칸마다 무지개 줄기 조각 하나 (길의 접선 쪽으로
     * 눕힘 · 다섯 빛은 접선에 수직으로 겹침 — 가파른 곳에서도 띠가 펼쳐짐). 영상 1: 꺾은선 네 토막에 2.5칸 조각을 하나씩 두었더니 짧은 토막의 조각이 꺾인 곳 너머로 삐져나와
     * 꼭대기에서 X 로 엇갈렸고 가파른 토막은 다섯 빛이 겹쳐 붉은 줄 하나로 보였음. 조각 수명은 life (처음) → lifeEnd (끝) — 다리가 처음부터 사라짐
     */
    static void rainbowArc(ServerLevel level, Vec3 from, Vec3 to, double peak, double life, double lifeEnd, Opt o) {
        int N = 48;
        Vec3[] pts = new Vec3[N + 1];
        double[] acc = new double[N + 1];
        for (int i = 0; i <= N; i++) {
            pts[i] = V.up(RuneKit.leapPoint(from, to, peak, i / (double) N), 0.45);
            if (i > 0) acc[i] = acc[i - 1] + V.len(V.sub(pts[i], pts[i - 1]));
        }
        double total = acc[N];
        if (total < 0.5) return;
        int n = Math.max(1, (int) Math.round(total / 2.0));
        double step = total / n;
        double size = Math.min(1.25, step * 0.62); // 조각 길이 = 반 크기 × 2 ≈ 간격 × 1.24 (끝이 옅어지는 그림이라 조금 겹침)
        int k = 0;
        for (int s = 0; s < n; s++) {
            double want = step * (s + 0.5);
            while (k < N - 1 && acc[k + 1] < want) k++;
            double seg = acc[k + 1] - acc[k];
            double a = seg > 1e-6 ? Math.max(0, Math.min(1, (want - acc[k]) / seg)) : 0;
            Vec3 mid = V.add(pts[k], V.scale(V.sub(pts[k + 1], pts[k]), a));
            Vec3 t = V.unit(V.sub(pts[k + 1], pts[k]));
            // 다섯 빛을 겹치는 쪽: 접선에 수직인 위쪽 + 옆쪽 (비스듬히) — 옆에서 봐도 · 길 뒤에서 봐도 (3인칭 카메라 · 영상 2: 위쪽만이면 멀어지는 길을 뒤에서 볼 때 다섯 빛이 한 줄로 겹쳐 붉은 줄로 보였음) 띠가 펼쳐짐
            Vec3 up = V.sub(V.UP, V.scale(t, V.dot(V.UP, t)));
            up = V.len(up) < 1e-3 ? V.UP : V.unit(up);
            Vec3 flat = V.flat(t);
            Vec3 side = V.unit(V.add(up, V.rightOf(flat)));
            double lf = life + (lifeEnd - life) * (s + 0.5) / n;
            for (int i = 0; i < JobFx.RAINBOW.length; i++) {
                double[] c = JobFx.RAINBOW[i];
                double[] core = {c[0] + (1 - c[0]) * 0.6, c[1] + (1 - c[1]) * 0.6, c[2] + (1 - c[2]) * 0.6};
                JobFx.loop(level, "jbifrost", JobFx.BIFROST, V.add(mid, side, (2 - i) * 0.14 * size / 1.25), lf, 12, o.with("dir", t, "size", size, "phase",
                        (double) ((s + i) % JobFx.BIFROST)), c, core);
            }
        }
    }

    // ------------------------------------------------------------ 배스천 차지

    /**
     * 배스천 차지 (롤 뽀삐 E): 방패를 앞으로 세우고 (braceAt 틱 — 방패에 무지개 빛이 맺힘) dashTicks 틱에 앞 dist 칸을 돌진 — 처음 닿은 적 하나를 들이받아 (ram · 도발)
     * 앞에 붙여 밀고 감 → slamAt 틱 방패로 크게 밀어붙임 (slam · 도발): 벽 · 움직이지 않는 것 (보스 · 허수아비 · 큰 몹) 에 박히면 stun 틱 기절 (보스 제외), 아니면 멀리
     * 밀려남 (knock). 닿은 적이 없으면 그때 앞 3칸의 적을 밀어붙임. 묶였거나 타고 있으면 쓰지 않음
     */
    static boolean charge(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int brace = Math.max(1, (int) k.num("braceAt", 4));
        int dash = Math.max(2, (int) k.num("dashTicks", 6));
        int slamAt = Math.max(brace + dash + 2, (int) k.num("slamAt", 16));
        int taunt = (int) k.num("taunt", 80);
        Vec3 f0 = Kit.facingOf(sp);
        Vec3 start = sp.position();
        double can = RuneKit.dashDistance(sp, f0, k.num("dist", 6), 0.3, 1.5);
        Vec3 end = V.add(start, f0, can);
        double step = can / dash;
        RpgCombat.setSteadfast(sp, slamAt + 4);
        // 세움: 앞의 프리즘 방패 · 방패로 모이는 무지개 빛 · 발밑 고리
        JobFx15.prism(level0, shieldFront(sp, 1.0), 0.62, 0, 2, brace / 20.0 + 0.05, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), SKY, SKY_CORE);
        JobFx.charge(sp, j, new Vec3(0.0, 1.1, 0.9), brace, 0.6);
        SkillFx.play(level0, "item.shield.block", start, 0.7, 0.9);
        SkillFx.play(level0, "item.armor.equip_iron", start, 0.8, 0.9);
        if (can > 0.4) JobMove.run(sp, JobMove.hold(0, brace), JobMove.to(brace, brace + dash, JobMove.at(end), JobMove.LINEAR));
        LivingEntity[] held = {null};
        boolean[] walled = {false};
        Vec3[] last = {start};
        for (int t = brace; t <= brace + dash; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 p = sp.position();
                // 달리는 길: 무지개 다리 줄기 · 방패 앞 빛 (2틱마다)
                if (tt > brace && (tt - brace) % 2 == 0) {
                    rainbowTrail(lv, V.up(last[0], 0.2), V.up(p, 0.2), 0.45, JobFx.hide1p(sp));
                    last[0] = p;
                    JobFx15.prism(lv, shieldFront(sp, 0.95), 0.62, 1, 4, 0.14, true, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "solo", 82.0, "fade", 0.1), SKY,
                            SKY_CORE);
                }
                if (walled[0]) return;
                if (held[0] == null) {
                    LivingEntity e = nearestFront(sp, 1.7, 80);
                    if (e == null) return;
                    held[0] = e;
                    JobTraits15.taunt(sp, e, taunt);
                    JobKit.strike(sp, e, j, P, k.num("ram", 1.6), false, false, life, 0, d -> {
                        JobImpact.hit(sp, e, j, 1.3);
                        if (e.level() instanceof ServerLevel el) {
                            JobFx15.prism(el, ThunderlordSkills.inFront(sp, e, 0.2).add(0, e.getBbHeight() * 0.55, 0), 0.55 + Kit.bodyPad(e) * 0.3, 5, 1, 0.22, false,
                                    Opt.of("vel", RuneKit.entVel(e)), SKY, SKY_CORE);
                            SkillFx.play(el, "entity.zombie.attack_iron_door", e.position(), 1.2, 0.6);
                        }
                    });
                    SkillFx.play(lv, "entity.goat.ram_impact", p, 1.1, 0.9);
                    if (!JobKit.pushable(e)) {
                        walled[0] = true; // 움직이지 않는 것: 그 앞에서 멈춤
                        JobMove.cancel(sp);
                    }
                    return;
                }
                // 밀고 감: 닿은 적을 내 앞 (한 걸음 더) 에 붙임 — 막히면 벽에 박힘
                LivingEntity e = held[0];
                if (e.isRemoved() || !e.isAlive()) return;
                Vec3 want = V.add(p, f0, 0.9 + e.getBbWidth() * 0.5 + step);
                if (!Kit.moveTo(e, new Vec3(want.x, e.getY(), want.z))) {
                    walled[0] = true;
                    JobMove.cancel(sp);
                }
                e.resetFallDistance();
            });
        }
        JobKit.motionLater(sp, "sentinel_charge:slam", slamAt - 4, life);
        RuneKit.later(slamAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            LivingEntity e = held[0];
            if (e == null || e.isRemoved() || !e.isAlive()) e = nearestFront(sp, 3.0, 90);
            // 밀어붙임: 눈앞 큰 베기 · 앞으로 무지개 검기 · 방패 번쩍 · 충돌 고리
            JobFx.slash(sp, j, 1.8, 0.35, 2.4, 200.0, 0.24);
            JobFx.bigWave(lv, sp, V.up(V.add(c, f0, 0.8), 1.1), f0, 14, 1.5, 90, 0.32, j, SKY, SKY_CORE, Opt.empty());
            JobFx15.prism(lv, shieldFront(sp, 1.1), 0.75, 5, 3, 0.4, false, JobFx.hide1p(sp), SKY, SKY_CORE);
            JobFx.ringFacing(lv, shieldFront(sp, 1.4), 1.2, j, V.scale(f0, -1), 0.3, Opt.empty());
            SkillFx.play(lv, "item.shield.block", c, 0.6, 1.0);
            SkillFx.play(lv, "entity.player.attack.knockback", c, 0.7, 1.0);
            if (e == null) return;
            LivingEntity t = e;
            JobTraits15.taunt(sp, t, taunt);
            Vec3 b = Kit.bodyPoint(t);
            double pad = Kit.bodyPad(t);
            // 벽에 박힘: 막혔음 · 움직이지 않는 것 · 그 뒤 1칸 안에 블록
            boolean wall = walled[0] || !JobKit.pushable(t) || RuneKit.rayLength(lv, b, f0, 1.4 + pad) < 1.1 + pad;
            JobKit.strike(sp, t, j, P, k.num("slam", 2.4), false, wall, life, 0, d -> {
                JobImpact.hit(sp, t, j, wall ? 2.0 : 1.5);
                if (!(t.level() instanceof ServerLevel el)) return;
                if (wall) {
                    JobTraits4.stun(t, j, (int) k.num("stun", 20));
                    Vec3 back = V.add(b, f0, 0.5 + pad);
                    JobFx15.prism(el, back, 0.9 + pad * 0.4, 5, 3, 0.45, false, Opt.empty(), SKY, SKY_CORE);
                    JobFx.burst(el, back, 1.6 + pad * 0.5, SKY, SKY_CORE, 0.35, Opt.empty());
                    for (int i = 0; i < 7; i++) {
                        double a = Math.toRadians(i * 360.0 / 7 + Kit.rand() * 20);
                        Vec3 dd = V.add(V.scale(V.rightOf(f0), Math.cos(a)), V.scale(V.UP, Math.sin(a)));
                        SkillFx.sparks(el, V.add(back, dd, 0.5), JobTraits15.bowCore(i), 3, 3);
                    }
                    SkillFx.play(el, "entity.iron_golem.damage", b, 0.7, 1.0);
                    SkillFx.play(el, "block.anvil.land", b, 0.8, 0.6);
                } else {
                    RuneKit.knock(t, f0, k.num("knock", 1.1), 0.32);
                    JobFx.burst(el, b, 1.1 + pad * 0.4, SKY, SKY_CORE, 0.28, Opt.of("vel", RuneKit.entVel(t)));
                }
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 헤이븐 리프

    /**
     * 헤이븐 리프 (롤 쉔 W): 겨눈 아군 곁 (range 칸 — 그 앞 1.2칸, 없으면 앞 fallback 칸) 으로 무지개를 타고 leap 틱에 뛰어올라 내려앉으며 둘레 radius 칸을 내리침 (slam ·
     * 조금 밀쳐냄) → 그 자리에 dur 틱 빛의 피난처 (JobTraits15 — 안의 아군을 노린 막을 수 있는 평타 · 몹 투사체를 받지 않음). 묶였거나 타고 있으면 그 자리에서
     */
    static boolean haven(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int leap = Math.max(4, (int) k.num("leap", 8));
        double r = k.num("radius", 3);
        int dur = (int) k.num("dur", 40);
        ServerPlayer ally = JobTraits4.aimAlly(sp, k.num("range", 12));
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 land = null;
        if (ally != null) {
            double dist = V.hdist(ally.position(), c0);
            Vec3 u = dist > 0.1 ? V.flat(V.sub(ally.position(), c0)) : f;
            land = Kit.standSpot(level0, V.up(V.add(c0, u, Math.max(0, dist - 1.2)), 0.6), 2.5);
            if (land == null) land = Kit.standSpot(level0, V.up(ally.position(), 0.6), 2.5);
        } else {
            double can = RuneKit.dashDistance(sp, f, k.num("fallback", 4), 0.3, 1.5);
            land = Kit.standSpot(level0, V.up(V.add(c0, f, can), 0.6), 2.5);
        }
        if (land == null) land = c0;
        Vec3 to = land;
        boolean moving = !Status.isRooted(sp) && !Kit.ridingAny(sp) && V.hdist(to, c0) > 0.4;
        double peak = JobMove.hopPeak(sp, c0, to, 2.2);
        if (moving) JobMove.run(sp, JobMove.hop(0, leap, JobMove.at(to), peak));
        RpgCombat.setSteadfast(sp, leap + 2);
        // 뛰어오름: 발밑 무지개 빛 · 무지개 다리 (뛰는 포물선을 따라 — 처음부터 사라짐)
        JobFx15.halo(level0, V.up(c0, 0.06), 1.4, 0.5, Opt.of("alpha", 0.8), SKY, SKY_CORE);
        SkillFx.sparks(level0, V.up(c0, 0.3), JobTraits15.bowCore(4), 10, 4);
        if (moving) rainbowArc(level0, c0, to, peak, 0.5, 0.8, JobFx.hide1p(sp));
        SkillFx.play(level0, "entity.player.attack.sweep", c0, 0.7, 0.7);
        SkillFx.play(level0, "block.amethyst_block.chime", c0, 0.7, 0.8);
        JobKit.motionLater(sp, "sentinel_haven:land", leap - 2, life);
        RuneKit.later(leap, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 g = ThunderlordSkills.ground(lv, sp.position());
            // 내려앉음: 땅을 내리치는 충격 · 빛의 피난처 (솟음 → 일렁임 → 금 · 흩어짐) · 땅의 빛 고리
            JobFx.smash(lv, V.up(g, 0.05), r * 0.9, j, 0.45, Opt.empty());
            JobFx15.haven(lv, g, r, 0, 2, 0.12, false, JobFx.hide1p(sp), SKY, SKY_CORE);
            RuneKit.later(2, () -> havenDome(sp, lv, g, r, (dur - 6) / 20.0));
            RuneKit.later(Math.max(3, dur - 4), () -> JobFx15.haven(lv, g, r, 6, 2, 0.35, false, JobFx.hide1p(sp), SKY, SKY_CORE));
            JobFx15.halo(lv, V.up(g, 0.06), r, dur / 20.0, Opt.of("alpha", 0.85, "fade", 0.1), GOLD, GOLD_CORE);
            JobFx15.halo(lv, V.up(g, 0.07), r * 0.6, dur / 20.0, Opt.of("alpha", 0.6, "fade", 0.1), SKY, SKY_CORE);
            for (int i = 0; i < 7; i++) {
                double a = Math.toRadians(i * 360.0 / 7);
                Vec3 p = V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), r * 0.95);
                SkillFx.sparks(lv, V.up(p, 0.2), JobTraits15.bowCore(i), 3, 2.5);
            }
            JobFx.eyeRing(sp, j, 2.4, 1.0, 0.4);
            SkillFx.play(lv, "item.mace.smash_ground", g, 1.1, 0.8);
            SkillFx.play(lv, "block.beacon.activate", g, 1.5, 0.8);
            SkillFx.play(lv, "item.shield.block", g, 0.8, 0.8);
            JobTraits15.havenStart(sp, g, r, dur);
            for (LivingEntity e : Kit.around(sp, g, r, 3)) {
                if (!e.isAlive() || e.isRemoved()) continue;
                JobKit.strike(sp, e, j, P, k.num("slam", 2.4), false, false, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 1.2);
                    if (JobKit.pushable(e)) RuneKit.knock(e, V.flat(V.sub(e.position(), g)), k.num("knock", 0.5), 0.2);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 레인보우 램파트

    /**
     * 레인보우 램파트 (롤 야스오 W): 방패를 높이 들었다가 (riseAt 틱 — 하늘의 무지개가 방패로 모임) 땅을 내리치면 앞 ahead 칸 (벽이 가까우면 그 앞) 에 폭 width 칸 · 높이
     * height 칸 무지개 장벽이 솟음 (일곱 빛 장막 여덟 — 장벽 띠 (면에서 depth 칸) 의 적 rise · 앞으로 밀쳐냄 · 도발) → dur 틱 동안 적의 투사체를 지우고 닿은 적은 둔화 ·
     * 도발 (JobTraits15) → 장벽이 무지개 조각으로 부서짐 (면에서 depth + 0.5칸 collapse)
     */
    static boolean rampart(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int riseAt = Math.max(4, (int) k.num("riseAt", 10));
        int dur = Math.max(20, (int) k.num("dur", 100));
        double width = k.num("width", 8);
        double height = k.num("height", 3.5);
        double depth = k.num("depth", 1.5);
        double half = width / 2;
        Vec3 f = Kit.facingOf(sp);
        Vec3 along = V.rightOf(f);
        Vec3 c0 = sp.position();
        RpgCombat.setSteadfast(sp, riseAt + 2);
        // 치켜듦: 방패로 모이는 무지개 빛 (하늘에서 일곱 빛 줄이 내려옴) · 발밑 고리
        JobFx.charge(sp, j, new Vec3(0.2, 2.5, 0.25), riseAt, 0.9);
        for (int i = 0; i < 7; i++) {
            int ii = i;
            RuneKit.later(i, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                double a = Math.toRadians(-60 + ii * 20);
                Vec3 sky = V.up(V.add(V.add(sp.position(), V.rightOf(Kit.facingOf(sp)), Math.sin(a) * 5), Kit.facingOf(sp), -1.5 + Math.cos(a) * 2), 7.5);
                JobFx.line(lv, sky, V.up(sp.position(), 2.6), JobTraits15.bow(ii), JobTraits15.bowCore(ii), 0.35, Opt.of("alpha", 0.9));
            });
        }
        SkillFx.play(level0, "block.beacon.power_select", c0, 1.2, 0.8);
        SkillFx.play(level0, "block.amethyst_block.resonate", c0, 0.8, 0.9);
        JobKit.motionLater(sp, "sentinel_rampart:slam", riseAt - 3, life);
        Vec3[] mid = {null};
        RuneKit.later(riseAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            double room = RuneKit.rayLength(lv, V.up(c, 1.0), f, k.num("ahead", 2.5) + 0.6) - 0.6;
            Vec3 center = ThunderlordSkills.ground(lv, V.add(c, f, Math.max(0.8, Math.min(k.num("ahead", 2.5), room))));
            mid[0] = center;
            // 내리침: 방패가 땅을 침 · 장벽 쪽으로 땅을 타는 검기 · 장벽 자리의 빛
            JobFx.smash(lv, V.up(V.add(c, f, 0.7), 0.05), 1.6, j, 0.4, Opt.empty());
            JobFx.groundWave(lv, sp, V.add(c, f, 0.6), f, 1.3, V.hdist(center, c) + 0.6, 16, j);
            int n = 8;
            for (int i = 0; i < n; i++) {
                int ci = (int) Math.floor((i + 0.5) / n * JobTraits15.BOW.length);
                Vec3 g = ThunderlordSkills.ground(lv, V.add(center, along, -half + (i + 0.5) * width / n));
                double[] gl = JobTraits15.bow(ci);
                double[] co = JobTraits15.bowCore(ci);
                JobFx15.rampart(lv, g, height, 0, 2, 0.12, false, Opt.of("alpha", 0.95), gl, co);
                RuneKit.later(2, () -> JobFx15.rampart(lv, g, height, 2, 4, (dur - 2) / 20.0, true, Opt.of("alpha", 0.9, "fade", 0.04), gl, co));
                SkillFx.sparks(lv, V.up(g, 0.3), co, 4, 3.5);
            }
            RuneKit.glowCrack(lv, V.up(center, 0.04), half, JobTraits15.bow(4), JobTraits15.bowCore(4), Opt.of("life", 0.9));
            Ult.flash(lv, center, SKY, Opt.of("radius", 5.0, "time", new double[] {0.02, 0.05, 0.3}), sp);
            SkillFx.play(lv, "item.mace.smash_ground_heavy", center, 1.0, 0.9);
            SkillFx.play(lv, "block.beacon.activate", center, 0.9, 1.0);
            SkillFx.play(lv, "block.amethyst_block.resonate", center, 1.3, 1.0);
            JobTraits15.rampartStart(sp, center, along, f, half, height, dur, (int) k.num("slow", 2), (int) k.num("taunt", 40));
            for (LivingEntity e : Kit.around(sp, center, half + depth + 1, 4)) {
                if (!e.isAlive() || e.isRemoved() || !JobTraits15.inBand(e.position(), center, along, f, half, depth, height, Kit.bodyPad(e))) continue;
                JobTraits15.taunt(sp, e, (int) k.num("taunt", 40));
                JobKit.strike(sp, e, j, P, k.num("rise", 4.8), false, false, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 1.5);
                    if (JobKit.pushable(e)) RuneKit.knock(e, f, 0.9, 0.42);
                });
            }
        });
        RuneKit.later(riseAt + dur, () -> {
            if (!RuneKit.sameLife(sp, life) || mid[0] == null) return;
            ServerLevel lv = sp.level();
            Vec3 center = mid[0];
            JobTraits15.rampartEnd(sp);
            // 부서짐: 장막마다 프리즘 조각 · 무지개 불티 · 유리 깨지는 소리
            int n = 8;
            for (int i = 0; i < n; i++) {
                int ci = (int) Math.floor((i + 0.5) / n * JobTraits15.BOW.length);
                Vec3 g = ThunderlordSkills.ground(lv, V.add(center, along, -half + (i + 0.5) * width / n));
                JobFx15.rampart(lv, g, height, 6, 2, 0.45, false, Opt.of("alpha", 0.95), JobTraits15.bow(ci), JobTraits15.bowCore(ci));
                SkillFx.sparks(lv, V.up(g, height * 0.5), JobTraits15.bowCore(ci), 5, 4);
            }
            SkillFx.play(lv, "block.glass.break", center, 0.8, 1.0);
            SkillFx.play(lv, "block.amethyst_cluster.break", center, 0.9, 1.0);
            for (LivingEntity e : Kit.around(sp, center, half + depth + 1.5, 4)) {
                if (!e.isAlive() || e.isRemoved() || !JobTraits15.inBand(e.position(), center, along, f, half, depth + 0.5, height, Kit.bodyPad(e))) continue;
                JobKit.strike(sp, e, j, P, k.num("collapse", 2.4), false, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.2));
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 나인 타이즈

    /**
     * 나인 타이즈: dur 틱 태세 (강화 지속) — 아홉 물결의 어머니: 둘레를 아홉 물결이 감돌며 (JobTraits15.stanceFx) 내가 받거나 막은 피해의 rate 가 둘레 radius 칸 아군
     * (나 포함) 의 보호막으로 (JobTraits15). 그림: 발밑에서 아홉 물결이 솟아 부서짐 · 바다빛 고리
     */
    static boolean tides(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) JobUpgrades.num(sp, k, "dur", 200);
        JobTraits.startStance(sp, k, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        for (int i = 0; i < 9; i++) {
            double a = i * Math.PI * 2 / 9;
            Vec3 p = V.add(V.up(c, 0.6), new Vec3(Math.cos(a), 0, Math.sin(a)), 1.6);
            JobFx15.tide(level, p, 0.55, 0, 7, 0.55, false, JobFx.hide1p(sp).with("dir", V.UP, "speed", 1.6), SEA, SEA_CORE);
        }
        JobFx15.halo(level, V.up(c, 0.06), 2.2, 0.7, Opt.of("alpha", 0.8), SEA, SEA_CORE);
        RuneKit.novaRing(level, c, 0.6, 2.6, SEA, SEA_CORE, Opt.of("life", 0.4));
        JobFx.aura(sp, j, 2.6, 0.9, 0.8);
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.45);
        SkillFx.play(level, "block.conduit.activate", c, 1.1, 0.9);
        SkillFx.play(level, "entity.player.splash.high_speed", c, 0.8, 0.7);
        Notify.notify(sp, "§b§l" + k.name() + " §7— 받거나 막은 피해의 " + Math.round(k.num("rate", 0.3) * 100) + "% 가 아군 보호막으로 · " + dur / 20 + "초", 40);
        return true;
    }

    // ------------------------------------------------------------ 클라리온 콜

    /** 가장 위험한 아군 (range 칸 · 나 빼고 — 체력 비율이 가장 낮은 아군, 없으면 null) */
    static ServerPlayer endangered(ServerPlayer sp, double range) {
        ServerPlayer best = null;
        double low = 2;
        for (ServerPlayer o : JobTraits4.allies(sp, sp.position(), range)) {
            if (o == sp || o.isDeadOrDying()) continue;
            double q = o.getHealth() / Math.max(1e-3, o.getMaxHealth());
            if (q < low) {
                low = q;
                best = o;
            }
        }
        return best;
    }

    /**
     * 클라리온 콜 (롤 쉔 R · 갈리오 R): 하늘 성채의 뿔피리 (rpg:ult_bifrost) — 황금 뿔피리를 들어 blastAt 틱에 불면 소리의 물결이 둘레 blastRadius 칸 적을 침 (blast) ·
     * 둘레 tauntRadius 칸 적 도발, 가장 위험한 아군 (range 칸 — 체력 비율이 가장 낮은 아군 · 없으면 겨눈 적 · 앞 fallback 칸) 머리 위에 빛 기둥 · shieldFor 틱 보호막
     * (최대 체력 × shield) → flyAt 틱 무지개 다리를 타고 날아가 landAt 틱 내려앉음 (둘레 landRadius 칸 land · 큰 숫자 · 둘레 tauntRadius 칸 적 도발 · 아군 (나 포함)
     * 보호막). 묶였거나 타고 있으면 날지 않고 그 자리에 내려앉음
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int blastAt = Math.max(4, (int) k.num("blastAt", 10));
        int flyAt = Math.max(blastAt + 10, (int) k.num("flyAt", 40));
        int landAt = Math.max(flyAt + 6, (int) k.num("landAt", 50));
        int taunt = (int) k.num("taunt", 60);
        double tauntR = k.num("tauntRadius", 8);
        int shieldFor = (int) k.num("shieldFor", 80);
        double shieldK = k.num("shield", 0.15);
        Vec3 c0 = sp.position();
        Ult.sky(level0, c0, "rpg:ult_bifrost", landAt + 30);
        RpgCombat.setSteadfast(sp, landAt + 4);
        JobMove.run(sp, JobMove.hold(0, flyAt));
        // 뿔피리를 듦: 입가의 황금 뿔피리 (나타남 → 울림) · 발밑 황금 고리
        hornFx(sp, 0, 1, blastAt / 20.0 + 0.05, false);
        RuneKit.later(blastAt, () -> {
            if (RuneKit.sameLife(sp, life)) hornFx(sp, 1, 4, (flyAt - blastAt) / 20.0, true);
        });
        JobFx.charge(sp, j, new Vec3(0.15, 1.6, 0.45), blastAt, 0.7);
        JobFx15.halo(level0, V.up(c0, 0.06), 2.4, flyAt / 20.0, Opt.of("alpha", 0.8, "fade", 0.1), GOLD, GOLD_CORE);
        SkillFx.play(level0, "item.goat_horn.sound.0", c0, 0.75, 1.0);
        ServerPlayer[] who = {null};
        LivingEntity[] foe = {null};
        // 붊: 소리의 물결 (세 번 — 첫 번이 침) · 위험한 아군을 고름
        for (int i = 0; i < 3; i++) {
            int ii = i;
            RuneKit.later(blastAt + i * 10, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 c = sp.position();
                Vec3 f = Kit.facingOf(sp);
                Vec3 mouth = V.add(V.up(c, 1.55), f, 0.9);
                JobFx.ringFacing(lv, V.add(mouth, f, 0.4), 1.0 + ii * 0.35, j, V.scale(f, -1), 0.35, Opt.of("grow", 2.2, "alpha", 0.9));
                RuneKit.novaRing(lv, c, 0.8, k.num("blastRadius", 6) * (ii == 0 ? 1.0 : 0.7) / 0.8 - 1, GOLD, GOLD_CORE, Opt.of("life", 0.45));
                JobFx15.halo(lv, V.up(c, 0.1), k.num("blastRadius", 6) * (ii == 0 ? 0.9 : 0.6), 0.5, Opt.of("alpha", 0.75), JobTraits15.bow(ii * 2), JobTraits15.bowCore(ii * 2));
                SkillFx.play(lv, ii == 0 ? "item.goat_horn.sound.5" : "item.goat_horn.sound.1", c, ii == 0 ? 0.6 : 0.7, 1.2);
                if (ii != 0) return;
                Ult.flash(lv, c, GOLD, Opt.of("radius", 6.0, "time", new double[] {0.02, 0.05, 0.3}), sp);
                for (LivingEntity e : Kit.around(sp, c, tauntR, 4)) {
                    if (!e.isAlive() || e.isRemoved()) continue;
                    JobTraits15.taunt(sp, e, taunt);
                    if (e.distanceTo(sp) > k.num("blastRadius", 6) + Kit.bodyPad(e)) continue;
                    JobKit.strike(sp, e, j, P, k.num("blast", 8), true, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.4));
                }
                // 위험한 아군 (없으면 겨눈 적 · 앞)
                ServerPlayer o = endangered(sp, k.num("range", 48));
                who[0] = o;
                if (o != null) {
                    ServerLevel ol = o.level();
                    Vec3 og = ThunderlordSkills.ground(ol, o.position());
                    JobFx15.rampart(ol, og, 7.0, 0, 2, 0.15, false, Opt.of("alpha", 0.9), GOLD, GOLD_CORE);
                    RuneKit.later(3, () -> JobFx15.rampart(ol, og, 7.0, 2, 4, (landAt - blastAt - 3) / 20.0, true, Opt.of("alpha", 0.8, "fade", 0.1), GOLD, GOLD_CORE));
                    RpgHealth.addShield(o, RpgHealth.maxOf(o) * shieldK, shieldFor, RpgHealth.maxOf(o) * shieldK);
                    JobFx15.prism(ol, JobTraits15.chest(o), 0.55, 0, 2, 0.3, false, Opt.of("vel", RuneKit.bodyVel(o)), GOLD, GOLD_CORE);
                    SkillFx.play(ol, "block.beacon.activate", o.position(), 1.4, 1.0);
                    JobSystem.note(sp, "§e클라리온 콜 §7— " + o.getName().getString() + " 에게");
                } else {
                    foe[0] = Kit.pickTarget(sp, k.num("fallback", 16), 20);
                }
            });
        }
        // 날아감: 무지개 다리를 타고 그 아군 곁 (겨눈 적 앞 · 앞 fallback 칸) 으로
        Vec3[] spot = {null};
        RuneKit.later(flyAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            Vec3 land = null;
            ServerPlayer o = who[0];
            if (o != null && !o.isRemoved() && o.isAlive() && o.level() == lv) {
                double dist = V.hdist(o.position(), c);
                Vec3 u = dist > 0.1 ? V.flat(V.sub(o.position(), c)) : f;
                land = JobTraits12.beyond(sp, o.position(), V.scale(u, -1), 1.3);
                if (land == null) land = Kit.standSpot(lv, V.up(o.position(), 0.6), 2.5);
            } else if (foe[0] != null && !foe[0].isRemoved() && foe[0].isAlive()) {
                LivingEntity e = foe[0];
                Vec3 back = V.flat(V.sub(c, e.position()));
                Vec3 u = V.len(back) > 0.1 ? back : V.scale(f, -1);
                land = JobTraits12.beyond(sp, e.position(), u, 1.4 + Kit.bodyPad(e));
            } else {
                double can = RuneKit.dashDistance(sp, f, k.num("fallback", 16) * 0.5, 0.3, 1.5);
                land = Kit.standSpot(lv, V.up(V.add(c, f, can), 0.6), 2.5);
            }
            if (land == null) land = c;
            spot[0] = land;
            int fly = landAt - flyAt;
            if (!Status.isRooted(sp) && !Kit.ridingAny(sp) && V.hdist(land, c) > 0.4) {
                double peak = JobMove.hopPeak(sp, c, land, Math.min(6, 2.5 + V.hdist(land, c) * 0.25));
                JobMove.run(sp, JobMove.hop(0, fly, JobMove.at(land), peak));
                rainbowArc(lv, c, land, peak, 0.6, 1.1, JobFx.hide1p(sp));
            }
            JobFx.speed(sp, j, fly / 20.0, 0.7);
            SkillFx.play(lv, "item.goat_horn.sound.5", c, 0.9, 0.9);
            SkillFx.play(lv, "entity.player.attack.sweep", c, 0.6, 0.8);
        });
        JobKit.motionLater(sp, "sentinel_ult:land", landAt - 4, life);
        RuneKit.later(landAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 g = ThunderlordSkills.ground(lv, sp.position());
            double r = k.num("landRadius", 5);
            // 내려앉음: 무지개 충격 고리 일곱 · 황금 빛 터짐 · 프리즘 방패 · 땅의 빛
            JobFx.smash(lv, V.up(g, 0.05), r, j, 0.55, Opt.empty());
            for (int i = 0; i < JobTraits15.BOW.length; i++) {
                int ii = i;
                RuneKit.later(i, () -> RuneKit.novaRing(lv, g, 0.6 + ii * 0.1, r * (0.8 + ii * 0.1) / (0.6 + ii * 0.1) - 1, JobTraits15.bow(ii), JobTraits15.bowCore(ii),
                        Opt.of("life", 0.5)));
            }
            JobFx.burst(lv, V.up(g, 1.2), 3.0, GOLD, GOLD_CORE, 0.4, Opt.empty());
            JobFx15.prism(lv, V.up(V.add(g, Kit.facingOf(sp), 1.0), 1.2), 1.0, 5, 3, 0.5, false, JobFx.hide1p(sp), SKY, SKY_CORE);
            JobFx15.halo(lv, V.up(g, 0.07), r, 0.8, Opt.of("alpha", 0.9), GOLD, GOLD_CORE);
            RuneKit.glowCrack(lv, V.up(g, 0.04), r * 1.1, SKY, SKY_CORE, Opt.of("life", 0.9));
            JobFx.radialWaves(sp, j, g, 6, 0.8, r + 1, 0.9, 1.4, 0.3, 15);
            Ult.flash(lv, g, GOLD, Opt.of("radius", 8.0, "time", new double[] {0.02, 0.06, 0.4}), sp);
            SkillFx.play(lv, "item.mace.smash_ground_heavy", g, 0.8, 1.0);
            SkillFx.play(lv, "entity.generic.explode", g, 1.1, 0.7);
            SkillFx.play(lv, "block.beacon.activate", g, 0.8, 1.0);
            for (LivingEntity e : Kit.around(sp, g, tauntR, 4)) {
                if (!e.isAlive() || e.isRemoved()) continue;
                JobTraits15.taunt(sp, e, taunt);
                if (V.hdist(e.position(), g) > r + Kit.bodyPad(e)) continue;
                JobKit.strike(sp, e, j, P, k.num("land", 24), false, true, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 2.4);
                    if (JobKit.pushable(e)) Kit.launch(e, 0, 0.45, 0);
                });
            }
            List<ServerPlayer> shielded = new ArrayList<>();
            for (ServerPlayer o : JobTraits4.allies(sp, g, tauntR)) {
                double cap = RpgHealth.maxOf(o) * shieldK;
                RpgHealth.addShield(o, cap, shieldFor, cap);
                shielded.add(o);
                JobFx15.prism(o.level(), JobTraits15.chest(o), 0.5, 0, 3, 0.4, false, o == sp ? JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(o)) : Opt.of("vel",
                        RuneKit.bodyVel(o)), GOLD, GOLD_CORE);
            }
            if (shielded.size() > 1) JobSystem.note(sp, "§e클라리온 콜 §7— 아군 " + shielded.size() + " 보호막");
        });
        return true;
    }

    /** 입가의 황금 뿔피리 (나팔이 앞 위로 — 3인칭 · 다른 사람은 몸 앞, 1인칭은 화면 오른쪽 아래) */
    private static void hornFx(ServerPlayer sp, int f, int n, double life, boolean loop) {
        ServerLevel lv = sp.level();
        Vec3 fw = Kit.facingOf(sp);
        Vec3 dir = V.unit(V.add(fw, V.scale(V.UP, 0.35)));
        Vec3 at = V.add(V.add(V.up(sp.position(), 1.5), fw, 0.75), V.rightOf(fw), 0.12);
        JobFx15.horn(lv, at, dir, 0.55, f, n, life, loop, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "fade", 0.08), GOLD, GOLD_CORE);
        Vec3 look = sp.getViewVector(1F);
        Vec3 eye = V.add(V.add(V.add(sp.getEyePosition(), look, 1.6), JobFx.screenUp(look), -0.45), V.rightOf(Kit.facingOf(sp)), 0.4);
        JobFx15.horn(lv, eye, V.unit(V.add(look, V.scale(JobFx.screenUp(look), 0.3))), 0.4, f, n, life, loop, JobFx.only1p(sp).with("vel", RuneKit.bodyVel(sp), "fade",
                0.08, "alpha", 0.9), GOLD, GOLD_CORE);
    }
}
