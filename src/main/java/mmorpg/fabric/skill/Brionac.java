package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 브류나크 (초월 창 — 12단계, 자바 전용): 광휘의 다섯 창 · 섬광 백열창. 수치는 SKILLS.lance, 색은 금빛 · 흰 심지.
 * 그림: rpg:rfx_relic 칸 0 (브류나크 모델 옆모습 — 날아가는 쪽으로 눕는 창, tools/java_fx_art.py), rfx_flare · rfx_rays (십자 빛), 활 빛줄기.
 * 쓰러지거나 차원을 옮기면 남은 것은 멈춤
 */
final class Brionac {
    private Brionac() {}

    /** rfx_relic · rfx_relicspin 의 브류나크 칸 (tools/java_fx_art.py RELIC 순서) */
    static final int RELIC_CELL = 0;
    /** rfx_relic 그림 칸 안의 창 길이 비율 (512점 칸에 약 488점) */
    static final double RELIC_LEN = 0.95;

    static void register() {
        Transcend.register("brionac_rays", Brionac::rays);
        Transcend.register("brionac_flurry", Brionac::flurry);
    }

    /** 날아가는 빛의 창 한 자루 (가운데 at, 창끝 방향 dir, 길이 len 칸, 초당 speed 칸으로 dir 쪽) */
    static void spear(ServerLevel level, Vec3 at, Vec3 dir, double len, double life, double speed, int cell, double alpha) {
        RuneKit.rfx(level, "relic", at, Opt.of("dir", dir, "size", len / RELIC_LEN, "life", life, "speed", speed, "cell", (double) cell, "alpha", alpha,
                "fade", 0.25));
    }

    /** 십자 빛 폭발: 별빛 (rfx_flare) 두 겹 · 세로 빛기둥 · 가로로 퍼지는 고리 · 불꽃 */
    static void crossBurst(ServerPlayer sp, Vec3 c, double[] glow, double[] core, double scale) {
        ServerLevel level = sp.level();
        double near = HeroKit.near(sp.getEyePosition(), c, 0.45) * scale;
        RuneKit.rfx2(level, "flare", c, Opt.of("size", 1.5 * near, "life", 0.32, "grow", 0.5, "fade", 0.6, "spin", 45.0), glow, core, new int[] {0, 1}, null);
        RuneKit.rfx2(level, "flare", c, Opt.of("size", 1.0 * near, "life", 0.26, "grow", 0.3, "fade", 0.6, "spin", 0.0), glow, core, new int[] {0, 1}, null);
        RuneKit.rfx(level, "rays", V.up(c, -0.9), Opt.of("size", 1.8 * near, "life", 0.4, "fade", 0.6, "color", glow, "alpha", 0.85, "cell", 0.0));
        SkillFx.ring(level, c, V.UP, glow, 1.1 * near, 0.3, 0);
        SkillFx.sparks(level, c, core, 6, 5);
        SkillFx.play(level, "random.glass", c, 1.8, 0.35);
        SkillFx.play(level, "chime.amethyst_block", c, 1.5, 0.9);
    }

    // ------------------------------------------------------------ 스킬 1 광휘의 다섯 창

