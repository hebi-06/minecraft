package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import mmorpg.fabric.vfx.Vfx;
import org.joml.Quaternionf;

/**
 * 0.25.0 무투 룬 (monk — 민첩 1.0 + 힘 0.5, 두 스탯 계열): 맨손 무술 · 금빛 기. 주먹은 rpg:rfx_punch (날아가는 기의 주먹) · rpg:rfx_fist (앞에서 본 주먹 ·
 * 펼친 손바닥), 마무리 숫자는 굵은 금빛 !! (IMPACT).
 * 연환권 (주먹 셋 → 장풍) · 비각 (날아차기로 돌진해 처음 맞은 적을 차고 뛰어올라 내려찍음) · 붕권 (기를 모아 거대한 손바닥 장풍) · 백열권 (주먹 비 → 마지막 일격) ·
 * 천권 강림 (금빛 하늘 · 하늘에 맺힌 거대한 금빛 주먹 (블록 표시) 아래로 기의 주먹이 쏟아지고 거대한 주먹이 내리꽂힘).
 */
final class RuneMonk {
    private RuneMonk() {}

    private static final double[][] C = RuneKit.colorOf("monk");
    /** 금빛 기 · 흰 심지 · 짙은 주황 (그늘) */
    static final double[] GOLD = C[0];
    static final double[] CORE = C[1];
    static final double[] EMBER = {0.95, 0.42, 0.08};
    private static final int[] FIST = {0, 1};
    private static final int[] PALM = {2, 3};

    static void register() {
        RuneCast.register("chainFist", RuneMonk::chainFist);
        RuneCast.register("flyingKick", RuneMonk::flyingKick);
        RuneCast.register("palmBlast", RuneMonk::palmBlast);
        RuneCast.register("hundredFists", RuneMonk::hundredFists);
        RuneCast.register("heavenFist", RuneMonk::heavenFist);
    }

    // ------------------------------------------------------------ 공용: 주먹 그림

    /** 날아가는 기의 주먹 (rpg:rfx_punch — 그림의 오른쪽 = 나아가는 쪽): from 에서 dir 로 speed 칸/초, life 초 */
    static void punch(ServerLevel level, Vec3 from, Vec3 dir, double size, double speed, double life, double[] glow, double[] core) {
        RuneKit.rfx2(level, "punch", from, Opt.of("dir", dir, "speed", speed, "size", size, "life", life, "fade", 0.35, "alpha", 1.0), glow, core, FIST, null);
    }

    /** 맞은 자리의 주먹 (rpg:rfx_fist — 보는 사람을 향함) · 금빛 터짐 · 치명타 알갱이 */
    static void impact(ServerLevel level, Vec3 at, double size) {
        RuneKit.rfx2(level, "fist", at, Opt.of("size", size, "life", 0.2, "grow", 0.5, "fade", 0.6, "spin", (Kit.rand() - 0.5) * 30), GOLD, CORE, FIST, null);
        SkillFx.burst(level, at, GOLD, size * 1.2, 0.22);
        SkillFx.particle(level, ParticleTypes.CRIT, at, 8, 0.25, 0.4);
    }

    /** 3D 손바닥 크기: 2D 손바닥 그림의 반 크기 (칸) → 모델 크기 (높이 26.4 픽셀 — 그림보다 조금 크게) */
    static double palmScale(double halfSize) {
        return halfSize * 2 * 1.15 / (26.4 / 16.0);
    }

    /** 손 자리 (눈앞 아래 — side +1 오른손 · -1 왼손) */
    static Vec3 hand(ServerPlayer sp, int side) {
        Vec3 f = V.flat(sp.getViewVector(1F));
        return V.up(V.add(V.add(sp.getEyePosition(), f, 0.5), V.rightOf(f), 0.38 * side), -0.45);
    }

    // ------------------------------------------------------------ 일반: 연환권

