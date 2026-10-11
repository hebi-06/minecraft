package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 마력 룬 (runes.js "마력" — 파랑: 원소 마법): 화염구 · 서리 폭발 · 화염 폭풍 · 블리자드 · 메테오 */
final class RuneInt {
    private RuneInt() {}

    static void register() {
        RuneCast.register("fireball", RuneInt::fireball);
        RuneCast.register("frostNova", RuneInt::frostNova);
        RuneCast.register("fireStorm", RuneInt::fireStorm);
        RuneCast.register("blizzard", RuneInt::blizzard);
        RuneCast.register("meteor", RuneInt::meteor);
    }

    /** 화염구: 조준한 몹 (없으면 조준한 곳) 으로 불덩이 (rpg:rfx_fireball) 가 날아가 폭발 (rpg:rfx_boom, 반지름 radius, 불 fire 초) */
    static boolean fireball(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 head = sp.getEyePosition();
        Vec3 f = V.flat(sp.getViewVector(1F));
        double range = k.num("range");
        LivingEntity target = Kit.pickTarget(sp, range, 20);
        Vec3 to = target != null ? Kit.bodyPoint(target) : V.up(RuneKit.groundAim(sp, range), 0.5);
        Vec3 from = V.add(V.up(head, -0.25), V.rightOf(f), 0.3);
        Vec3 d = V.unit(V.sub(to, from));
        double len = V.len(V.sub(to, from));
        double life = Math.max(0.05, len / k.num("speed"));
        int ticks = Math.max(1, (int) Math.round(life * 20));
        double[] glow = RuneKit.FIRE[0];
        double[] core = RuneKit.FIRE[1];
        // 불덩이는 손에서 0.8칸 앞부터 (1인칭 화면 가운데를 덮지 않게)
        double s0 = Math.min(0.8, len * 0.5);
        RuneKit.rfx(level, "fireball", V.add(from, d, s0), Opt.of("dir", d, "speed", (len - s0) / life, "size", 0.5, "life", life, "frames", 4.0, "fps", 14.0,
                "loop", true, "phase", Kit.rand() * 4));
        for (double s = 0.9; s < len; s += 0.6) {
            double delay = (s / len) * life;
            SkillFx.fx(level, "rpg:arrow_trail", V.add(from, d, s), Opt.of("dir", d, "size", 0.34, "thick", 0.3, "life", delay + 0.32, "delay", delay,
                    "color", glow, "alpha", 0.75));
        }
        for (int t = 1; t < ticks; t += 2) {
            Vec3 p = V.add(from, d, (len * t) / ticks);
            Tasks.later(t, () -> SkillFx.vanilla(level, "minecraft:basic_flame_particle", p));
        }
        SkillFx.fx(level, "rpg:magic_circle_flash", from, Opt.of("size", 0.4, "life", 0.2, "spin", Kit.rand() * 360, "color", glow, "alpha", 1.0));
        SkillFx.play(level, "mob.blaze.shoot", head, 1.1, 0.8);
        Tasks.later(ticks, () -> {
            if (sp.isRemoved()) return;
            Vec3 at = target != null && !target.isRemoved() ? Kit.bodyPoint(target) : to;
            double sc = RuneKit.nearK(sp, at, 4, 0.4);
            double radius = k.num("radius");
            RuneKit.rfx(level, "boom", V.up(at, 0.2), Opt.of("size", radius * 0.75 * sc, "life", 0.55, "frames", 8.0, "fps", 15.0));
            SkillFx.burst(level, at, glow, 1.6 * sc, 0.3);
            SkillFx.ring(level, at, d, core, 0.9, 0.3, 0);
            SkillFx.sparks(level, at, glow, 10, 5);
            RuneKit.zonePulse(level, new Vec3(at.x, at.y - 0.9, at.z), glow, radius, 0.4);
            SkillFx.vanilla(level, "minecraft:lava_particle", at);
            SkillFx.play(level, "random.explode", at, 1.4, 0.5);
            SkillFx.play(level, "fire.ignite", at, 1.0, 1);
            for (LivingEntity e : Kit.around(sp, at, radius, 3)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) RuneKit.burn(e, k.num("fire"));
            }
        });
        return true;
    }

    /**
     * 서리 폭발: 주위를 얼려 피해와 둔화 — 얼음 결정 (rpg:rfx_crystal) 이 몸 둘레에서 바깥으로 네 겹 솟고 (쓴 사람 1.4칸 안은 비움),
     * 고드름 (rpg:rfx_icicle) 이 사방으로 튐. 맞은 적은 발밑이 얼음 결정에 갇힘 (둔화 동안)
     */
    static boolean frostNova(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double[] glow = RuneKit.ICE[0];
        double[] core = RuneKit.ICE[1];
        double radius = k.num("radius");
        RuneKit.zonePulse(level, c, glow, radius, 0.5);
        RuneKit.zonePulse(level, c, core, radius * 0.6, 0.35);
        SkillFx.rune(level, c, Opt.of("size", radius * 0.8, "life", 0.9, "color", glow, "alpha", 0.85, "rate", -60.0, "grow", 0.3));
        for (int i = 0; i < 6; i++) {
            double a = (i / 6.0) * Math.PI * 2 + 0.2;
            Vec3 d = V.unit(new Vec3(Math.cos(a), 0.12, Math.sin(a)));
            RuneKit.rfx2(level, "icicle", V.add(V.up(c, 0.7), d, 1.0), Opt.of("dir", d, "speed", 10.0, "size", 0.36, "life", 0.32), i % 2 == 1 ? core : glow,
                    RuneKit.WHITE);
        }
        // 얼음 결정 네 겹 (1 ~ 4틱, 틱마다 한 겹씩 바깥으로)
        double[] rings = {1.6, 2.4, 3.2, 4.0};
        for (int j = 0; j < rings.length; j++) {
            final int jj = j;
            final double r0 = rings[j];
            RuneKit.later(j + 1, () -> {
                double rr = Math.min(r0, radius * 0.95);
                int n = 4 + jj * 2;
                double a0 = Kit.rand() * Math.PI * 2;
                Vec3 p0 = !sp.isRemoved() ? sp.position() : c;
                for (int i = 0; i < n; i++) {
                    double a = a0 + ((double) i / n) * Math.PI * 2;
                    Vec3 at = new Vec3(c.x + Math.cos(a) * rr, c.y, c.z + Math.sin(a) * rr);
                    if (Math.hypot(at.x - p0.x, at.z - p0.z) < 1.4) continue;
                    RuneKit.iceCrystal(sp, level, at, (0.42 + jj * 0.08) * (0.8 + Kit.rand() * 0.4), glow, Opt.of("life", 1.1 - jj * 0.1, "fade", 0.35));
                }
            });
        }
        SkillFx.burst(level, V.up(c, 1), core, 1.4, 0.3);
        SkillFx.sparks(level, V.up(c, 0.8), core, 8, 4);
        for (int i = 0; i < 6; i++) {
            double a = Kit.rand() * Math.PI * 2;
            double rr = Kit.rand() * radius;
            SkillFx.vanilla(level, "minecraft:snowflake_particle", new Vec3(c.x + Math.cos(a) * rr, c.y + 1 + Kit.rand(), c.z + Math.sin(a) * rr));
        }
        SkillFx.play(level, "random.glass", c, 1.2, 0.8);
        SkillFx.play(level, "mob.player.hurt_freeze", c, 0.9, 0.8);
        List<LivingEntity> frozen = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, c, radius)) {
            if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                RuneKit.effect(e, "slowness", (int) k.num("slowTicks"), (int) k.num("slowAmp"));
                if (frozen.size() < 20) SkillFx.burst(level, Kit.bodyPoint(e), glow, 0.7, 0.3); // 번쩍임은 20명까지 (파티클 예산)
                frozen.add(e);
            }
        }
        // 얼어붙음: 결정 고리가 다 솟은 뒤 (5틱), 가까운 6명까지 (파티클 예산)
        List<LivingEntity> nearest = RuneKit.nearestTo(frozen, c);
        List<LivingEntity> six = nearest.subList(0, Math.min(6, nearest.size()));
        RuneKit.later(5, () -> {
            for (LivingEntity e : six) if (!e.isRemoved()) encase(sp, level, e, glow, 1.2);
        });
        return true;
    }

    /** 얼어붙음: 적의 발밑 둘레에 얼음 결정 셋 (seconds 초 — 둔화된 적이 천천히 빠져나가므로 짧게, 몸 크기만큼 벌어짐) */
    private static void encase(ServerPlayer sp, ServerLevel level, LivingEntity e, double[] glow, double seconds) {
        double pad = 0.35 + Kit.bodyPad(e);
        double a0 = Kit.rand() * Math.PI * 2;
        for (int i = 0; i < 3; i++) {
            double a = a0 + (i / 3.0) * Math.PI * 2;
            Vec3 at = new Vec3(e.getX() + Math.cos(a) * pad, e.getY(), e.getZ() + Math.sin(a) * pad);
            RuneKit.iceCrystal(sp, level, at, 0.55 + Kit.bodyPad(e) * 0.5, glow, Opt.of("life", seconds, "fade", 0.15, "grow", 0.05, "v", (double) (i % 3),
                    "alpha", 0.85));
        }
    }

    /**
     * 화염 폭풍: 조준한 곳에 불기둥 소용돌이 (gap 틱마다 hits 번, 가운데로 끌어당김) — 가운데 거대한 불 회오리 (rpg:rfx_vortex) · 밑동의 불꽃 +
     * 둘레를 도는 작은 불 회오리 셋 + 바깥을 휘감는 불길, 마지막에 폭발 (rpg:rfx_boom)
     */
    static boolean fireStorm(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        double radius = k.num("radius");
        Vec3 g = RuneKit.groundAim(sp, k.num("range"));
        double[] glow = RuneKit.FIRE[0];
        double[] core = RuneKit.FIRE[1];
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int total = (hits - 1) * gap + 10;
        double sc = RuneKit.nearK(sp, V.up(g, 2.5), 6, 0.25); // 가까이 겨누면 작게 (화면을 덮지 않게)
        SkillFx.rune(level, g, Opt.of("size", radius, "life", total / 20.0 + 0.3, "color", glow, "alpha", 0.8, "rate", 90.0, "grow", 0.0));
        SkillFx.play(level, "mob.ghast.fireball", g, 0.8, 1);
        SkillFx.play(level, "fire.ignite", g, 0.8, 1);
        for (int t = 0; t < total; t += 3) {
            final int tt = t;
            Tasks.later(t, () -> {
                double a = tt * 0.55;
                boolean last = tt + 3 >= total;
                // 바깥을 휘감는 불길 (피해 범위 가장자리 가까이)
                Vec3 p = new Vec3(g.x + Math.cos(a) * radius * 0.8, g.y + 0.9 + (tt % 9) * 0.1, g.z + Math.sin(a) * radius * 0.8);
                Vec3 dir = new Vec3(Math.cos(a + Math.PI / 2), 0, Math.sin(a + Math.PI / 2));
                SkillFx.arc(level, p, dir, Opt.of("shape", "heavy", "flip", false, "size", 2.2 * sc, "life", 0.32, "speed", 2.0, "spin", 70.0, "color", glow,
                        "core", tt % 6 == 0));
                // 둘레를 도는 작은 불 회오리 셋 (반대로 돎 — 한 틱에 0.22 라디안, 다시 그리는 사이 원을 따라 미끄러짐)
                double r2 = radius * 0.62;
                for (int j = 0; j < 3; j++) {
                    double b = -tt * 0.22 + (j * Math.PI * 2) / 3;
                    Vec3 q = new Vec3(g.x + Math.cos(b) * r2, g.y, g.z + Math.sin(b) * r2);
                    Vec3 tang = new Vec3(Math.sin(b), 0, -Math.cos(b));
                    RuneKit.vortexAt(level, q, (0.9 + 0.15 * j) * sc, j > 0 ? glow : core, Opt.of("life", 0.2, "alpha", 0.85, "fade", last ? 0.5 : 0.05, "phase",
                            (double) ((tt + j * 3) % 8), "vel", V.scale(tang, r2 * 4.4)));
                }
                if (tt % 6 == 0) {
                    // 가운데 거대한 불 회오리 (다음까지 이어짐) + 밑동에서 날름거리는 불꽃 둘
                    RuneKit.vortexAt(level, g, 2.5 * sc, glow, Opt.of("life", 0.36, "alpha", 0.95, "fps", 22.0, "fade", tt + 6 >= total ? 0.5 : 0.0, "grow",
                            tt == 0 ? 0.25 : 0.0, "phase", (double) (tt % 8)));
                    for (int j = 0; j < 2; j++) {
                        double b = tt * 0.9 + j * Math.PI;
                        SkillFx.fx(level, "rpg:fire_wall", new Vec3(g.x + Math.cos(b) * 0.5 * sc, g.y, g.z + Math.sin(b) * 0.5 * sc), Opt.of("size", 1.5 * sc,
                                "life", 0.45, "color", j > 0 ? core : glow, "alpha", 0.9, "spin", Kit.rand() * 360));
                    }
                }
                if (tt % 9 == 0) {
                    double b = Kit.rand() * Math.PI * 2;
                    SkillFx.fx(level, "rpg:arrow_spark", new Vec3(g.x + Math.cos(b) * 0.8, g.y + 1, g.z + Math.sin(b) * 0.8), Opt.of("dir",
                            V.unit(new Vec3(Math.cos(b), 2.2, Math.sin(b))), "speed", 6.0, "size", 0.3, "grow", 0.8, "life", 0.6, "color", core, "alpha", 1.0));
                }
                SkillFx.vanilla(level, "minecraft:basic_flame_particle", new Vec3(g.x + (Kit.rand() - 0.5) * radius, g.y + 0.4,
                        g.z + (Kit.rand() - 0.5) * radius));
            });
        }
        SkillFx.every(hits, gap, i -> {
            RuneKit.zonePulse(level, g, i % 2 == 1 ? core : glow, radius, 0.4);
            SkillFx.play(level, "fire.fire", g, 1.0, 0.9);
            if (sp.isRemoved()) return;
            for (LivingEntity e : Kit.around(sp, g, radius, 4)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                    RuneKit.knock(e, V.sub(g, e.position()), k.num("pull"), 0.05);
                    RuneKit.burn(e, 2);
                }
            }
            if (i == hits - 1) {
                RuneKit.rfx(level, "boom", V.up(g, 1.0), Opt.of("size", radius * 0.8 * sc, "life", 0.55, "frames", 8.0, "fps", 15.0));
                SkillFx.burst(level, V.up(g, 1), glow, 2.2, 0.35);
                SkillFx.sparks(level, V.up(g, 1), core, 12, 6);
                SkillFx.play(level, "random.fizz", g, 0.8, 1);
                SkillFx.play(level, "random.explode", g, 1.1, 0.6);
            }
        });
        return true;
    }

    /** 눈구름 덩이 하나 (가운데에서 x · z, 크기 s, 그림 칸) */
    private record Puff(double x, double z, double s, int cell) {}

    /** 블리자드: 조준한 곳 위에 눈구름 (rpg:rfx_cloud) 이 끼고 눈보라 (gap 틱마다 hits 번 피해 + 둔화), 얼음 조각 (rpg:rfx_icicle) 이 비스듬히 쏟아짐 */
    static boolean blizzard(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        double radius = k.num("radius");
        Vec3 g = RuneKit.groundAim(sp, k.num("range"));
        double[] glow = RuneKit.ICE[0];
        double[] core = RuneKit.ICE[1];
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int total = (hits - 1) * gap + 8;
        double[] snow = {0.9, 0.95, 1.0};
        SkillFx.rune(level, g, Opt.of("size", radius, "life", total / 20.0 + 0.3, "color", glow, "alpha", 0.8, "rate", -30.0, "grow", 0.0));
        SkillFx.play(level, "mob.player.hurt_freeze", g, 0.7, 1);
        SkillFx.play(level, "random.glass", g, 0.7, 0.8);
        Vec3 wind = V.unit(new Vec3(0.35, -1, 0.2));
        Vec3 sky = V.up(g, 6.2);
        // 눈구름: 덩이 다섯 (10틱마다 다시 그림, 조금씩 흘러감)
        List<Puff> puffs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            double a = (i / 5.0) * Math.PI * 2 + Kit.rand();
            double rr = i == 0 ? 0 : radius * 0.6;
            puffs.add(new Puff(Math.cos(a) * rr, Math.sin(a) * rr, i == 0 ? 2.6 : 1.9, i % 4));
        }
        for (int t = 0; t < total; t += 10) {
            final int tt = t;
            RuneKit.later(t, () -> {
                for (Puff p : puffs) {
                    RuneKit.rfx(level, "cloud", new Vec3(sky.x + p.x() + tt * 0.02, sky.y + (p.s() > 2 ? 0.5 : 0), sky.z + p.z()), Opt.of("size", p.s(), "life",
                            tt == 0 ? 0.62 : 0.56, "cell", (double) p.cell(), "color", snow, "alpha", 0.9, "fade", tt + 10 >= total ? 0.5 : 0.0));
                }
            });
        }
        for (int t = 0; t < total; t += 3) {
            final int tt = t;
            Tasks.later(t, () -> {
                for (int j = 0; j < 4; j++) {
                    double a = Kit.rand() * Math.PI * 2;
                    double rr = Math.sqrt(Kit.rand()) * radius;
                    Vec3 ground = new Vec3(g.x + Math.cos(a) * rr, g.y, g.z + Math.sin(a) * rr);
                    Vec3 top = V.add(ground, wind, -5.5);
                    RuneKit.rfx2(level, "icicle", top, Opt.of("dir", wind, "speed", 20.0, "size", 0.42, "life", 0.27), j % 2 == 1 ? core : glow, RuneKit.WHITE);
                }
                if (tt % 6 == 0) RuneKit.zonePulse(level, V.up(g, 1.1 + (tt % 12) * 0.08), core, radius * 0.9, 0.5); // 떠도는 눈보라 고리
                SkillFx.vanilla(level, "minecraft:snowflake_particle", new Vec3(g.x + (Kit.rand() - 0.5) * radius * 1.6, g.y + 1.5 + Kit.rand(),
                        g.z + (Kit.rand() - 0.5) * radius * 1.6));
            });
        }
        SkillFx.every(hits, gap, i -> {
            RuneKit.zonePulse(level, g, i % 2 == 1 ? core : glow, radius, 0.45);
            SkillFx.play(level, "step.powder_snow", g, 0.8, 1);
            if (sp.isRemoved()) return;
            for (LivingEntity e : Kit.around(sp, g, radius, 4)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                    RuneKit.effect(e, "slowness", (int) k.num("slowTicks"), (int) k.num("slowAmp"));
                    if (i % 2 == 0) {
                        SkillFx.fx(level, "rpg:ice_spike", e.position(), Opt.of("size", 0.9, "life", 0.5, "spin", Kit.rand() * 30 - 15, "color", glow,
                                "alpha", 0.9));
                    }
                }
            }
        });
        return true;
    }

    /**
     * 메테오: 조준한 곳에 불의 룬이 그려지고 (delay) 뒤쪽 하늘에서 불타는 운석 (rpg:rfx_meteor) 이 연기 꼬리를 끌며 떨어져 폭발 (rpg:rfx_boom).
     * 초월 2차 강화: 불타는 하늘 · 비전 문양 · 불타며 갈라지는 하늘 · 주황 번쩍임 · 충격 고리 · 용암처럼 빛나는 금 · 솟는 버섯구름
     */
    static boolean meteor(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 g = RuneKit.groundAim(sp, k.num("range"));
        Vec3 f = V.flat(sp.getViewVector(1F));
        double[] glow = RuneKit.FIRE[0];
        double[] core = RuneKit.FIRE[1];
        double[] red = {1.0, 0.2, 0.1};
        int delay = (int) k.num("delay");
        double radius = k.num("radius");
        for (int t = 0; t < delay; t += 2) {
            final int tt = t;
            Tasks.later(t, () -> {
                SkillFx.rune(level, g, Opt.of("size", radius, "life", 0.14, "color", glow, "alpha", 0.9, "progress", Math.min(1, (tt + 2) / (delay * 0.7)), "rate",
                        0.0, "spin", -tt * 3.0));
                if (tt % 8 == 0) RuneKit.zonePulse(level, g, red, radius, 0.35); // 떨어질 자리 (피해 범위 그대로)
            });
        }
        SkillFx.play(level, "mob.blaze.shoot", sp.getEyePosition(), 0.6, 1);
        SkillFx.play(level, "fire.fire", g, 0.7, 1);
        // 2차 강화 — 예고: 불타는 하늘 · 겨눈 땅의 비전 문양 (불빛, 커지며 거꾸로 돎) · 운석이 오기 전에 앞쪽 하늘이 불타며 갈라짐
        RuneKit.ultSkyFor(sp, level, g, "meteor", delay + 46, null);
        RuneKit.domainSigil(level, g, radius * 1.15, "arcane", glow, core, Opt.of("life", (delay + 1) / 20.0, "spinRate", -30.0, "grow", 0.3, "alpha", 0.78,
                "fade", 0.08));
        Vec3 burning = RuneKit.skyPoint(sp, g, 6, 21, 6, 22);
        RuneKit.later(Math.max(0, delay - 22), () -> RuneKit.skyPlate(sp, level, "crack", burning, 6.4, red, core, Opt.of("life", 1.4, "grow", 0.45, "fade", 0.35,
                "cell", (double) RuneKit.CRACK_V)));
        // 운석: 뒤쪽 하늘에서 비스듬히 (마지막 16틱) — 지나간 자리에 연기가 피어오름
        int fall = 16;
        Vec3 start = V.add(V.up(g, 22), f, -10);
        Vec3 d = V.unit(V.sub(g, start));
        double len = V.len(V.sub(g, start));
        Tasks.later(delay - fall, () -> {
            RuneKit.rfx(level, "meteor", start, Opt.of("dir", d, "speed", len / (fall / 20.0), "size", 3.2, "life", fall / 20.0, "frames", 4.0, "fps", 12.0,
                    "loop", true));
            for (int t = 0; t < fall; t += 2) {
                final int tt = t;
                Tasks.later(t, () -> {
                    Vec3 p = V.add(start, d, (len * tt) / fall);
                    SkillFx.vanilla(level, "minecraft:lava_particle", p);
                    RuneKit.rfx(level, "cloud", V.add(p, d, -2.5), Opt.of("size", 1.3, "life", 1.1, "grow", 1.4, "cell", (double) (tt % 4), "color",
                            new double[] {0.32, 0.28, 0.27}, "alpha", 0.7, "fade", 0.7));
                });
            }
            SkillFx.play(level, "mob.ghast.fireball", g, 0.5, 1);
        });
        Tasks.later(delay, () -> {
            if (sp.isRemoved()) return;
            Vec3 mid = V.up(g, 1);
            double sc = RuneKit.nearK(sp, mid, 8, 0.35);
            // 발동: 주황 번쩍임 · 갈라진 하늘의 섬광 → 충격 고리 (1틱 뒤) → 용암처럼 빛나는 금 (2틱 뒤) → 솟는 버섯구름 (3 · 5 · 7틱 뒤)
            RuneKit.ultFlashFor(sp, level, g, "meteor", "flash", Opt.empty());
            RuneKit.skyFlare(level, burning, 4.4, glow, core, Opt.of("life", 0.6));
            RuneKit.later(1, () -> RuneKit.novaRing(level, g, 2.4, 3.0, glow, core, Opt.of("life", 0.5)));
            RuneKit.later(2, () -> RuneKit.glowCrack(level, g, radius * 1.15, new double[] {1.0, 0.38, 0.08}, new double[] {1.0, 0.86, 0.5}, Opt.of("life", 2.8,
                    "fade", 0.6, "v", 1.0)));
            for (int i = 0; i < 3; i++) {
                final int ii = i;
                RuneKit.later(3 + i * 2, () -> RuneKit.rfx(level, "cloud", V.up(g, 2.5 + ii * 2.6), Opt.of("dir", V.UP, "speed", 2.2, "size",
                        (2.0 + ii * 0.9) * sc, "life", 1.8, "grow", 0.7, "cell", (double) (ii % 4), "color", new double[] {0.36, 0.26, 0.22}, "alpha", 0.75,
                        "fade", 0.6)));
            }
            RuneKit.rfx(level, "boom", V.up(g, radius * 0.45 * sc), Opt.of("size", radius * 0.95 * sc, "life", 0.9, "frames", 8.0, "fps", 9.0));
            for (int i = 0; i < 4; i++) {
                double a = (i / 4.0) * Math.PI * 2 + 0.6;
                Vec3 at = new Vec3(g.x + Math.cos(a) * radius * 0.6, g.y + 1.0, g.z + Math.sin(a) * radius * 0.6);
                RuneKit.later(2 + i, () -> RuneKit.rfx(level, "boom", at, Opt.of("size", radius * 0.42 * sc, "life", 0.6, "frames", 8.0, "fps", 14.0)));
            }
            SkillFx.burst(level, mid, glow, 6.0 * sc, 0.55);
            SkillFx.burst(level, mid, red, 4.0 * sc, 0.45);
            SkillFx.burst(level, mid, core, 2.6 * sc, 0.35);
            SkillFx.fx(level, "rpg:arrow_spark", mid, Opt.of("dir", V.UP, "size", 4 * sc, "grow", 2.0, "life", 0.22, "color", RuneKit.WHITE, "alpha", 1.0));
            for (int i = 0; i < 5; i++) {
                final int ii = i;
                Tasks.later(i * 2, () -> SkillFx.shock(level, g, ii % 2 == 1 ? red : glow, 2 + ii * 1.5, 0.55 + ii * 0.1));
            }
            for (int i = 0; i < 10; i++) {
                Vec3 dd = RuneKit.turn(new Vec3(1, 0, 0), i * 36 + Kit.rand() * 12);
                SkillFx.fx(level, "rpg:light_cut", V.add(V.up(g, 0.08), dd, 2.4), Opt.of("dir", dd, "size", 2.2, "thick", 0.4, "life", 1.2, "delay", 0.9, "color",
                        glow, "alpha", 0.95));
            }
            // 불타는 땅: 둘레 불꽃 · 갈라진 땅 · 튀는 바위
            for (int i = 0; i < 8; i++) {
                double a = (i / 8.0) * Math.PI * 2 + Kit.rand() * 0.3;
                SkillFx.fx(level, "rpg:fire_wall", new Vec3(g.x + Math.cos(a) * radius * 0.75, g.y, g.z + Math.sin(a) * radius * 0.75), Opt.of("size", 1.8, "life",
                        1.2, "color", glow, "alpha", 0.95, "spin", Kit.rand() * 360));
            }
            SkillFx.fx(level, "rpg:ground_crack", V.up(g, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 1.3, "life", 2.0, "color",
                    new double[] {0.3, 0.08, 0.02}, "alpha", 0.95));
            for (int i = 0; i < 8; i++) {
                double a = (i / 8.0) * Math.PI * 2 + Kit.rand() * 0.4;
                SkillFx.fx(level, "rpg:boulder", V.up(g, 0.6), Opt.of("dir", V.unit(new Vec3(Math.cos(a), 1.2, Math.sin(a))), "speed", 8 + Kit.rand() * 3, "size",
                        0.32, "life", 0.8, "spin", Kit.rand() * 360, "color", new double[] {0.4, 0.26, 0.2}, "alpha", 1.0));
            }
            SkillFx.sparks(level, mid, glow, 16, 8);
            SkillFx.vanilla(level, "minecraft:huge_explosion_emitter", mid);
            SkillFx.play(level, "random.explode", g, 0.6, 1);
            SkillFx.play(level, "firework.large_blast", g, 0.8, 1);
            SkillFx.play(level, "item.trident.thunder", g, 0.5, 0.8);
            RuneKit.shakeAround(level, g, 20, 0.45, 0.45);
            for (LivingEntity e : Kit.around(sp, g, radius, 4)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                    RuneKit.knock(e, V.sub(e.position(), g), 0.6, k.num("lift"));
                    RuneKit.burn(e, k.num("fire"));
                }
            }
        });
        return true;
    }
}