    /**
     * 광휘의 다섯 창: 머리 위 · 등 뒤에 빛의 창 shots 자루가 부채꼴로 나타남 (windup 틱) → gap 틱마다 하나씩, 앞 cone° · range 칸 안의 보이는 적
     * (targets 명까지 — 창을 돌아가며 나눔, 한 명이면 모두 그 적에게) 에게 틱당 speed 칸으로 날아가 꽂힘 (mult) → 꽂힌 자리에 십자 빛 폭발.
     * 겨눌 적이 없으면 조준한 곳에 꽂히며 둘레 radius 칸의 적을 침. 대상이 없어도 씀 (재사용 대기)
     */
    static boolean rays(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        int shots = (int) k.num("shots");
        int windup = (int) k.num("windup");
        int gap = (int) k.num("gap");
        double range = k.num("range");
        List<LivingEntity> targets = targetsIn(sp, range, k.num("cone"), (int) k.num("targets"));
        Vec3 eye = sp.getEyePosition();
        Vec3 f = Kit.facingOf(sp);
        Vec3 right = V.rightOf(f);
        Vec3 aim = Kit.aimPoint(sp, range);
        // 부채꼴로 나타나는 창 (머리 뒤 위 — 1인칭 화면 위쪽 양옆에 보임)
        Vec3[] from = new Vec3[shots];
        for (int i = 0; i < shots; i++) {
            double a = Math.toRadians(-70 + 140.0 * i / Math.max(1, shots - 1));
            from[i] = V.add(V.add(V.add(eye, f, -0.4), right, Math.sin(a) * 1.7), V.UP, 0.75 + Math.cos(a) * 0.75);
            LivingEntity t = targets.isEmpty() ? null : targets.get(i % targets.size());
            Vec3 to = t != null ? Kit.bodyPoint(t) : aim;
            Vec3 d = V.unit(V.sub(to, from[i]));
            double hold = (windup + i * gap) / 20.0;
            final int ii = i;
            Tasks.later(i, () -> {
                if (!ok.getAsBoolean()) return;
                spear(level, from[ii], d, 1.6, hold - ii / 20.0 + 0.05, 0, RELIC_CELL, 0.95);
                SkillFx.burst(level, from[ii], glow, 0.5, 0.25);
                SkillFx.sparks(level, from[ii], core, 3, 2);
                SkillFx.play(level, "chime.amethyst_block", from[ii], 1.2 + ii * 0.12, 0.6);
            });
        }
        SkillFx.play(level, "beacon.power", sp.position(), 1.4, 0.6);
        SkillFx.play(level, "item.trident.riptide_1", sp.position(), 1.6, 0.5);
        RuneKit.domainSigil(level, sp.position(), 2.4, "holy", glow, core, Opt.of("life", 0.9, "spinRate", 60.0, "alpha", 0.75));
        for (int i = 0; i < shots; i++) {
            final int ii = i;
            Tasks.later(windup + i * gap, () -> {
                if (!ok.getAsBoolean()) return;
                LivingEntity t = targets.isEmpty() ? null : targets.get(ii % targets.size());
                if (t != null && !Kit.canHit(sp, t)) t = null;
                launch(sp, w, k, ok, from[ii], t, aim, glow, core);
            });
        }
        return true;
    }