    /**
     * 연환권: gap 틱마다 hits 번 왼 · 오른 주먹을 번갈아 내지름 (날아가는 기의 주먹 · 앞의 주먹 그림) — 앞 radius 칸 · cone 도의 적 (위력 × mult) →
     * 마지막에 손바닥 장풍 (위력 × crush, 밀쳐냄 knock)
     */
    static boolean chainFist(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        String life = RuneKit.lifeToken(sp);
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        double radius = k.num("radius");
        double cone = k.num("cone");
        boolean[] burst = {false}; // 0.50.0 2D 강화 (아래 등급 터짐): 장풍이 처음 맞힌 적에 한 번
        SkillFx.every(hits + 1, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            Vec3 view = sp.getViewVector(1F);
            Vec3 f = V.flat(view);
            Vec3 d = V.unit(new Vec3(f.x, Math.max(-0.3, Math.min(0.3, view.y)), f.z));
            boolean last = i == hits;
            if (!last) {
                int side = i % 2 == 0 ? 1 : -1;
                Vec3 h = hand(sp, side);
                fistFlight(level, h, d); // 2026-11-13 3D 기의 주먹 (fist — 2D 주먹 그림 대신)
                Vec3 end = V.add(V.up(c, 1.2), d, 2.6);
                RuneKit.later(2, () -> impact(level, V.add(end, V.rightOf(f), 0.25 * side), 0.42));
                SkillFx.ring(level, V.add(h, d, 1.6), d, GOLD, 0.3, 0.18, 2.0);
                SkillFx.play(level, "entity.player.attack.strong", c, 1.1 + i * 0.1, 0.8);
                SkillFx.play(level, "entity.player.attack.knockback", c, 1.3, 0.5);
            } else {
                // 장풍: 펼친 손바닥이 앞으로 밀려나가며 커짐 · 기의 고리 — 2026-11-13 3D 기의 손바닥 (palm 조각 물체 — 2D 손바닥 그림 대신, palmPush)
                Vec3 at = V.add(V.up(c, 1.2), d, 2.2);
                palmPush(level, V.add(V.up(c, 1.2), d, 1.1), d);
                for (int j = 0; j < 3; j++) {
                    final int jj = j;
                    RuneKit.later(j, () -> SkillFx.ring(level, V.add(at, d, 0.8 + jj * 0.9), d, jj % 2 == 0 ? GOLD : CORE, 0.55 + jj * 0.25, 0.3, 3.0));
                }
                SkillFx.play(level, "wind_charge.burst", c, 0.9, 0.9);
                SkillFx.play(level, "entity.player.attack.knockback", c, 0.7, 1.0);
            }
            // 0.49.0 기: 장풍까지 마치면 buffTicks 동안 가하는 피해 + buff (몸에 금빛 기운 · 상태 줄) — 장풍이 맞은 뒤 (맞은 적이 없으면 바로)
            boolean[] ki = {false};
            Runnable gainKi = () -> {
                if (ki[0] || !last || !(k.numOr("buff", 0) > 0) || !RuneKit.sameLife(sp, life)) return;
                ki[0] = true;
                RuneMarks.empower(sp, k.num("buff"), (int) k.num("buffTicks"), "기", GOLD, EMBER);
            };
            List<LivingEntity> fists = RuneKit.nearestTo(RuneKit.coneAround(sp, c, f, radius, cone), c);
            for (LivingEntity e : fists) {
                PairKit.hit(sp, r, e, P, last ? k.num("crush") : k.num("mult"), last ? DamageNumbers.Style.IMPACT : null, life, dmg -> {
                    Vec3 b = Kit.bodyPoint(e);
                    if (RuneKit.fxQuota("chainFist", 4)) impact(level, V.add(b, V.unit(V.sub(sp.getEyePosition(), b)), 0.3), (last ? 0.6 : 0.4) * RuneKit.nearK(sp, b, 4, 0.5));
                    if (last && !burst[0]) {
                        burst[0] = true;
                        RuneBoost.impact(level, e.position(), GOLD, CORE, 2.5, r.grade()); // 0.50.0 2D 강화 (아래 등급 터짐): 마무리 장풍이 처음 맞힌 적의 발밑
                    }
                    if (last && !PairKit.boss(e)) RuneKit.knock(e, f, k.numOr("knock", 0), 0.15);
                    if (last) RuneKit.later(1, gainKi);
                });
            }
            if (last && fists.isEmpty()) gainKi.run();
        });
        return true;
    }

    /**
     * 2026-11-13 연환권의 3D 기의 주먹 (fist — 치는 면이 앞 · 손등이 위): 손 앞 0.3칸에서 작게 나타나 2틱에 2.5칸 앞까지 내질러지며 커짐 → 3틱 동안 조금 더 나아가며
     * 작아져 사라짐
     */
    private static void fistFlight(ServerLevel level, Vec3 hand, Vec3 d) {
        double s = RuneProps.scaleFor(1.0, RuneProps.FIST_H);
        org.joml.Quaternionf q = RuneProps.along(V.scale(d, -1), V.cross(V.UP, d));
        Vec3 a0 = V.add(hand, d, 0.3);
        mmorpg.fabric.vfx.Vfx.Fx fx = RuneProps.fx(level, a0, 10);
        mmorpg.fabric.vfx.Vfx.Prop p = RuneProps.prop(fx, RuneProps.FIST, a0, RuneProps.FIST_FACE, q, s * 0.45, GOLD, CORE);
        fx.on(1, () -> p.to(V.add(hand, d, 2.6), null, s, 2));
        fx.on(3, () -> p.to(V.add(hand, d, 3.1), null, s * 0.15, 3));
        fx.on(6, p::remove);
    }

    /** 2026-11-13 연환권 마무리 장풍의 3D 기의 손바닥 (palm — 앞 (미는 면) = d): at 에서 작게 → 4틱에 2.4칸 밀려 나가며 커짐 → 작아지며 사라짐 */
    private static void palmPush(ServerLevel level, Vec3 at, Vec3 d) {
        mmorpg.fabric.vfx.Vfx.Fx fx = RuneProps.fx(level, at, 14);
        org.joml.Quaternionf q = RuneProps.facing(d, V.UP);
        mmorpg.fabric.vfx.Vfx.Group hand3d = RuneProps.shape(fx, RuneProps.PALM, at, q, palmScale(0.3), GOLD, CORE);
        fx.on(1, () -> hand3d.to(V.add(at, d, 2.4), null, palmScale(1.0), 4));
        fx.on(6, () -> hand3d.to(V.add(at, d, 3.0), null, palmScale(0.3), 4));
        fx.on(10, hand3d::remove);
    }

    // ------------------------------------------------------------ 희귀: 비각
    // ------------------------------------------------------------ 희귀: 비각

    /**
     * 0.49.0 비각 (돌진 → 도약 — 사용자 "도약 스킬 같은것도 많이"): 조준한 곳 (range 칸) 으로 air 틱 동안 뛰어올라 (금빛 잔상 · 앞으로 뻗은 발의 기 줄기) 내려오며
     * 날아차기 — 앞 kick 칸 부채꼴의 적 (위력 × mult) → 반동으로 살짝 떠올랐다가 10틱 뒤 내려찍기 (둘레 radius 칸 · 위력 × crush)
     */
    static boolean flyingKick(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 start = sp.position();
        Vec3 land = RuneKit.leapLand(sp, k.num("range"), 1.3);
        Vec3 f = V.hdist(land, start) > 0.3 ? V.flat(V.sub(land, start)) : V.flat(sp.getViewVector(1F));
        int air = (int) k.num("air");
        // 떠나는 자리의 잔상 · 나는 동안 잔상과 앞으로 뻗은 발의 기
        RuneKit.shadeClone(sp, level, start, f, 1, GOLD, CORE, Opt.of("life", 0.35, "alpha", 0.8));
        for (int t = 2; t < air; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 p = sp.position();
                RuneKit.shadeClone(sp, level, p, f, 1, GOLD, CORE, Opt.of("life", 0.25, "alpha", 0.55));
                Vec3 foot = V.add(V.up(p, 0.7), f, 0.9);
                RuneKit.streak(level, foot, V.add(foot, f, 1.2), EMBER, 0.3, 0.15, 0.95, 0);
            });
        }
        SkillFx.play(level, "item.enchant.lunge2", start, 1.0, 0.8);
        RuneKit.leap(sp, land, 1.9 + V.hdist(land, start) * 0.1, air, GOLD, EMBER, () -> {
            Vec3 c = sp.position();
            Vec3 foot = V.add(V.up(c, 0.9), f, 1.2);
            punch(level, V.add(V.up(c, 0.9), f, 0.3), f, 0.8, 10.0, 0.14, GOLD, CORE);
            impact(level, foot, 0.85);
            SkillFx.arc(level, foot, f, Opt.of("shape", "heavy", "flip", true, "size", 1.8, "life", 0.2, "speed", 2.0, "spin", 60.0, "color", GOLD, "aura", EMBER));
            SkillFx.play(level, "entity.player.attack.strong", c, 0.8, 1.0);
            SkillFx.play(level, "entity.goat.ram_impact", c, 1.2, 0.8);
            for (LivingEntity e : RuneKit.coneAround(sp, c, f, k.num("kick"), 100)) {
                PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                    impact(level, Kit.bodyPoint(e), 0.7);
                    if (!PairKit.boss(e)) Kit.knockback(e, f.x * 0.2, f.z * 0.2, 0.25);
                });
            }
            Kit.noFall(sp, 30);
            Kit.launch(sp, 0, 0.45, 0); // 반동으로 살짝 떠오름
            RuneKit.later(7, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Kit.launch(sp, 0, -1.2, 0);
                RuneKit.streak(level, V.up(sp.position(), 1.6), V.up(sp.position(), 0.2), GOLD, 0.4, 0.2);
            });
            RuneKit.later(10, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 g = PairKit.onGround(level, sp.position());
                double radius = k.num("radius");
                RuneKit.zonePulse(level, g, GOLD, radius, 0.45);
                RuneKit.novaRing(level, g, 0.6, radius * 0.9, GOLD, CORE, Opt.of("life", 0.4));
                RuneBoost.impact(level, g, GOLD, CORE, radius, r.grade()); // 0.50.0 2D 강화 (아래 등급 터짐): 내려찍은 자리
                RuneKit.glowCrack(level, g, radius * 0.8, GOLD, CORE, Opt.of("life", 1.0));
                RuneKit.rockBurst(level, V.up(g, 0.3), 5, 4, 0.2);
                SkillFx.particle(level, ParticleTypes.CRIT, V.up(g, 0.4), 20, radius * 0.3, 0.5);
                RuneKit.shakeAround(level, g, 8, 0.25, 0.25);
                SkillFx.play(level, "mace.smash_ground", g, 1.0, 1.0);
                SkillFx.play(level, "entity.player.attack.crit", g, 0.8, 1.0);
                for (LivingEntity e : Kit.around(sp, g, radius, 3)) {
                    PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.IMPACT, life, dmg -> {
                        impact(level, Kit.bodyPoint(e), 0.8);
                        if (!PairKit.boss(e)) Kit.knockback(e, 0, 0, 0.35);
                    });
                }
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 고귀: 붕권

    /**
     * 붕권: delay 틱 동안 기를 모음 (양손에 모여드는 금빛 알갱이 · 앞에서 커지는 손바닥 · 도는 고리) → 거대한 손바닥 장풍이 앞으로 밀려나가며
     * 앞 range 칸 · cone 도의 적을 한 번에 (위력 × mult, 밀쳐냄 knock — 보스 제외). 부채꼴로 퍼지는 기의 물결 · 땅의 빛줄기
     */
    static boolean palmBlast(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        int delay = (int) k.num("delay");
        double range = k.num("range");
        double cone = k.num("cone");
        SkillFx.play(level, "mob.evocation_illager.prepare_attack", sp.position(), 1.4, 0.7);
        SkillFx.play(level, "beacon.power", sp.position(), 1.6, 0.5);
        // 2026-11-12 3D 기의 손바닥 (사용자 "손 모양 3d로 연출" — tools/vfx_props.py palm, 빛 = 무투 계열 색): 몸 앞에서 커지며 따라옴 → 앞으로 밀려 나가며 커짐 → 사라짐
        Vec3 f0 = V.flat(sp.getViewVector(1F));
        mmorpg.fabric.vfx.Vfx.Fx palmFx = RuneProps.fx(level, sp.position(), delay + 20);
        // 2026-11-12 조각 물체 (사용자 "만년빙하처럼" — tools/vfx_shapes.py palm: 기울인 반투명 기운 조각 · 손바닥 가운데 빛)
        mmorpg.fabric.vfx.Vfx.Group hand3d = RuneProps.shape(palmFx, RuneProps.PALM, V.add(V.up(sp.position(), 1.15), f0, 1.5), RuneProps.facing(f0, V.UP),
                palmScale(0.25), GOLD, CORE);
        for (int t = 0; t < delay; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double q = (tt + 2) / (double) delay;
                Vec3 f = V.flat(sp.getViewVector(1F));
                Vec3 at = V.add(V.up(sp.position(), 1.15), f, 1.5);
                if (tt > 0) hand3d.to(at, RuneProps.facing(f, V.UP), palmScale(0.25 + 0.45 * q), 2);
                RuneKit.rfx(level, "fist", at, Opt.of("size", (0.25 + 0.45 * q) * 1.3, "life", 0.14, "alpha", 0.3 + 0.2 * q, "spin", tt * 3.0, "color", GOLD, "cell",
                        (double) PALM[0])); // 손바닥 둘레의 기 (2D 후광)
                SkillFx.ring(level, at, f, tt % 4 == 0 ? GOLD : CORE, 0.3 + 0.6 * q, 0.14, -1.5);
                for (int j = 0; j < 3; j++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 from = V.add(at, new Vec3(Math.cos(a) * 1.4, (Kit.rand() - 0.3) * 1.2, Math.sin(a) * 1.4), 1.0);
                    Vec3 dv = V.sub(at, from);
                    SkillFx.fx(level, "rpg:arrow_spark", from, Opt.of("dir", V.unit(dv), "speed", V.len(dv) / 0.2, "size", 0.12, "life", 0.2, "color", GOLD, "alpha",
                            1.0));
                }
                RuneKit.bodyAura(sp, GOLD, Opt.of("n", 4.0, "size", 0.9 + 0.3 * q, "alpha", 0.65));
            });
        }
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 c = sp.position();
            Vec3 view = sp.getViewVector(1F);
            Vec3 f = V.flat(view);
            Vec3 at = V.add(V.up(c, 1.15), f, 1.6);
            // 3D 손바닥이 앞으로 밀려 나감 (0.42초 — 커지며) → 작아지며 사라짐 · 둘레의 기 (2D 후광)
            Vec3 to = V.add(at, f, range * 1.4 * 0.42 * 0.75);
            hand3d.to(at, RuneProps.facing(f, V.UP), palmScale(1.0), 1);
            palmFx.on(palmFx.age() + 1, () -> hand3d.to(to, null, palmScale(2.2), 8));
            palmFx.on(palmFx.age() + 9, () -> hand3d.size(palmScale(0.4), 4));
            palmFx.on(palmFx.age() + 13, hand3d::remove);
            RuneKit.rfx(level, "fist", at, Opt.of("dir", f, "speed", range * 1.4, "size", 1.4 * 1.3, "life", 0.42, "grow", 0.6, "fade", 0.5, "alpha", 0.45, "color", GOLD,
                    "cell", (double) PALM[0]));
            for (int j = 0; j < 4; j++) {
                final int jj = j;
                RuneKit.later(j, () -> SkillFx.ring(level, V.add(at, f, 1.0 + jj * 1.6), f, jj % 2 == 0 ? GOLD : CORE, 1.0 + jj * 0.55, 0.32, 3.0));
            }
            // 부채꼴로 퍼지는 기의 물결 · 땅의 빛줄기
            for (int j = -3; j <= 3; j++) {
                Vec3 dj = RuneKit.turn(f, j * cone / 7);
                double off = 2.4 + Math.abs(j) * 0.3;
                SkillFx.fx(level, "rpg:light_cut", V.add(V.up(c, 0.08), dj, range * 0.5), Opt.of("dir", dj, "size", range * 0.45, "thick", 0.35, "life", 0.7, "delay",
                        0.45, "color", GOLD, "alpha", 0.9));
                RuneKit.later(Math.abs(j), () -> RuneKit.arc(level, V.add(V.up(c, 1.1), dj, off), dj, Opt.of("shape", "arc", "size", 2.0, "life", 0.24, "speed",
                        range * 1.2, "spin", 90.0, "color", GOLD, "aura", EMBER, "alpha", 0.85)));
            }
            SkillFx.fx(level, "rpg:shock_ring", V.up(V.add(c, f, 2.0), 0.08), Opt.of("dir", V.ZERO, "size", range * 0.35, "life", 0.45, "color", GOLD, "alpha", 0.9));
            SkillFx.particle(level, ParticleTypes.CLOUD, V.add(V.up(c, 0.4), f, 2.5), 20, 1.0, 0.25);
            SkillFx.shake(sp, 0.25, 0.25);
            SkillFx.play(level, "wind_charge.burst", c, 0.6, 1.0);
            SkillFx.play(level, "mob.warden.sonic_boom", c, 1.6, 0.5);
            SkillFx.play(level, "entity.player.attack.knockback", c, 0.6, 1.0);
            boolean[] burst = {false}; // 0.50.0 2D 강화 (아래 등급 터짐): 장풍이 처음 맞힌 적에 한 번
            for (LivingEntity e : RuneKit.coneTargets(sp, range, cone, 32)) {
                PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                    impact(level, Kit.bodyPoint(e), 1.0);
                    if (!burst[0]) {
                        burst[0] = true;
                        RuneBoost.impact(level, e.position(), GOLD, CORE, 2.5, r.grade()); // 0.50.0 2D 강화 (아래 등급 터짐): 장풍이 처음 맞힌 적의 발밑
                    }
                    if (!PairKit.boss(e)) RuneKit.knock(e, V.sub(e.position(), c), k.num("knock"), 0.35);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 영웅: 백열권

    /**
     * 2026-11-13 백열권의 3D 기의 화신 (rpg:rune_ki_avatar · 1.7배): 쓴 사람 뒤에서 기의 소용돌이로 솟아 (appear) 몸을 따라오며 주먹 비 (barrage — 되풀이)
     * 를 퍼붓고, 마지막 일격 틱 (flurry) 에 맞춰 크게 당겼다 쭉 뻗어 침 (finisher — KI_FINISH_AT 틱 먼저 틈) → 위로 기가 되어 흩어짐
     */
    private static void kiAvatar(ServerPlayer sp, String life, int flurry) {
        ServerLevel level = sp.level();
        java.util.function.Supplier<Vec3> spot = () -> {
            Vec3 f = Kit.facingOf(sp);
            return V.up(V.add(sp.position(), f, -1.35), 0.15);
        };
        RuneMob m = RuneMob.spawn(RuneMob.KI_AVATAR, level, spot.get(), Kit.facingOf(sp), flurry + 40, 1.7, true);
        if (m == null) return;
        m.anim("appear");
        m.follow(() -> {
            if (!sp.isRemoved()) m.face(Kit.facingOf(sp));
            return spot.get();
        }, 0.6, flurry + 12);
        RuneKit.later(6, () -> {
            if (m.alive()) m.hold("barrage");
        });
        RuneKit.later(Math.max(7, flurry - RuneMob.KI_FINISH_AT), () -> {
            if (m.alive()) m.anim("finisher");
        });
        RuneKit.later(flurry + 9, () -> m.leave("vanish", 9));
        SkillFx.burst(level, V.up(spot.get(), 1.2), GOLD, 1.6, 0.35);
    }

    /**
     * 백열권: 수많은 주먹 (2틱마다 날아가는 기의 주먹 셋 · 맞은 자리의 주먹 · 2026-11-13 뒤의 3D 기의 화신이 함께 주먹 비) 을 퍼부음 — 피해는 gap 틱마다 hits 번 앞 radius 칸 · cone 도의 적 (위력 × mult) →
     * 마지막 일격 (커다란 주먹 · 충격 고리 — 위력 × crush, 밀쳐냄 knock)
     */
    static boolean hundredFists(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        String life = RuneKit.lifeToken(sp);
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        double radius = k.num("radius");
        double cone = k.num("cone");
        int flurry = hits * gap;
        SkillFx.play(sp.level(), "item.enchant.lunge3", sp.position(), 1.2, 0.7);
        kiAvatar(sp, life, flurry);
        for (int t = 0; t < flurry; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 view = sp.getViewVector(1F);
                Vec3 f = V.flat(view);
                Vec3 c = sp.position();
                for (int j = 0; j < 3; j++) {
                    int side = (tt / 2 + j) % 2 == 0 ? 1 : -1;
                    Vec3 h = V.add(hand(sp, side), new Vec3((Kit.rand() - 0.5) * 0.5, (Kit.rand() - 0.4) * 0.6, (Kit.rand() - 0.5) * 0.5));
                    Vec3 d = V.unit(V.add(RuneKit.turn(f, (Kit.rand() - 0.5) * cone * 0.7), V.UP, (Kit.rand() - 0.5) * 0.4));
                    punch(level, V.add(h, d, 0.7), d, 0.55 + Kit.rand() * 0.2, 16.0, 0.14, j == 0 ? GOLD : CORE, CORE);
                }
                Vec3 spot = V.add(V.add(V.up(c, 1.0 + (Kit.rand() - 0.5) * 0.8), f, 2.0 + Kit.rand() * (radius - 2.0)), V.rightOf(f), (Kit.rand() - 0.5) * 2.4);
                impact(level, spot, 0.45 + Kit.rand() * 0.2);
                if (tt % 4 == 0) SkillFx.play(level, "entity.player.attack.strong", c, 1.3 + Kit.rand() * 0.4, 0.5);
                if (tt % 6 == 0) RuneKit.bodyAura(sp, GOLD, Opt.of("n", 4.0, "alpha", 0.6));
            });
        }
        SkillFx.every(hits, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            SkillFx.play(level, "entity.player.attack.crit", c, 1.0 + i * 0.08, 0.8);
            for (LivingEntity e : RuneKit.coneAround(sp, c, f, radius, cone)) {
                PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                    if (RuneKit.fxQuota("hundred", 4)) impact(level, Kit.bodyPoint(e), 0.6);
                });
            }
        });
        // 마지막 일격
        RuneKit.later(flurry, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            Vec3 view = sp.getViewVector(1F);
            Vec3 f = V.flat(view);
            Vec3 d = V.unit(new Vec3(f.x, Math.max(-0.2, Math.min(0.2, view.y)), f.z));
            punch(level, V.add(hand(sp, 1), d, 0.8), d, 1.5, 12.0, 0.25, GOLD, CORE);
            Vec3 at = V.add(V.up(c, 1.2), d, 2.6);
            RuneKit.later(2, () -> {
                impact(level, at, 1.5);
                for (int j = 0; j < 3; j++) SkillFx.ring(level, V.add(at, d, j * 0.8), d, j % 2 == 0 ? GOLD : CORE, 1.1 + j * 0.5, 0.3, 4.0);
            });
            RuneKit.zonePulse(level, c, GOLD, radius, 0.4);
            SkillFx.shake(sp, 0.3, 0.3);
            SkillFx.play(level, "mace.heavy_smash_ground", c, 1.2, 0.8);
            SkillFx.play(level, "entity.goat.ram_impact", c, 0.8, 1.0);
            SkillFx.play(level, "wind_charge.burst", c, 0.8, 0.8);
            boolean[] burst = {false}; // 0.50.0 2D 강화 (아래 등급 터짐): 마지막 일격이 처음 맞힌 적에 한 번
            for (LivingEntity e : RuneKit.coneAround(sp, c, f, radius, cone)) {
                PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.IMPACT, life, dmg -> {
                    impact(level, Kit.bodyPoint(e), 1.0);
                    if (!burst[0]) {
                        burst[0] = true;
                        RuneBoost.impact(level, e.position(), GOLD, CORE, 2.5, r.grade()); // 0.50.0 2D 강화 (아래 등급 터짐): 마지막 일격이 처음 맞힌 적의 발밑
                    }
                    if (!PairKit.boss(e)) RuneKit.knock(e, f, k.numOr("knock", 0), 0.2);
                    // 0.49.0 마지막 일격에 stun 틱 기절 (머리 위 금빛 룬)
                    if (k.numOr("stun", 0) > 0) RuneKit.stun(e, (int) k.num("stun"), GOLD, RuneKit.fxQuota("hundredStun", 8));
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 초월: 천권 강림

    /** 거대한 주먹이 떨어지는 틱 */
    private static final int SLAM = 8; // 2026-11-12 운석처럼 떨어지는 주먹 (4 → 8틱 — 떨어지는 모습이 보이게) · 블록 표시 주먹 (giantFist) 은 3D 주먹 (RuneProps.FIST) 으로

    /**
     * 천권 강림: 조준한 곳 (range 칸) 에 금빛 하늘 (3돌부터) · 금빛 영역 문양 · 빛기둥, 하늘에 거대한 금빛 주먹 (블록 표시 — 금 · 빛나는 마디) 이 delay 틱 동안
     * 맺힘 → gap 틱마다 hits 번 하늘에서 기의 주먹이 반지름 radius 칸의 적마다 쏟아짐 (위력 × mult) → 거대한 주먹이 내리꽂힘 (위력 × crush — 금빛 충격 고리 ·
     * 갈라짐 · 빛기둥, 띄움). 대상이 없어도 씀
     */
    static boolean heavenFist(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 aim = RuneKit.groundAim(sp, k.num("range"));
        double radius = k.num("radius");
        int delay = (int) k.num("delay");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int lastHit = delay + (hits - 1) * gap;
        int slamAt = lastHit + 16 - SLAM; // 내리꽂힘 = 마지막 주먹 비 + 16틱 (표의 t 그대로 — 떨어지는 틱이 늘어난 만큼 일찍 떨어지기 시작)
        int crushAt = slamAt + SLAM;
        int end = crushAt + 14;
        Vec3 f = V.flat(V.sub(aim, sp.position()));
        RuneKit.ultSkyFor(sp, level, aim, "heavenFist", end + 30, null);
        RuneKit.ultFlashFor(sp, level, aim, "heavenFist", "flash", Opt.empty());
        RuneKit.domainSigil(level, aim, radius, "holy", GOLD, CORE, Opt.of("life", end / 20.0, "spinRate", 30.0, "grow", 0.15, "alpha", 0.85, "fade", 0.15));
        RuneKit.selfCue(sp, GOLD, CORE, true);
        RuneKit.godRays(sp, level, aim, radius * 0.9, 6, GOLD, CORE, Opt.of("life", delay / 20.0 + 0.4, "h", 16.0, "alpha", 0.7));
        SkillFx.play(level, "beacon.activate", aim, 0.6, 1.0);
        SkillFx.play(level, "mob.evocation_illager.prepare_summon", sp.position(), 0.8, 0.8);
        SkillFx.play(level, "block.bell.hit", aim, 0.5, 1.0);
        // 몸의 금빛 기운 (쓰는 동안)
        for (int t = 0; t < crushAt; t += 2) {
            RuneKit.later(t, () -> {
                if (RuneKit.sameLife(sp, life)) RuneKit.bodyAura(sp, GOLD, Opt.of("n", 4.0, "alpha", 0.6));
            });
        }
        // 하늘의 거대한 주먹: 작게 맺혀 커짐 (조준한 곳 위 — 주먹 아래 끝이 땅에서 sky 칸)
        // 조준한 곳 너머 위 (1인칭에서도 보이게 — 눈에서 30° 안쪽)
        double sky = 3.6;
        Vec3 skyAt = V.add(V.up(aim, sky), f, 3.5);
        // 2026-11-12 3D 거대한 주먹 (사용자 "거대한 주먹 고퀄로 만든 후 마지막에 하늘에서 메테오처럼 떨어지는 연출로" — tools/vfx_props.py fist, 높이 7칸):
        // 손등이 쓴 사람 쪽 · 치는 면 아래로 하늘에 맺혀 커짐 → 위로 솟아 사라졌다가 → 먼 하늘에서 운석처럼 비스듬히 떨어져 박힘 → 빛으로 흩어짐 (작아짐)
        Vfx.Fx fistFx = RuneProps.fx(level, aim, end + 24);
        double fs = RuneProps.scaleFor(7.0, RuneProps.FIST_H);
        Vec3 toMe = V.scale(f, -1);
        Quaternionf q = Vfx.basis(V.unit(V.cross(V.UP, toMe)), V.UP, toMe);
        Vfx.Prop fist = RuneProps.prop(fistFx, RuneProps.FIST, skyAt, RuneProps.FIST_FACE, q, fs * 0.12, GOLD, CORE);
        fistFx.on(2, () -> fist.size(fs, delay - 2));
        for (int t = delay; t < slamAt - 6; t += 20) {
            final double bob = ((t - delay) / 20) % 2 == 0 ? 0.35 : 0;
            fistFx.on(t, () -> fist.move(V.up(skyAt, bob), 20));
        }
        RuneKit.skyFlare(level, V.up(skyAt, 2.5), 3.4, GOLD, CORE, Opt.of("life", delay / 20.0 + 0.2, "spinRate", 30.0));
        for (int t = 0; t < slamAt; t += 4) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                SkillFx.particle(level, ParticleTypes.END_ROD, V.up(skyAt, 2.0), 6, 1.6, 0.05);
                SkillFx.fx(level, "rpg:arrow_spark", V.up(skyAt, Kit.rand() * 4), Opt.of("dir", V.unit(new Vec3(Kit.rand() - 0.5, 0.2, Kit.rand() - 0.5)), "speed",
                        3.0, "size", 0.2, "life", 0.5, "color", GOLD, "alpha", 1.0));
            });
        }
        // 기의 주먹 비
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            RuneKit.later(delay + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                List<LivingEntity> ts = Kit.around(sp, aim, radius, 5);
                List<Vec3> spots = new ArrayList<>();
                for (int j = 0; j < Math.min(8, ts.size()); j++) spots.add(Kit.bodyPoint(ts.get(j)));
                for (int j = 0; j < 3; j++) {
                    double a = Kit.rand() * Math.PI * 2;
                    double rr = Math.sqrt(Kit.rand()) * radius;
                    spots.add(new Vec3(aim.x + Math.cos(a) * rr, aim.y + 0.4, aim.z + Math.sin(a) * rr));
                }
                for (Vec3 p : spots) {
                    Vec3 from = V.add(V.up(p, 7.0), new Vec3((Kit.rand() - 0.5) * 2, 0, (Kit.rand() - 0.5) * 2));
                    Vec3 dv = V.sub(p, from);
                    punch(level, from, V.unit(dv), 1.0, V.len(dv) / 0.2, 0.2, GOLD, CORE);
                }
                SkillFx.play(level, "entity.player.attack.strong", aim, 0.7 + ii * 0.05, 0.9);
                RuneKit.later(4, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    for (Vec3 p : spots) {
                        impact(level, V.up(p, 0.2), 0.7);
                        SkillFx.fx(level, "rpg:shock_ring", new Vec3(p.x, aim.y + 0.08, p.z), Opt.of("dir", V.ZERO, "size", 0.9, "life", 0.35, "color", GOLD, "alpha",
                                0.85));
                    }
                    RuneKit.zonePulse(level, aim, ii % 2 == 0 ? GOLD : CORE, radius, 0.4);
                    SkillFx.play(level, "mace.smash_ground", aim, 1.0 + ii * 0.04, 0.7);
                    for (LivingEntity e : ts) PairKit.hit(sp, r, e, P, k.num("mult"), life, null);
                });
            });
        }
        // 거대한 주먹: 위로 솟아 (6틱) → 먼 하늘 (조준한 곳 너머 위) 에서 운석처럼 비스듬히 떨어짐 (SLAM 틱 — 불꼬리 · 빛줄기) → 땅에 박힘
        Vec3 meteorFrom = V.add(V.up(aim, 22), f, 9);
        Vec3 fall = V.unit(V.sub(V.up(aim, 0.3), meteorFrom));
        Quaternionf down = RuneProps.along(V.scale(fall, -1), V.rightOf(f));
        RuneKit.later(slamAt - 6, () -> fist.to(V.up(skyAt, 14), null, fs * 0.7, 6));
        RuneKit.later(slamAt - 1, () -> fist.to(meteorFrom, down, fs * 1.1, 0));
        RuneKit.later(slamAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            for (int i = 1; i <= SLAM; i++) {
                double u = i / (double) SLAM;
                Vec3 at = V.add(meteorFrom, V.sub(V.up(aim, 0.3), meteorFrom), u * u);
                fistFx.on(fistFx.age() + i - 1, () -> fist.move(at, 1));
            }
            Opt mo = Opt.of("dir", fall, "speed", V.len(V.sub(V.up(aim, 0.3), meteorFrom)) / (SLAM / 20.0), "size", 4.2, "life", SLAM / 20.0, "frames", 4.0, "fps",
                    12.0, "loop", true);
            RuneBoost.aura(level, "meteor", V.add(meteorFrom, fall, -1.5), mo, GOLD, 1.35, 0.55); // 금빛 불꼬리
            Vec3 mid = V.up(aim, sky * 0.5);
            RuneKit.streak(level, meteorFrom, V.up(aim, 1), GOLD, 1.4, 0.3);
            SkillFx.play(level, "mob.enderdragon.flap", mid, 0.6, 1.0);
            SkillFx.play(level, "item.trident.thunder", mid, 1.4, 0.5);
            SkillFx.play(level, "mob.ghast.fireball", mid, 0.6, 1.0);
        });
        RuneKit.later(crushAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.ultFlashFor(sp, level, aim, "heavenFist", "flash", Opt.empty());
            RuneBoost.impact(level, aim, GOLD, CORE, radius); // 0.50.0 2D 강화 (초월 나머지): 거대한 주먹이 내리꽂힌 터짐
            double sc = RuneKit.nearK(sp, V.up(aim, 2), 8, 0.4);
            RuneKit.rfx2(level, "fist", V.up(aim, 2.2 * sc), Opt.of("size", 3.0 * sc, "life", 0.4, "grow", 0.8, "fade", 0.6), GOLD, CORE, FIST, null);
            RuneKit.novaRing(level, aim, 1.6, radius * 0.9, GOLD, CORE, Opt.of("life", 0.6));
            RuneKit.glowCrack(level, aim, radius, GOLD, CORE, Opt.of("life", 1.8, "v", 1.0));
            RuneKit.godRays(sp, level, aim, radius * 0.7, 8, GOLD, CORE, Opt.of("life", 0.9, "h", 18.0));
            SkillFx.fx(level, "rpg:shock_ring", V.up(aim, 0.08), Opt.of("dir", V.ZERO, "size", radius * 0.5, "life", 0.5, "color", GOLD, "alpha", 0.95));
            RuneKit.rockBurst(level, V.up(aim, 0.4), 10, 6, 0.3);
            SkillFx.particle(level, ParticleTypes.CRIT, V.up(aim, 1.0), 60, radius * 0.4, 0.8);
            RuneKit.shakeAround(level, aim, 22, 0.55, 0.55);
            SkillFx.play(level, "random.explode", aim, 0.5, 1.0);
            SkillFx.play(level, "mace.heavy_smash_ground", aim, 0.5, 1.0);
            SkillFx.play(level, "block.bell.hit", aim, 0.6, 1.0);
            for (LivingEntity e : Kit.around(sp, aim, radius, 5)) {
                PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.IMPACT, life, dmg -> {
                    if (!PairKit.boss(e)) RuneKit.knock(e, V.sub(e.position(), aim), 0.4, 0.6);
                    if (RuneKit.fxQuota("heavenCrush", 8)) impact(level, Kit.bodyPoint(e), 1.0);
                });
            }
        });
        // 주먹이 빛으로 흩어짐 (작아지며 가라앉음)
        RuneKit.later(crushAt + 6, () -> {
            fist.to(V.up(aim, -2.0), null, fs * 0.25, 10);
            fistFx.on(fistFx.age() + 10, fist::remove);
            SkillFx.particle(level, ParticleTypes.END_ROD, V.up(aim, 2), 40, 2.0, 0.15);
        });
        return true;
    }
}
