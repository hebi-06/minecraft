package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.Elements;
import mmorpg.core.Jobs;
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
 * 2026-11-07 마검사 반전 엘리멘탈 블레이드 (소드 콜러 대신 — 사용자 "불 + 물 = 즉시 단일 %피해 …" 원소 반응 · 고른 것 "모두 원소 (예외)"): 1차 마검사 (한 가지 마력으로
 * 크게 베는 광역 단타) 를 거꾸로 — 다섯 원소를 칼에 바꿔 깃들여 혼자서 원소 반응을 터뜨림. 스킬마다 원소가 하나 (jobs.json elem — 모두 원소 스킬이라 위력 −5%, 반응 몫은
 * 직업 세기 1.15 에서 뺌): 플레어 엣지 (불 — 가로 불꽃 초승달 → 다시 누르면 불꽃 회오리) · 어스 서지 (바위 — 내달려 갈라진 길에서 바위 가시) · 볼트 피어스 (전기 — 번개
 * 찌르기 → 남은 번개가 세 번 내리꽂힘) · 프로스트타이드 (물 · 얼음 — 태세, JobTraits17) · 엘리멘탈 칼라미티 (다섯 — 다섯 원소의 거대한 칼이 차례로 내리꽂혀 반응을 잇고
 * 하나로 합쳐진 백금빛 대검이 내리찍음). 칼 · 검기 · 숫자는 그 원소의 빛 ({@link #tint} — 직업 색 대신 원소 색). 몸 쓰는 직업이 아님 — 칼과 칼빛 (내 동작은 칼질).
 * 맞은 자리는 JobImpact (화면은 흔들지 않음).
 */
final class ElementalbladeSkills {
    private ElementalbladeSkills() {}

    /** 칼라미티 차례 (jobs.json order 가 없으면) */
    private static final List<String> ORDER = List.of("fire", "water", "thunder", "ice", "rock");
    /** 바위 가시 (돌 몸 — 짙은 갈색 · 틈의 금빛) */
    private static final double[] ROCK_BODY = {0.42, 0.29, 0.16};
    private static final double[] ROCK_SEAM = {1.0, 0.78, 0.32};
    /** 칼라미티의 합쳐진 대검 (백금빛 — 다섯 원소 빛이 모임) */
    private static final double[] PRISM = {1.0, 0.86, 0.5};
    private static final double[] PRISM_CORE = {1.0, 0.98, 0.92};

    static void register() {
        JobSystem.register("elementalblade_flare", ElementalbladeSkills::flare);
        JobSystem.register("elementalblade_surge", ElementalbladeSkills::surge);
        JobSystem.register("elementalblade_bolt", ElementalbladeSkills::bolt);
        JobSystem.register(JobTraits17.TIDE, ElementalbladeSkills::tide);
        JobSystem.register("elementalblade_ult", ElementalbladeSkills::ult);
    }

    // ------------------------------------------------------------ 원소 빛

    private static final Map<String, Jobs.Job> TINTS = new HashMap<>();

    /** 직업 j 를 원소 el 의 빛으로 (그림 · 숫자 그라데이션 — id · 스킬 · 특성은 그대로). 원소가 아니면 j */
    static Jobs.Job tint(Jobs.Job j, String el) {
        Elements.Element e = el == null ? null : Elements.get(el);
        if (j == null || e == null) return j;
        return tint(j, el, e.glow(), e.core());
    }

    /** 직업 j 를 빛 glow · core 로 (이름 key 로 기억) */
    private static Jobs.Job tint(Jobs.Job j, String key, double[] glow, double[] core) {
        return TINTS.computeIfAbsent(j.id() + "|" + key, x -> new Jobs.Job(j.id(), j.name(), j.weapons(), j.stats(), j.rune(), j.ready(), j.scale(), j.role(), j.line(),
                new double[][] {glow, core}, j.trait(), j.skills(), j.base(), j.path()));
    }

    /** 이 직업 (그림의 바탕 — 직업이 없으면 엘리멘탈 블레이드) */
    private static Jobs.Job jobOr(ServerPlayer sp) {
        Jobs.Job j = JobSystem.job(sp);
        return j != null ? j : Jobs.job(JobTraits17.EB);
    }

    /** 땅 높이로 내린 자리 */
    private static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, V.up(p, 0.6), p.y), p.z);
    }

    /** 칼끝 (든 손 앞 — 1인칭 눈앞 오른쪽 아래) */
    private static Vec3 bladeTip(ServerPlayer sp) {
        RuneKit.Hand h = RuneKit.handOf(sp);
        return V.up(V.add(h.hand(), h.f(), 0.55), 0.25);
    }

    /**
     * 원소의 맞은 자리 (size = 몸 배): 불 = 불꽃이 터져 솟음 · 물 = 물보라 · 소용돌이 · 전기 = 하늘에서 번개 · 고리 · 얼음 = 얼음 조각 · 서리 고리 · 바위 = 돌가시 · 돌 조각
     */
    static void elementFx(ServerPlayer sp, LivingEntity e, String el, double size) {
        if (!(e.level() instanceof ServerLevel level)) return;
        Vec3 b = Kit.bodyPoint(e);
        Vec3 feet = e.position();
        double pad = Kit.bodyPad(e);
        double[] g = ElementFx.glow(el);
        double[] c = ElementFx.core(el);
        Jobs.Job tj = tint(jobOr(sp), el);
        switch (el) {
            case "fire" -> {
                JobFx.burst(level, b, (1.2 + pad * 0.5) * size, g, c, 0.32, Opt.empty());
                SkillFx.fx(level, "rpg:fire_wall", feet, Opt.of("size", (1.6 + pad) * size, "life", 0.55, "color", g, "alpha", 0.95, "spin", Kit.rand() * 360));
                SkillFx.fx(level, "rpg:ember", V.up(feet, 0.3), Opt.of("size", 1.2 * size, "life", 0.7, "speed", 1.6, "color", c, "alpha", 0.9));
                SkillFx.play(level, "entity.blaze.shoot", b, 0.9, 0.7);
                SkillFx.play(level, "item.firecharge.use", b, 0.8, 0.6);
            }
            case "water" -> {
                JobFx.burst(level, b, (1.1 + pad * 0.5) * size, g, c, 0.3, Opt.empty());
                SkillFx.fx(level, "rpg:splash", V.up(feet, 0.2), Opt.of("size", (1.6 + pad) * size, "life", 0.55, "color", c, "alpha", 0.95));
                SkillFx.fx(level, "rpg:whirlpool", V.up(feet, 0.08), Opt.of("dir", V.ZERO, "size", (1.4 + pad) * size, "life", 0.6, "spin", Kit.rand() * 360, "speed", 220.0,
                        "color", g, "alpha", 0.85));
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4 + Kit.rand() * 0.3;
                    SkillFx.fx(level, "rpg:drop", b, Opt.of("dir", V.unit(new Vec3(Math.cos(a), 0.6 + Kit.rand() * 0.8, Math.sin(a))), "speed", 4.0 + Kit.rand() * 2, "size",
                            0.16, "life", 0.5, "color", c, "alpha", 0.95));
                }
                SkillFx.play(level, "entity.generic.splash", b, 0.9, 0.8);
                SkillFx.play(level, "item.trident.riptide_1", b, 1.2, 0.6);
            }
            case "thunder" -> {
                JobFx.thunder(level, feet, 7.5 + pad, tj, 0.38, Opt.empty());
                JobFx.volt(level, V.up(feet, 0.1), (1.3 + pad) * size, tj, 0.45, Opt.empty());
                for (int i = 0; i < 4; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    RuneKit.zap(level, b, V.add(b, new Vec3(Math.cos(a), (Kit.rand() - 0.3) * 0.8, Math.sin(a)), (1.2 + pad) * size), g, c, 0.12, 0.22);
                }
                SkillFx.play(level, "item.trident.thunder", b, 1.5, 0.55);
                SkillFx.play(level, "entity.lightning_bolt.impact", b, 1.2, 0.5);
            }
            case "ice" -> {
                JobTraits16.shardsFx(level, b, (0.9 + pad * 0.4) * size);
                JobFx15.halo(level, V.up(feet, 0.08), (1.5 + pad) * size, 0.45, Opt.of("alpha", 0.9), g, c);
                SkillFx.fx(level, "rpg:snowfall", V.up(b, 0.5), Opt.of("size", 0.9 * size, "life", 0.8, "color", c, "alpha", 0.85));
                SkillFx.play(level, "block.glass.break", b, 0.9, 0.8);
                SkillFx.play(level, "block.powder_snow.break", b, 0.8, 0.9);
            }
            default -> {
                for (int i = 0; i < 5; i++) {
                    double a = i * Math.PI * 2 / 5 + Kit.rand() * 0.4;
                    Vec3 p = V.add(feet, new Vec3(Math.cos(a), 0, Math.sin(a)), (0.7 + pad) * size);
                    JobFx.spikeLife(level, ground(level, p), (0.55 + Kit.rand() * 0.25) * size, 0.1, 0.35, Opt.of("spin", Kit.rand() * 20 - 10), ROCK_BODY, ROCK_SEAM);
                }
                RuneKit.rockBurst(level, V.up(feet, 0.4), 8, 4.5, 0.3);
                JobFx.burst(level, b, (1.0 + pad * 0.5) * size, g, c, 0.28, Opt.empty());
                SkillFx.play(level, "block.stone.break", b, 0.7, 1.0);
                SkillFx.play(level, "item.mace.smash_ground", b, 0.9, 0.6);
            }
        }
    }

    // ------------------------------------------------------------ 플레어 엣지

    /**
     * 플레어 엣지 (불): 칼을 오른쪽 뒤로 감아 slashAt 틱 동안 칼날에 불꽃을 감았다가 앞 reach 칸 · arc 도를 가로로 크게 벰 (slash — 거대한 불꽃 초승달 검기) → gap 틱
     * 뒤부터 window 틱 안에 다시 누르면 (JobRecast) 몸을 크게 돌려 spinAt 틱에 불꽃 회오리로 둘레 radius 칸을 벰 (spin)
     */
    static boolean flare(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int stage = Math.min(1, JobRecast.stage(sp, k.id()));
        Jobs.Job fj = tint(j, "fire");
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        if (stage == 0) {
            JobRecast.open(sp, k, 1, (int) k.num("window", 40), (int) k.num("gap", 11), null);
            int at = Math.max(1, (int) k.num("slashAt", 5));
            // 감음: 오른쪽 뒤로 당긴 칼날에 불꽃이 감김 (빛이 빨려 들며 커짐) · 칼날을 따라 솟는 불티
            JobFx.charge(sp, fj, new Vec3(0.8, 1.25, -0.45), at, 0.7);
            for (int t = 0; t < at; t += 2) {
                RuneKit.later(t, () -> {
                    if (RuneKit.sameLife(sp, life)) JobTraits17.bladeGlow(sp, "fire", 1.4);
                });
            }
            SkillFx.play(level0, "item.firecharge.use", sp.position(), 0.9, 0.6);
            SkillFx.play(level0, "entity.blaze.ambient", sp.position(), 1.4, 0.35);
            RuneKit.later(at, () -> {
                if (RuneKit.sameLife(sp, life)) flareSlash(sp, fj, k, P, life);
            });
            JobSystem.note(sp, "§6다시 누르면 불꽃 회오리 §7(" + Math.round(k.num("window", 40) / 2.0) / 10.0 + "초)");
        } else {
            JobRecast.close(sp, k.id());
            JobRecast.motion(sp, k.id() + ":spin");
            int at = Math.max(1, (int) k.num("spinAt", 4));
            // 몸을 크게 돌림: 발밑에서 불꽃 고리가 감겨 듦 → at 틱에 회오리
            JobFx.ring(level0, V.up(sp.position(), 0.08), k.num("radius", 3.0), fj, true, at / 20.0 + 0.1, Opt.of("alpha", 0.8));
            SkillFx.play(level0, "entity.blaze.shoot", sp.position(), 0.7, 0.6);
            RuneKit.later(at, () -> {
                if (RuneKit.sameLife(sp, life)) flareSpin(sp, fj, k, P, life);
            });
        }
        JobTraits17.setBlade(sp, "fire");
        return true;
    }

    /** 가로 불꽃 베기: 눈앞 큰 베기 · 거대한 불꽃 초승달 검기 · 베인 부채꼴 땅에서 솟는 불길 · 부채꼴 안의 적 */
    private static void flareSlash(ServerPlayer sp, Jobs.Job fj, Jobs.Skill k, double P, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double reach = k.num("reach", 4.5);
        double arc = k.num("arc", 110);
        JobFx.slash(sp, fj, 2.2, 0.3, 2.3, 172.0, 0.26);
        JobFx.bigWave(level, sp, V.up(V.add(c, f, 1.0), 1.1), f, 14, 2.1, -10, 0.3, fj, Opt.empty());
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(-arc / 2 + arc * i / 4.0);
            Vec3 d = V.turnY(f, Math.toDegrees(a));
            Vec3 p = ground(level, V.add(c, d, reach * 0.75));
            int ii = i;
            RuneKit.later(Math.abs(i - 2), () -> SkillFx.fx(level, "rpg:fire_wall", p, Opt.of("size", 1.3, "life", 0.5, "color", JobKit.glow(fj), "alpha", 0.9, "spin",
                    ii * 70.0)));
        }
        SkillFx.sparks(level, bladeTip(sp), JobFx.inner(fj), 10, 5.0);
        SkillFx.play(level, "entity.player.attack.sweep", c, 0.8, 1.0);
        SkillFx.play(level, "entity.blaze.shoot", c, 1.1, 0.8);
        SkillFx.play(level, "item.firecharge.use", c, 1.3, 0.6);
        for (LivingEntity e : RuneKit.coneAround(sp, c, f, reach, arc)) {
            JobKit.strike(sp, e, fj, P, k.num("slash", 3.4), true, false, life, 0, x -> {
                JobImpact.hit(sp, e, fj, 1.2);
                if (e.level() instanceof ServerLevel lv) SkillFx.fx(lv, "rpg:ember", V.up(e.position(), 0.3), Opt.of("size", 1.0, "life", 0.6, "speed", 1.4, "color",
                        JobKit.core(fj), "alpha", 0.9));
            });
        }
    }

    /** 불꽃 회오리: 허리 높이 회전 칼날 · 몸에서 솟는 불꽃 회오리 · 둘레 불길 고리 · 둘레의 적 */
    private static void flareSpin(ServerPlayer sp, Jobs.Job fj, Jobs.Skill k, double P, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double r = k.num("radius", 3.0);
        JobFx.whirl(level, V.up(c, 1.0), r, fj, 0.4, JobFx.hide1p(sp).with("fps", 40.0));
        JobFx.twister(sp, fj, 3.2, 0.55, 0.95);
        JobFx.slash(sp, fj, 1.8, 0.25, 2.6, 186.0, 0.24);
        RuneKit.novaRing(level, c, 0.6, r / 0.6 - 1, JobKit.glow(fj), JobKit.core(fj), Opt.of("life", 0.4));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            Vec3 p = ground(level, V.add(c, new Vec3(Math.cos(a), 0, Math.sin(a)), r * 0.85));
            RuneKit.later(i % 2, () -> SkillFx.fx(level, "rpg:fire_wall", p, Opt.of("size", 1.2, "life", 0.5, "color", JobKit.glow(fj), "alpha", 0.9, "spin",
                    Kit.rand() * 360)));
        }
        SkillFx.play(level, "entity.blaze.shoot", c, 0.7, 1.0);
        SkillFx.play(level, "item.trident.riptide_2", c, 1.2, 0.7);
        SkillFx.play(level, "entity.player.attack.sweep", c, 0.6, 1.0);
        for (LivingEntity e : Kit.around(sp, c, r, 2.0)) {
            JobKit.strike(sp, e, fj, P, k.num("spin", 5.1), true, false, life, 0, x -> {
                JobImpact.hit(sp, e, fj, 1.5);
                elementFx(sp, e, "fire", 0.8);
            });
        }
    }

    // ------------------------------------------------------------ 어스 서지

    /**
     * 어스 서지 (바위): 칼끝을 땅에 대고 바위 갑옷을 두른 채 보는 쪽 dist 칸 (벽 앞까지) 을 dashTicks 틱에 미끄러지듯 내달림 (지나간 땅이 금빛으로 갈라짐) → eruptAt 틱에
     * 갈라진 길 전체에서 바위 가시가 한꺼번에 솟아 길 위 (폭 width) 의 적을 띄움 (spike · lift). 내달리는 동안 · guardFor 틱 받는 피해 −guard. 묶였거나 타고 있으면 쓰지 않음
     */
    static boolean surge(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Jobs.Job rj = tint(j, "rock");
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        int dash = Math.max(2, (int) k.num("dashTicks", 6));
        int erupt = Math.max(dash, (int) k.num("eruptAt", 7));
        double can = RuneKit.dashDistance(sp, f, k.num("dist", 7), 0.3, 1.5);
        double len = Math.max(1.5, can);
        Guards.set(sp, dash + (int) k.num("guardFor", 20), k.num("guard", 0.3), null, null);
        RpgCombat.setSteadfast(sp, erupt + 2);
        if (can > 0.4) JobMove.run(sp, JobMove.to(0, dash, JobMove.at(V.add(c0, f, can)), JobMove.OUT));
        // 바위 갑옷: 몸을 감싸는 돌 조각 · 발밑 돌가루 — 내달리는 동안 2틱마다 몸 둘레 돌 조각 · 발밑 돌 튐 · 갈라짐이 몸을 따라 달림
        // (영상 42346989114: 몸에서 솟는 빛 JobFx.aura 는 쓴 자리에 남아 금빛 불꽃처럼 보였음 — 뺌)
        RuneKit.rockBurst(level0, V.up(c0, 0.8), 6, 2.0, 0.22);
        Vec3 g0 = ground(level0, c0);
        WarlordSkills.rift(level0, g0, f, len, ROCK_SEAM, 0.9, len * 20 / dash);
        for (int t = 1; t <= dash; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 p = sp.position();
                RuneKit.rockBurst(lv, V.up(p, 0.2), 3, 2.5, 0.16);
                RuneKit.rockBurst(lv, V.up(p, 1.1), 3, 1.2, 0.2);
                SkillFx.fx(lv, "rpg:dust", V.up(p, 0.1), Opt.of("size", 0.9, "life", 0.5, "color", ROCK_BODY, "alpha", 0.7));
                SkillFx.play(lv, "block.gravel.break", p, 0.8, 0.5);
            });
        }
        SkillFx.play(level0, "block.stone.place", c0, 0.6, 0.9);
        SkillFx.play(level0, "item.armor.equip_iron", c0, 0.8, 0.8);
        // 솟음: 길 전체에서 바위 가시 (1.1칸마다 — 가운데 크게) · 금빛 틈 · 길 위의 적을 띄움
        RuneKit.later(erupt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 r = V.rightOf(f);
            for (double s = 0.6; s <= len + 0.3; s += 1.1) {
                double side = (Kit.rand() - 0.5) * 0.5;
                Vec3 p = ground(lv, V.add(V.add(c0, f, s), r, side));
                // 영상 42346989114: 0.75 ~ 1.2 는 과녁 (2칸) 보다 작아 보였음 → 1.15 ~ 1.65 (높이 약 2.3 ~ 3.3칸) · 양옆에 작은 가시 하나씩
                double size = 1.15 + 0.4 * Math.sin(Math.PI * s / (len + 0.6)) + Kit.rand() * 0.1;
                // 2026-11-13 3D 바위 가시 (직업 도감 메모 "땅 연출을 3d로" — 2D rfx_jspike 대신, 금 사이 금빛)
                WarlordSkills.spike3d(lv, p, size, 0.1, 0.6, ROCK_SEAM);
                Vec3 q = ground(lv, V.add(p, r, (side > 0 ? -1 : 1) * 0.8));
                WarlordSkills.spike3d(lv, q, size * 0.6, 0.12, 0.5, ROCK_SEAM);
                RuneKit.glowCrack(lv, p, 1.1, ROCK_BODY, ROCK_SEAM, Opt.of("life", 1.0, "v", (double) ((int) (s * 3) % 2)));
            }
            RuneKit.rockBurst(lv, V.up(V.add(c0, f, len * 0.5), 0.5), 10, 5.0, 0.3);
            // 2026-11-13 갈라진 길에서 3D 바위가 솟아 양옆으로 튐
            for (double u : new double[] {0.35, 0.75}) mmorpg.fabric.rune.RuneSwing.erupt(lv, ground(lv, V.add(c0, f, len * u)), f, 4, 360, 0.6, 1.4, 0.4, 0.75, 2.4,
                    ROCK_SEAM);
            SkillFx.play(lv, "item.mace.smash_ground", sp.position(), 0.7, 1.0);
            SkillFx.play(lv, "block.pointed_dripstone.land", V.add(c0, f, len * 0.5), 0.6, 1.0);
            SkillFx.play(lv, "block.stone.break", V.add(c0, f, len * 0.5), 0.5, 0.9);
            double lift = k.num("lift", 0.5);
            for (Kit.LineHit hit : RuneKit.lineTargets(sp, V.up(c0, 0.9), f, len + 0.6, k.num("width", 1.6) / 2)) {
                LivingEntity e = hit.e();
                JobKit.strike(sp, e, rj, P, k.num("spike", 5.1), true, false, life, 0, x -> {
                    JobImpact.hit(sp, e, rj, 1.4);
                    if (JobKit.pushable(e)) RuneKit.knock(e, f, 0.05, lift);
                    RuneKit.rockBurst(lv, Kit.bodyPoint(e), 5, 3.5, 0.24);
                });
            }
        });
        JobTraits17.setBlade(sp, "rock");
        return true;
    }

    // ------------------------------------------------------------ 볼트 피어스

    /**
     * 볼트 피어스 (전기): 칼을 뒤로 당겨 chargeAt 틱 동안 칼끝에 번개를 모았다가 겨눈 적 (reach 칸 — 없으면 앞 줄의 첫 적) 을 번개 창 같은 찌르기로 꿰뚫음 (pierce) → 칼에
     * 남은 번개가 every 틱마다 그 적에게 내리꽂힘 (strikes — 적이 없으면 찌르기 그림만)
     */
    static boolean bolt(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        Jobs.Job tj = tint(j, "thunder");
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int charge = Math.max(1, (int) k.num("chargeAt", 8));
        int every = Math.max(10, (int) k.num("every", 10));
        double reach = k.num("reach", 6);
        List<Double> strikes = SwordmasterSkills.numbers(k, "strikes", List.of(3.07, 3.07, 3.06));
        JobFx.charge(sp, tj, new Vec3(0.55, 1.3, -0.55), charge, 0.75);
        for (int t = 0; t < charge; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                JobTraits17.bladeGlow(sp, "thunder", 1.3);
            });
        }
        SkillFx.play(level0, "block.beacon.power_select", sp.position(), 1.8, 0.4);
        SkillFx.play(level0, "entity.lightning_bolt.thunder", sp.position(), 1.9, 0.15);
        RuneKit.later(charge, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 f = Kit.facingOf(sp);
            LivingEntity t = Kit.pickTarget(sp, reach, 25);
            if (t == null) {
                List<Kit.LineHit> line = RuneKit.lineTargets(sp, V.up(sp.position(), 1.0), f, reach, 0.9);
                t = line.isEmpty() ? null : line.get(0).e();
            }
            Vec3 tip = bladeTip(sp);
            Vec3 to = t != null ? Kit.bodyPoint(t) : V.up(V.add(sp.position(), f, reach), 1.1);
            Vec3 d = V.unit(V.sub(to, tip));
            // 번개 창: 칼끝에서 적을 꿰뚫고 뒤로 빠져나가는 빛의 창 · 창을 감는 번개 · 꿰뚫린 자리 번쩍임 (영상 42346989114: 창 1.3 · 빛줄기 하나는 3인칭에서 작았음 → 창 2.0 ·
            // 꿰뚫고 뒤로 2칸 · 굵은 번개 다섯 · 칼끝 · 과녁의 번개 구슬 · 퍼지는 고리)
            JobFx.spear(lv, V.add(to, d, 2.0), d, 2.0, 0, 0.3, tj, Opt.of("fade", 0.4));
            JobFx.beam(lv, tip, V.add(to, d, 2.2), tj, 0.6, 0.26, Opt.empty());
            for (int i = 0; i < 5; i++) RuneKit.zap(lv, tip, V.add(V.add(to, d, Kit.rand() * 1.6), new Vec3(Kit.rand() - 0.5, Kit.rand() - 0.5, Kit.rand() - 0.5), 0.9),
                    JobKit.glow(tj), JobKit.core(tj), 0.16, 0.28);
            RuneKit.plasmaOrb(lv, to, 1.0, JobKit.glow(tj), Opt.of("life", 0.35));
            RuneKit.plasmaOrb(lv, tip, 0.55, JobKit.glow(tj), Opt.of("life", 0.25, "vel", RuneKit.bodyVel(sp)));
            RuneKit.novaRing(lv, V.sub(to, new Vec3(0, 0.9, 0)), 0.6, 2.4, JobKit.glow(tj), JobKit.core(tj), Opt.of("life", 0.4));
            JobFx.slash(sp, tj, 2.0, 0.15, 1.4, 90.0, 0.18);
            SkillFx.play(lv, "item.trident.throw", sp.position(), 1.4, 0.8);
            SkillFx.play(lv, "entity.lightning_bolt.impact", to, 1.6, 0.6);
            if (t == null) return;
            LivingEntity target = t;
            JobKit.strike(sp, target, tj, P, k.num("pierce", 6.1), true, false, life, 0, x -> {
                JobImpact.hit(sp, target, tj, 1.5);
                JobFx.stab(lv, Kit.bodyPoint(target), 1.4 + Kit.bodyPad(target) * 0.5, tj, 0.3, Opt.of("spin", 0.0));
            });
            // 남은 번개: every 틱마다 하늘에서 그 적에게 (내리꽂히기 직전 칼끝이 지직거림)
            for (int i = 0; i < strikes.size(); i++) {
                double mult = strikes.get(i);
                int at = (i + 1) * every;
                RuneKit.later(at - 3, () -> {
                    if (RuneKit.sameLife(sp, life) && !target.isRemoved()) RuneKit.zap(sp.level(), bladeTip(sp), V.up(bladeTip(sp), 1.4), JobKit.glow(tj), JobKit.core(tj), 0.08,
                            0.16);
                });
                JobKit.strike(sp, target, tj, P, mult, true, false, life, at, x -> {
                    JobImpact.hit(sp, target, tj, 1.1);
                    elementFx(sp, target, "thunder", 0.9);
                });
            }
        });
        JobTraits17.setBlade(sp, "thunder");
        return true;
    }

    // ------------------------------------------------------------ 프로스트타이드

    /** 프로스트타이드 (물 · 얼음): dur 틱 태세 (JobTraits17 — 칼 평타마다 물 · 얼음이 번갈아 · 물결 · 서리 검기 평타 × echo) · 켜질 때 물 · 서리 소용돌이 */
    static boolean tide(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", (int) k.num("dur", 160));
        JobTraits.startStance(sp, k, dur);
        JobTraits17.tideOn(sp);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Jobs.Job wj = tint(j, "water");
        Jobs.Job ij = tint(j, "ice");
        SkillFx.fx(level, "rpg:whirlpool", V.up(c, 0.08), Opt.of("dir", V.ZERO, "size", 2.2, "life", 0.8, "spin", 0.0, "speed", 260.0, "color", JobKit.glow(wj), "alpha",
                0.85));
        JobFx.twister(sp, wj, 2.8, 0.6, 0.8);
        for (int q = 0; q < 3; q++) {
            int qq = q;
            RuneKit.later(q * 4, () -> JobFx15.halo(sp.level(), V.up(sp.position(), 0.08), 1.2 + qq * 1.0, 0.5, Opt.of("alpha", 0.85 - qq * 0.15), JobKit.glow(qq % 2 == 0
                    ? wj : ij), JobKit.core(qq % 2 == 0 ? wj : ij)));
        }
        JobTraits16.shardsFx(level, V.up(c, 1.2), 0.7);
        JobTraits17.bladeGlow(sp, "water", 1.6);
        JobTraits17.bladeGlow(sp, "ice", 1.6);
        SkillFx.play(level, "item.trident.riptide_1", c, 1.0, 0.8);
        SkillFx.play(level, "block.glass.place", c, 0.7, 0.7);
        SkillFx.play(level, "entity.player.hurt_freeze", c, 1.2, 0.5);
        return true;
    }

    // ------------------------------------------------------------ 엘리멘탈 칼라미티

    /**
     * 엘리멘탈 칼라미티 (다섯 원소): 칼을 하늘로 치켜들면 칼끝에서 다섯 빛줄기가 솟아 겨눈 적 (range 칸 — 없으면 앞 fallback 칸의 땅 · 그 둘레 3칸의 가장 가까운 적) 위
     * 하늘에 riseAt 틱에 다섯 원소의 거대한 칼이 둥글게 나타남 → first 틱부터 every 틱마다 order 차례로 내리꽂힘 (swords — 칼마다 그 원소를 묻힘: 같은 적 2초 제한 없이
     * 이어서 반응) → finalAt 틱에 다섯 칼이 하나로 합쳐진 백금빛 대검이 내리찍음 (final · 큰 숫자)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        LivingEntity t0 = Kit.pickTarget(sp, k.num("range", 14), 15);
        Vec3 spot0 = t0 != null ? t0.position() : ground(level0, V.add(c0, f0, RuneKit.dashDistance(sp, f0, k.num("fallback", 8), 0.3, 1.5)));
        List<String> order = strings(k, "order", ORDER);
        List<Double> swords = SwordmasterSkills.numbers(k, "swords", List.of(8.0, 8.0, 8.0, 8.0, 8.0));
        int n = Math.min(order.size(), swords.size());
        int riseAt = Math.max(1, (int) k.num("riseAt", 6));
        int first = Math.max(riseAt + 4, (int) k.num("first", 18));
        int every = Math.max(10, (int) k.num("every", 10));
        int finalAt = Math.max(first + (n - 1) * every + 10, (int) k.num("finalAt", 70));
        String life = RuneKit.lifeToken(sp);
        double react = JobSystem.power(sp, j);
        LivingEntity[] cur = {t0};
        Vec3[] spot = {spot0};
        double H = 6.2;
        double R = 2.4;
        Ult.sky(level0, c0, "rpg:ult_elements", finalAt + 24);
        Ult.flash(level0, c0, JobKit.glow(j), Opt.of("radius", 10.0, "time", new double[] {0.03, 0.05, 0.35}), sp);
        SkillFx.play(level0, "block.beacon.activate", c0, 1.3, 0.8);
        SkillFx.play(level0, "block.end_portal.spawn", c0, 1.6, 0.3);
        JobFx.aura(sp, j, 3.0, 0.6, 0.9);
        // 0 ~ riseAt: 치켜든 칼끝에서 다섯 원소 빛줄기가 하늘로 솟음 (칼이 나타날 자리로)
        Vec3[] seats = new Vec3[n];
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(-90 + i * 360.0 / n);
            seats[i] = new Vec3(Math.cos(a) * R, H, Math.sin(a) * R);
        }
        Vec3 tip0 = V.up(c0, 3.0);
        for (int i = 0; i < n; i++) {
            Jobs.Job ej = tint(j, order.get(i));
            Vec3 to = V.add(spot0, seats[i]);
            int ii = i;
            RuneKit.later(i, () -> JobFx.line(sp.level(), tip0, to, ej, 0.35 + ii * 0.03, Opt.of("alpha", 0.9)));
        }
        // riseAt: 하늘에 다섯 칼 (칼끝이 아래 · 천천히 오르내림) · 땅에 원소 고리
        int[] ids = new int[n];
        Vec3[] hover = new Vec3[n];
        RuneKit.later(riseAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            if (cur[0] != null && !cur[0].isRemoved()) spot[0] = cur[0].position();
            for (int i = 0; i < n; i++) {
                Jobs.Job ej = tint(j, order.get(i));
                Vec3 at = V.add(spot[0], seats[i]);
                hover[i] = at;
                int stay = first + i * every - riseAt;
                ids[i] = JobFx3d.spawn(lv, "phantom_sword", at, ej, Opt.of("dir", V.scale(V.UP, -1), "life", stay / 20.0 + 0.1, "scale", 2.1, "fadeIn", 0.2, "fadeOut", 0.05,
                        "glowAlpha", 0.6, "shine", 0.4, "bob", 0.12, "bobHz", 0.9, "faceCam", 1.0));
                JobFx.glint(lv, at, 0.8, ej, 0.35, Opt.empty());
            }
            JobFx.runeRing(lv, V.up(ground(lv, spot[0]), 0.08), R + 0.6, j, n, 40, (finalAt - riseAt) / 20.0, Opt.of("alpha", 0.8));
            SkillFx.play(lv, "block.amethyst_block.resonate", spot[0], 0.7, 1.0);
            SkillFx.play(lv, "item.trident.riptide_3", spot[0], 1.4, 0.6);
        });
        // 내리꽂힘: every 틱마다 한 자루 (3틱 전에 날아감 — 닿는 틱에 피해 · 원소 · 그 원소의 맞은 자리)
        for (int i = 0; i < n; i++) {
            int ii = i;
            String el = order.get(i);
            Jobs.Job ej = tint(j, el);
            double mult = swords.get(i);
            int at = first + i * every;
            RuneKit.later(at - 3, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                LivingEntity t = pick(sp, cur, spot);
                Vec3 from = hover[ii] != null ? hover[ii] : V.add(spot[0], seats[ii]);
                JobFx3d.kill(lv, ids[ii], from, 0.02);
                Vec3 to = t != null ? Kit.bodyPoint(t) : V.up(ground(lv, spot[0]), 0.4);
                JobFx3d.fly(lv, "phantom_sword", from, to, JobFx3d.SWORD_TIP, 2.1, 0.15, 0.3, ej, Opt.of("fadeOut", 0.12, "shine", 0.6, "glowAlpha", 0.7));
                SkillFx.play(lv, "item.trident.throw", from, 0.8, 0.8);
            });
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                JobTraits17.setBlade(sp, el);
                LivingEntity t = pick(sp, cur, spot);
                if (t == null) {
                    // 적이 없음: 땅에 꽂힌 자리의 그림만
                    ServerLevel lv = sp.level();
                    Vec3 g = ground(lv, spot[0]);
                    JobFx.burst(lv, V.up(g, 0.4), 1.2, ej, 0.3, Opt.empty());
                    RuneKit.rockBurst(lv, V.up(g, 0.3), 5, 3.5, 0.2);
                    return;
                }
                JobKit.strike(sp, t, ej, P, mult, true, false, life, 0, x -> {
                    ElementSystem.apply(sp, t, el, react, true, true);
                    JobImpact.hit(sp, t, ej, 1.4);
                    elementFx(sp, t, el, 1.1);
                });
                if (ii == n - 1) JobFx.aura(sp, j, 2.8, 0.5, 0.8);
            });
        }
        // 합쳐짐: 다섯 원소 빛이 적 위 높이 한 점으로 모여 백금빛 대검이 됨 (finalAt − 10)
        int merge = finalAt - 10;
        int[] big = {0};
        Vec3[] bigAt = {null};
        RuneKit.later(merge, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            LivingEntity t = pick(sp, cur, spot);
            Vec3 base = t != null ? t.position() : ground(lv, spot[0]);
            Vec3 top = V.up(base, H + 2.5);
            bigAt[0] = top;
            for (int i = 0; i < n; i++) {
                Jobs.Job ej = tint(j, order.get(i));
                double a = Math.toRadians(-90 + i * 360.0 / n);
                Vec3 from = V.add(base, new Vec3(Math.cos(a) * (R + 1.2), 0.6, Math.sin(a) * (R + 1.2)));
                JobFx.line(lv, from, top, ej, 0.4, Opt.of("alpha", 0.95));
                JobFx.glint(lv, top, 0.5 + i * 0.12, ej, 0.3, Opt.empty());
            }
            big[0] = JobFx3d.spawn(lv, "phantom_sword", top, tint(j, "prism", PRISM, PRISM_CORE), Opt.of("dir", V.scale(V.UP, -1), "life", 0.6, "scale", 3.4, "fadeIn",
                    0.25, "fadeOut", 0.05, "glowAlpha", 0.8, "shine", 0.8, "faceCam", 1.0));
            Ult.flash(lv, top, PRISM, Opt.of("radius", 8.0, "time", new double[] {0.02, 0.04, 0.3}), sp);
            SkillFx.play(lv, "block.beacon.power_select", top, 0.7, 1.0);
            SkillFx.play(lv, "block.respawn_anchor.charge", top, 0.8, 0.9);
        });
        RuneKit.later(finalAt - 4, () -> {
            if (RuneKit.sameLife(sp, life)) JobKit.motion(sp, k.id() + ":final");
        });
        // 내리찍음: 대검이 3틱에 적에게 → finalAt 큰 한 방 · 다섯 원소 고리 · 백금빛 빛살 · X 검기
        RuneKit.later(finalAt - 3, () -> {
            if (!RuneKit.sameLife(sp, life) || bigAt[0] == null) return;
            ServerLevel lv = sp.level();
            LivingEntity t = pick(sp, cur, spot);
            JobFx3d.kill(lv, big[0], bigAt[0], 0.02);
            // 영상 42346989114: 칼끝이 가슴에서 멈춰 하늘에 걸린 칼처럼 보였음 → 과녁을 꿰뚫고 땅에 박힘 (칼끝이 땅 아래 0.8칸)
            Vec3 to = V.up(ground(lv, t != null ? t.position() : spot[0]), -0.8);
            JobFx3d.fly(lv, "phantom_sword", bigAt[0], to, JobFx3d.SWORD_TIP, 3.4, 0.15, 0.45, tint(j, "prism", PRISM, PRISM_CORE), Opt.of("fadeOut", 0.2, "shine", 0.9,
                    "glowAlpha", 0.85));
            SkillFx.play(lv, "item.trident.riptide_3", to, 0.8, 1.0);
        });
        RuneKit.later(finalAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            LivingEntity t = pick(sp, cur, spot);
            Vec3 g = ground(lv, t != null ? t.position() : spot[0]);
            Vec3 b = t != null ? Kit.bodyPoint(t) : V.up(g, 0.8);
            for (int i = 0; i < n; i++) {
                String el = order.get(i);
                int ii = i;
                RuneKit.later(i, () -> RuneKit.novaRing(lv, g, 1.0 + ii * 0.5, 3.4, ElementFx.glow(el), ElementFx.core(el), Opt.of("life", 0.55)));
            }
            JobFx.burst(lv, b, 3.0, PRISM, PRISM_CORE, 0.45, Opt.empty());
            RuneKit.rockBurst(lv, V.up(g, 0.3), 12, 6.0, 0.3);
            RuneKit.godRays(sp, lv, g, 3.5, 8, PRISM, PRISM_CORE, Opt.of("h", 7.0, "life", 0.7, "alpha", 0.8, "skipNear", true));
            Vec3 ff = t != null ? V.flat(V.sub(t.position(), sp.position())) : Kit.facingOf(sp);
            ff = V.len(ff) < 1e-3 ? Kit.facingOf(sp) : V.unit(ff);
            JobFx.bigWave(lv, null, b, ff, 0, 2.6, 45, 0.4, j, PRISM, PRISM_CORE, Opt.empty());
            JobFx.bigWave(lv, null, b, ff, 0, 2.6, -45, 0.4, j, PRISM, PRISM_CORE, Opt.empty());
            Ult.flash(lv, b, PRISM_CORE, Opt.empty(), sp);
            SkillFx.play(lv, "item.trident.thunder", b, 1.2, 0.6);
            SkillFx.play(lv, "entity.generic.explode", b, 0.9, 0.7);
            SkillFx.play(lv, "block.beacon.deactivate", b, 1.6, 0.6);
            if (t == null) return;
            Jobs.Job pj = tint(j, "prism", PRISM, PRISM_CORE);
            JobKit.strike(sp, t, pj, P, k.num("final", 28), true, true, life, 0, x -> JobImpact.hit(sp, t, pj, 2.5));
        });
        return true;
    }

    /** 칼라미티의 지금 과녁: 처음 겨눈 적 (살아 있으면) → 아니면 자리 둘레 3칸의 가장 가까운 적 (자리를 그 적으로 옮김) */
    private static LivingEntity pick(ServerPlayer sp, LivingEntity[] cur, Vec3[] spot) {
        LivingEntity t = cur[0];
        if (t != null && !t.isRemoved() && t.isAlive() && Kit.canHit(sp, t)) {
            spot[0] = t.position();
            return t;
        }
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : Kit.around(sp, spot[0], 3.0, 2.5)) {
            double d = e.position().distanceToSqr(spot[0]);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        cur[0] = best;
        if (best != null) spot[0] = best.position();
        return best;
    }

    /** jobs.json 의 글자 목록 (없으면 fallback) */
    private static List<String> strings(Jobs.Skill k, String key, List<String> fallback) {
        List<Object> l = k.def().listOrNull(key);
        if (l == null || l.isEmpty()) return fallback;
        List<String> out = new ArrayList<>();
        for (Object o : l) if (o instanceof String s && Elements.known(s)) out.add(s);
        return out.isEmpty() ? fallback : out;
    }
}