    /** 앞 cone° · range 칸 안의 보이는 적: 조준한 적이 먼저, 그다음 화면 가운데에 가까운 순 (max 명) */
    static List<LivingEntity> targetsIn(ServerPlayer sp, double range, double cone, int max) {
        Vec3 eye = sp.getEyePosition();
        Vec3 view = sp.getViewVector(1F);
        double half = Math.toRadians(cone / 2);
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, sp.position(), range, 6)) {
            Vec3 d = V.sub(Kit.bodyPoint(e), eye);
            double len = V.len(d);
            if (len < 0.5 || Math.acos(Math.max(-1, Math.min(1, V.dot(d, view) / len))) > half + Math.asin(Math.min(1, Kit.bodyPad(e) / len))) continue;
            list.add(e);
        }
        list = Kit.visibleFrom(sp, eye, list);
        list.sort(Comparator.comparingDouble(e -> {
            Vec3 d = V.unit(V.sub(Kit.bodyPoint(e), eye));
            return -V.dot(d, view) + V.len(V.sub(e.position(), sp.position())) * 0.002;
        }));
        LivingEntity aim = Kit.pickTarget(sp, range, 0);
        if (aim != null && list.remove(aim)) list.add(0, aim);
        return list.size() > max ? new ArrayList<>(list.subList(0, max)) : list;
    }

    /** 창 하나가 날아감 → 꽂힘 */
    private static void launch(ServerPlayer sp, Weapon w, Obj k, BooleanSupplier ok, Vec3 from, LivingEntity t, Vec3 aim, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        Vec3 to = t != null ? Kit.bodyPoint(t) : aim;
        Vec3 d = V.sub(to, from);
        double len = Math.max(0.5, V.len(d));
        Vec3 u = V.unit(d);
        double speed = k.num("speed");
        int ticks = Math.max(1, (int) Math.ceil(len / speed));
        spear(level, V.add(from, u, 0.8), u, 1.8, ticks / 20.0 + 0.05, speed * 20, RELIC_CELL, 1.0);
        RuneKit.streak(level, from, to, glow, 0.16, 0.25, 0.85, 0.02);
        SkillFx.play(level, "item.trident.throw", from, 1.4, 0.7);
        Tasks.later(ticks, () -> {
            if (!ok.getAsBoolean()) return;
            if (t != null && Kit.canHit(sp, t)) {
                Kit.hitAt(sp, t, Kit.reserveHit(sp, t, Tasks.now()), ok, () -> {
                    if (Kit.skillHit(sp, t, w, k.num("mult")) > 0) crossBurst(sp, HeroKit.body(t, to), glow, core, 1);
                });
                crossBurst(sp, HeroKit.body(t, to), glow, core, 0.7);
                return;
            }
            // 겨눈 적이 없거나 쓰러짐: 그 자리 둘레 radius 칸의 적 (가장 가까운 하나)
            Vec3 at = t != null ? to : aim;
            crossBurst(sp, at, glow, core, 0.9);
            List<LivingEntity> near = Kit.around(sp, at, k.num("radius"), 2);
            near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(at)));
            if (!near.isEmpty()) {
                LivingEntity e = near.get(0);
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), ok, () -> Kit.skillHit(sp, e, w, k.num("mult")));
            }
        });
    }

    // ------------------------------------------------------------ 스킬 2 섬광 백열창

    /**
     * 섬광 백열창: ticks 틱 동안 thrusts 번 앞 cone° · range 칸으로 빛의 창을 내지름 (그림 — 창 잔상 · 찌르기 빛줄기) — 그 안의 보이는 적을 hits 번
     * 벰 (mult, 몹 무적 시간을 피해 적마다 틱을 잡음) → 마지막에 창끝에서 나선 빛줄기가 beam 칸 (벽 앞까지) 폭 width 를 꿰뚫음 (afterMult).
     * 찌르는 동안 보는 쪽이 바뀌면 그쪽을 찌름. 대상이 없어도 씀
     */
    static boolean flurry(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life) && Kit.holding(sp, w);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        int ticks = (int) k.num("ticks");
        int thrusts = (int) k.num("thrusts");
        int hits = (int) k.num("hits");
        double range = k.num("range");
        double cone = k.num("cone");
        RuneKit.selfCue(sp, glow, core, true);
        SkillFx.play(level, "item.trident.riptide_2", sp.position(), 1.5, 0.8);
        SkillFx.play(level, "beacon.activate", sp.position(), 1.8, 0.5);
        Set<UUID> seen = new HashSet<>();
        // 찌르기 thrusts 번을 ticks 틱에 고르게 (한 번에 양옆 둘 — 화면 가운데로 모이는 빛의 창의 비)
        for (int i = 0; i < thrusts; i++) {
            final int ii = i;
            Tasks.later((int) Math.round(i * (double) ticks / thrusts), () -> {
                if (!ok.getAsBoolean()) return;
                List<LivingEntity> aims = coneTargets(sp, range, cone);
                thrustFx(sp, range, cone, glow, core, ii * 2, aims);
                thrustFx(sp, range, cone, glow, core, ii * 2 + 1, aims);
            });
        }
        int hitEvery = Math.max(1, ticks / Math.max(1, hits));
        for (int h = 0; h < hits; h++) {
            Tasks.later(h * hitEvery, () -> {
                if (!ok.getAsBoolean()) return;
                long now = Tasks.now();
                for (LivingEntity e : coneTargets(sp, range, cone)) {
                    seen.add(e.getUUID());
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                        if (Kit.skillHit(sp, e, w, k.num("mult")) <= 0) return;
                        Vec3 c = HeroKit.body(e, sp.getEyePosition());
                        Vec3 v = V.unit(V.sub(c, sp.getEyePosition()));
                        double near = HeroKit.near(sp.getEyePosition(), c, 0.45);
                        SkillFx.fx(level, "rpg:slash_x", V.add(c, v, -0.3), Opt.of("dir", v, "size", 0.8 * near, "life", 0.2, "color", glow, "alpha", 1.0));
                        SkillFx.sparks(level, c, core, 4, 5);
                        SkillFx.play(level, "item.trident.hit", c, 1.3 + Kit.rand() * 0.3, 0.5);
                    });
                }
            });
        }
        Tasks.later(ticks + 2, () -> {
            if (ok.getAsBoolean()) spiral(sp, w, k, ok, glow, core);
        });
        return true;
    }

    /** 앞 cone° · range 칸 안의 보이는 적 */
    static List<LivingEntity> coneTargets(ServerPlayer sp, double range, double cone) {
        Vec3 pos = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double half = Math.toRadians(cone / 2);
        List<LivingEntity> in = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, pos, range, 3)) if (Kit.bodyInCone(e, pos, f, half)) in.add(e);
        return Kit.visibleFrom(sp, sp.getEyePosition(), in);
    }

    /**
     * 찌르기 하나: 눈 둘레 (양옆 0.5~1.3칸 · 위아래로 흩어진 자리 — i 가 짝수면 오른쪽) 에서 겨눈 적의 몸 (없으면 앞 부채꼴 안의 한 점) 으로 빛의 창이
     * 꽂히며 빛줄기가 남고 끝에서 번쩍 — 1인칭에서도 창이 비스듬히 화면 가운데로 모여 보이게 (앞으로 곧게 뻗으면 끝만 보임)
     */
    private static void thrustFx(ServerPlayer sp, double range, double cone, double[] glow, double[] core, int i, List<LivingEntity> aims) {
        ServerLevel level = sp.level();
        Vec3 eye = sp.getEyePosition();
        Vec3 view = sp.getViewVector(1F);
        Vec3 right = V.rightOf(Kit.facingOf(sp));
        double side = (i % 2 == 0 ? 1 : -1) * (0.5 + Kit.rand() * 0.8);
        Vec3 from = V.add(V.add(V.up(eye, -0.75 + Kit.rand() * 1.15), right, side), view, 0.9);
        Vec3 to;
        if (!aims.isEmpty()) {
            LivingEntity t = aims.get((i / 2) % aims.size());
            to = V.add(Kit.bodyPoint(t), new Vec3((Kit.rand() - 0.5) * 0.7, (Kit.rand() - 0.5) * 0.9, (Kit.rand() - 0.5) * 0.7));
        } else {
            Vec3 d = V.turnY(view, (Kit.rand() * 2 - 1) * cone * 0.4);
            to = V.add(eye, d, 3 + Kit.rand() * Math.max(0.5, range - 3));
        }
        Vec3 d = V.unit(V.sub(to, from));
        double len = Math.max(1.0, Math.min(V.len(V.sub(to, from)), Kit.rayDist(level, from, d, range)));
        Vec3 end = V.add(from, d, len);
        spear(level, from, d, 1.3, 0.12, len / 0.12 * 0.9, RELIC_CELL, 0.95);
        SkillFx.fx(level, "rpg:arrow_shot", V.add(from, d, len / 2), Opt.of("dir", d, "speed", 0.0, "size", len / 2, "thick", 0.14, "life", 0.18, "color", glow,
                "alpha", 0.9));
        SkillFx.fx(level, "rpg:arrow_shot", V.add(from, d, len / 2), Opt.of("dir", d, "speed", 0.0, "size", len / 2 * 0.96, "thick", 0.05, "life", 0.15, "color",
                core, "alpha", 1.0));
        double near = HeroKit.near(eye, end, 0.45);
        Tasks.later(2, () -> RuneKit.rfx2(level, "flare", V.add(end, d, -0.25), Opt.of("size", 0.55 * near, "life", 0.16, "grow", 0.4, "fade", 0.6, "spin",
                Kit.rand() * 90), glow, core, new int[] {0, 1}, null));
        if (i % 3 == 0) SkillFx.play(level, "item.spear.attack_miss", eye, 1.3 + Kit.rand() * 0.4, 0.5);
    }

    /** 마지막 나선 빛줄기 (afterMult) */
    private static void spiral(ServerPlayer sp, Weapon w, Obj k, BooleanSupplier ok, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        Vec3 eye = sp.getEyePosition();
        Vec3 dir = sp.getViewVector(1F);
        Vec3 from = V.add(V.up(eye, -0.3), dir, 1.0);
        double len = Math.max(1, Kit.rayDist(level, from, dir, k.num("beam")));
        Vec3 end = V.add(from, dir, len);
        // 빛줄기 (금빛 굵게 + 흰 심지) + 나선 (빛 알갱이 두 줄이 감음) + 큰 빛의 창이 끝까지 날아감
        SkillFx.beamAway(level, eye, from, end, glow, Opt.of("half", 0.5, "thick", 0.55, "life", 0.4, "sweep", 0.1, "gap", 0.8));
        SkillFx.beamAway(level, eye, from, end, core, Opt.of("half", 0.45, "thick", 0.18, "life", 0.35, "sweep", 0.1, "gap", 0.8));
        Vec3 side = V.unit(V.cross(dir, Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : V.UP));
        Vec3 up = V.unit(V.cross(side, dir));
        for (double s = 1.6; s < len; s += 0.45) {
            for (int j = 0; j < 2; j++) {
                double a = s * 1.6 + j * Math.PI;
                Vec3 p = V.add(V.add(V.add(from, dir, s), side, Math.cos(a) * 0.55), up, Math.sin(a) * 0.55);
                SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", dir, "speed", 0.5, "size", 0.12, "grow", 1.0, "life", 0.35 + s * 0.01, "color", j == 0 ? glow : core,
                        "alpha", 1.0));
            }
        }
        spear(level, V.add(from, dir, 2.0), dir, 3.2, Math.max(0.1, (len - 2) / 50.0), 50, RELIC_CELL, 1.0);
        SkillFx.play(level, "item.trident.thunder", eye, 1.6, 0.5);
        SkillFx.play(level, "beacon.deactivate", eye, 1.8, 0.6);
        SkillFx.shake(sp, 0.18, 0.2);
        long now = Tasks.now();
        for (Kit.LineHit h : Kit.lineHits(sp, from, dir, len, k.num("width"))) {
            LivingEntity e = h.e();
            Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now + (long) (h.t() / 2.5)), ok, () -> {
                if (Kit.skillHit(sp, e, w, k.num("afterMult")) > 0) crossBurst(sp, HeroKit.body(e, end), glow, core, 1.2);
            });
        }
        crossBurst(sp, end, glow, core, 1.4);
    }
}
