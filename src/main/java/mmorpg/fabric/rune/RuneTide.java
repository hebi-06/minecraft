package mmorpg.fabric.rune;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.FxDisplays;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * 0.25.0 해류 룬 (tide — 마력 1.0 + 체력 0.5, 두 스탯 계열): 물 · 소용돌이 · 해일. 마력 계열이라 마법 피해, 마무리 숫자는 굵은 물빛 ! (SURGE).
 * 물의 창 (손 앞에 모인 물이 뾰족한 창이 되어 꿰뚫고 맞은 적을 밀침) · 치유의 비 (조준한 곳 위 비구름 · 빗줄기 4번 — 빗줄기마다 나와 구름 아래 아군 회복) ·
 * 소용돌이 감옥 (가운데로 끌어당기는 소용돌이 6번 → 물기둥) · 해일 (거대한 파도가 밀려가며 휩쓸고 끝에서 부서짐 — 휩쓸린 적은 한 번 더) ·
 * 심해의 군주 (소용돌이에서 바다뱀 (프리즈머린 블록 표시) 이 똬리를 틀며 솟아 물줄기를 뿜고 내리꽂혀 대해일 — 깊은 바다 하늘).
 */
final class RuneTide {
    private RuneTide() {}

    private static final double[][] C = RuneKit.colorOf("tide");
    /** 물빛 · 흰 거품 · 깊은 바다 · 비구름 */
    static final double[] WATER = C[0];
    static final double[] FOAM = C[1];
    static final double[] DEEP = {0.04, 0.2, 0.5};
    static final double[] CLOUD = {0.5, 0.62, 0.78};
    /** 미리 칠한 파도 그림 (rpg:tide_wave) 에 물빛을 조금 */
    static final double[] SEA = {0.78, 0.92, 1.0};

    static void register() {
        RuneCast.register("tideLance", RuneTide::tideLance);
        RuneCast.register("healingRain", RuneTide::healingRain);
        RuneCast.register("maelstrom", RuneTide::maelstrom);
        RuneCast.register("tidalWave", RuneTide::tidalWave);
        RuneCast.register("leviathan", RuneTide::leviathan);
    }

    // ------------------------------------------------------------ 공용: 물보라

    /** 솟구치는 물보라 왕관 (rpg:rfx_splash 칸 0 · 1 — 서 있는 그림, 밑동 = at). size = 반 높이 */
    static void crown(ServerLevel level, Vec3 at, double size, double life, double alpha) {
        RuneKit.rfx2(level, "splash", V.up(at, size * 0.84), Opt.of("size", size, "life", life, "grow", 0.3, "fade", 0.55, "alpha", alpha), WATER, FOAM,
                new int[] {0, 1}, null);
    }

    /** 부서지는 파도 마루 (칸 2 · 3 — 밑동 = at) */
    static void crest(ServerLevel level, Vec3 at, double size, double life, double alpha) {
        RuneKit.rfx2(level, "splash", V.up(at, size * 0.95), Opt.of("size", size, "life", life, "grow", 0.15, "fade", 0.5, "alpha", alpha), WATER, FOAM,
                new int[] {2, 3}, null);
    }

    /** 물보라 (바닐라 물보라 + 튀어 떨어지는 물방울 n 개) */
    static void spray(ServerLevel level, Vec3 at, int n, double spread) {
        SkillFx.particle(level, ParticleTypes.SPLASH, at, n * 3, spread, 0.15);
        for (int i = 0; i < n; i++) {
            double a = Kit.rand() * Math.PI * 2;
            SkillFx.fx(level, "rpg:drop", at, Opt.of("dir", V.unit(new Vec3(Math.cos(a), 1.2 + Kit.rand(), Math.sin(a))), "speed", 3.0 + Kit.rand() * 3, "size",
                    0.2 + Kit.rand() * 0.14, "life", 0.6, "color", i % 3 == 0 ? FOAM : WATER, "alpha", 0.95));
        }
    }

    /** 맞은 자리: 작은 물보라 왕관 · 물방울 · 물빛 터짐 */
    static void soak(ServerLevel level, LivingEntity e, double k) {
        Vec3 b = Kit.bodyPoint(e);
        crown(level, e.position(), 0.75 * k, 0.4, 0.95);
        spray(level, b, 4, 0.3);
        SkillFx.burst(level, b, WATER, 0.9 * k, 0.22);
    }

    // ------------------------------------------------------------ 일반: 물의 창

    /**
     * 물의 창: 손 앞에 물이 모여 (물 고리 · 물보라) 뾰족한 물의 창 (rpg:rfx_icicle 을 물빛으로 + 감싼 물줄기) 이 range 칸 (벽 앞까지) 을 speed 칸/초로 날아가며
     * 폭 width 칸의 적을 꿰뚫음 (위력 × mult) — 맞은 적은 물보라 왕관 · 밀쳐짐 (knock). 지나간 자리에 물보라 · 물결 고리
     */
    static boolean tideLance(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 view = sp.getViewVector(1F);
        Vec3 f = V.flat(view);
        Vec3 d = V.unit(new Vec3(f.x, Math.max(-0.35, Math.min(0.35, view.y)), f.z));
        Vec3 from = V.add(V.up(sp.position(), 1.2), f, 0.6);
        double len = Math.max(2, RuneKit.rayLength(level, from, d, k.num("range")));
        double speed = k.num("speed");
        double s0 = 1.2;
        double fly = Math.max(0.05, (len - s0) / speed);
        int ticks = Math.max(1, (int) Math.round(fly * 20));
        Vec3 hand = V.add(from, d, s0);
        // 손 앞에 물이 모임
        SkillFx.ring(level, V.add(sp.getEyePosition(), view, 1.6), view, WATER, 0.42, 0.22, 3.0);
        SkillFx.particle(level, ParticleTypes.SPLASH, hand, 16, 0.25, 0.1);
        // 물의 창 (뾰족한 촉) + 감싼 물줄기 둘
        RuneKit.rfx2(level, "icicle", hand, Opt.of("dir", d, "speed", speed, "size", 0.95, "life", fly, "fade", 0.15), WATER, FOAM);
        RuneKit.flyStreak(level, hand, V.add(from, d, len), speed, WATER, FOAM, 1.4, 0.42);
        RuneKit.later(1, () -> RuneKit.flyStreak(level, hand, V.add(from, d, len), speed, DEEP, WATER, 1.1, 0.6));
        for (int t = 1; t <= ticks; t++) {
            final int tt = t;
            final double s = Math.min(len, s0 + speed * t / 20.0);
            RuneKit.later(t, () -> {
                Vec3 p = V.add(from, d, s);
                SkillFx.particle(level, ParticleTypes.SPLASH, p, 6, 0.15, 0.08);
                if (tt % 2 == 0) SkillFx.fx(level, "rpg:drop", p, Opt.of("dir", V.unit(new Vec3(Kit.rand() - 0.5, 0.7, Kit.rand() - 0.5)), "speed", 2.2, "size", 0.24,
                        "life", 0.5, "color", WATER, "alpha", 0.9));
                if (tt % 3 == 0) SkillFx.ring(level, p, d, FOAM, 0.5, 0.25, 0.4);
            });
        }
        SkillFx.play(level, "item.trident.riptide_1", sp.position(), 1.3, 0.7);
        SkillFx.play(level, "item.trident.throw", sp.position(), 0.8, 0.9);
        SkillFx.play(level, "entity.dolphin.splash", sp.position(), 1.0, 0.8);
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, d, len, k.num("width"))) {
            LivingEntity e = h.e();
            int when = Math.max(0, (int) Math.round(Math.max(0, h.t() - s0) / speed * 20));
            RuneKit.later(when, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                    soak(level, e, 1.1);
                    RuneKit.knock(e, d, k.num("knock"), 0.2);
                    SkillFx.play(level, "entity.generic.splash", e.position(), 1.2, 0.8);
                });
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 희귀: 치유의 비

    /** 구름 덩이 (가운데에서 x · z, 크기, 그림 칸) */
    private record Puff(double x, double z, double s, int cell) {}

    /**
     * 치유의 비: 조준한 곳 (range 칸) 위에 비구름 (rpg:rfx_cloud) 이 모이고 (delay 틱) 비가 내림 — gap 틱마다 hits 번 굵은 빗줄기가 쏟아져
     * 반지름 radius 칸의 적 (위력 × mult) · 땅에 물결 고리, 빗줄기마다 나와 구름 아래 아군의 체력을 최대 체력 × heal × 스탯 비례만큼 회복 (초록 숫자)
     */
    static boolean healingRain(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 g = RuneKit.groundAim(sp, k.num("range"));
        double radius = k.num("radius");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int delay = (int) k.num("delay");
        int total = delay + (hits - 1) * gap + 10;
        Vec3 sky = V.up(g, 4.1); // 1인칭에서도 구름이 보이게 (눈에서 30° 안쪽)
        SkillFx.rune(level, g, Opt.of("size", radius, "life", total / 20.0, "color", WATER, "alpha", 0.7, "rate", 25.0, "progress", 1.0));
        SkillFx.play(level, "weather.rain", g, 1.0, 0.9);
        SkillFx.play(level, "mob.evocation_illager.cast_spell", sp.position(), 1.3, 0.6);
        // 비구름: 덩이 일곱 (10틱마다 다시 그림 — 처음에는 작게 모여듦)
        List<Puff> puffs = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            double a = (i / 7.0) * Math.PI * 2 + Kit.rand();
            double rr = i == 0 ? 0 : radius * (0.45 + 0.25 * (i % 2));
            puffs.add(new Puff(Math.cos(a) * rr, Math.sin(a) * rr, i == 0 ? 2.8 : 2.0, i % 4));
        }
        for (int t = 0; t < total; t += 10) {
            final int tt = t;
            RuneKit.later(t, () -> {
                double q = Math.min(1, (tt + 6) / (double) Math.max(1, delay));
                for (Puff p : puffs) {
                    RuneKit.rfx(level, "cloud", new Vec3(sky.x + p.x() * q, sky.y + (p.s() > 2.4 ? 0.5 : 0), sky.z + p.z() * q), Opt.of("size", p.s() * (0.6 + 0.4 * q),
                            "life", 0.6, "cell", (double) p.cell(), "color", CLOUD, "alpha", 0.92, "fade", tt + 10 >= total ? 0.5 : 0.0, "grow", tt == 0 ? 0.4 : 0.0));
                }
            });
        }
        // 비: 2틱마다 빗줄기 (가는 물빛 줄) · 땅의 빗방울
        Vec3 fall = V.unit(new Vec3(0.12, -1, 0.08));
        for (int t = Math.max(0, delay - 6); t < total; t += 2) {
            RuneKit.later(t, () -> {
                for (int j = 0; j < 5; j++) {
                    double a = Kit.rand() * Math.PI * 2;
                    double rr = Math.sqrt(Kit.rand()) * radius;
                    Vec3 top = new Vec3(g.x + Math.cos(a) * rr, sky.y - 0.6, g.z + Math.sin(a) * rr);
                    RuneKit.rfx2(level, "icicle", top, Opt.of("dir", fall, "speed", 24.0, "size", 0.32, "life", 0.2), j % 2 == 0 ? WATER : FOAM, RuneKit.WHITE);
                }
                SkillFx.particle(level, ParticleTypes.RAIN, V.up(g, 0.1), 10, radius * 0.5, 0);
                SkillFx.particle(level, ParticleTypes.FALLING_WATER, V.up(sky, -0.8), 6, radius * 0.45, 0);
            });
        }
        double scale = Runes.supportScale(RpgStats.effective(sp), r.family());
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            RuneKit.later(delay + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                // 굵은 빗줄기: 구름에서 물줄기 여럿 · 땅에 물결 고리 · 물보라
                for (int j = 0; j < 9; j++) {
                    double a = j / 9.0 * Math.PI * 2 + ii;
                    double rr = (j == 0 ? 0 : 0.35 + 0.6 * Kit.rand()) * radius;
                    Vec3 top = new Vec3(g.x + Math.cos(a) * rr, sky.y - 0.4, g.z + Math.sin(a) * rr);
                    RuneKit.flyStreak(level, top, new Vec3(top.x, g.y + 0.1, top.z), 40, WATER, FOAM, 0.9, 0.3);
                }
                RuneKit.zonePulse(level, g, ii % 2 == 0 ? WATER : FOAM, radius, 0.5);
                SkillFx.fx(level, "rpg:whirlpool", V.up(g, 0.08), Opt.of("dir", V.ZERO, "size", radius * 0.8, "life", 0.5, "spin", ii * 70.0, "speed", 220.0, "color",
                        FOAM, "alpha", 0.55));
                spray(level, V.up(g, 0.2), 6, radius * 0.4);
                SkillFx.play(level, "entity.generic.splash", g, 0.9 + ii * 0.08, 0.7);
                for (LivingEntity e : Kit.around(sp, g, radius, 4)) {
                    PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                        if (RuneKit.fxQuota("rain", 6)) soak(level, e, 0.9);
                    });
                }
                // 회복: 나 (어디에 있든) + 구름 아래 아군
                List<ServerPlayer> who = new ArrayList<>(RuneKit.playersAround(level, g, radius + 0.5, sp));
                if (!who.contains(sp) && !sp.isRemoved()) who.add(sp);
                for (ServerPlayer pl : who) {
                    double healed = RpgHealth.healBy(pl, RpgHealth.maxOf(pl) * k.num("heal") * scale);
                    if (healed <= 0) continue;
                    DamageNumbers.show(pl, healed, DamageNumbers.Style.HEAL, "", 0);
                    Vec3 c = pl.position();
                    for (int j = 0; j < 5; j++) {
                        Vec3 d = RuneKit.turn(Kit.facingOf(pl), j * 72 + Kit.rand() * 30);
                        SkillFx.fx(level, "rpg:arrow_spark", V.up(V.add(c, d, 0.8), 0.3 + Kit.rand() * 0.6), Opt.of("dir", V.UP, "speed", 2.0, "size", 0.14, "grow",
                                1.2, "life", 0.6, "color", j % 2 == 0 ? new double[] {0.5, 1.0, 0.75} : FOAM, "alpha", 1.0));
                    }
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 고귀: 소용돌이 감옥

    /**
     * 소용돌이 감옥: 조준한 곳 (range 칸) 에 반지름 radius 칸의 소용돌이 (도는 물결 · 둘레를 도는 물벽 · 가운데 물기둥 회오리) — gap 틱마다 hits 번 안의 적을
     * 쳐 (위력 × mult) 가운데로 끌어당기고 (pull — 보스 제외) 느리게 함 → 끝에 가운데에서 물기둥이 솟아 터짐 (위력 × crush, 띄움)
     */
    static boolean maelstrom(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 g = RuneKit.groundAim(sp, k.num("range"));
        double radius = k.num("radius");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int crushAt = hits * gap;
        SkillFx.rune(level, g, Opt.of("size", radius, "life", (crushAt + 6) / 20.0, "color", DEEP, "alpha", 0.75, "rate", -90.0, "progress", 1.0));
        SkillFx.play(level, "block.bubble_column.whirlpool_inside", g, 0.8, 1.0);
        SkillFx.play(level, "item.trident.riptide_3", g, 0.8, 0.7);
        double a0 = Kit.rand() * Math.PI * 2;
        for (int t = 0; t < crushAt; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double q = Math.min(1, (tt + 2) / 10.0);
                if (tt % 6 == 0) {
                    SkillFx.fx(level, "rpg:whirlpool", V.up(g, 0.08), Opt.of("dir", V.ZERO, "size", radius * q, "life", 0.4, "spin", tt * 25.0, "speed", -620.0, "color",
                            WATER, "alpha", 0.75));
                    SkillFx.fx(level, "rpg:whirlpool", V.up(g, 0.12), Opt.of("dir", V.ZERO, "size", radius * 0.55 * q, "life", 0.4, "spin", tt * 40.0, "speed", -900.0,
                            "color", FOAM, "alpha", 0.6));
                }
                // 둘레를 도는 물벽 (파도 마루 여섯)
                for (int j = 0; j < 6; j++) {
                    double a = a0 + j * Math.PI / 3 + tt * 0.22;
                    Vec3 p = PairKit.ring(g, radius * 0.9 * q, a);
                    if (PairKit.nearCam(sp, V.up(p, 1), 2.6)) continue;
                    crest(level, p, 0.85, 0.14, 0.85);
                }
                // 가운데 물기둥 회오리 · 빙빙 도는 물보라
                RuneKit.vortexAt(level, V.up(g, 1.9), 2.1 * q, WATER, Opt.of("life", 0.14, "alpha", 0.85, "fps", 24.0, "fade", 0.0, "phase", (double) (tt % 8)));
                for (int j = 0; j < 4; j++) {
                    double a = a0 - tt * 0.3 + j * Math.PI / 2;
                    Vec3 p = V.up(PairKit.ring(g, radius * (0.3 + 0.15 * j), a), 0.3 + j * 0.2);
                    Vec3 tan = new Vec3(-Math.sin(a), 0.05, Math.cos(a));
                    level.sendParticles(ParticleTypes.SPLASH, p.x, p.y, p.z, 0, tan.x, tan.y, tan.z, 0.6);
                    level.sendParticles(ParticleTypes.BUBBLE_POP, p.x, p.y, p.z, 1, 0.2, 0.1, 0.2, 0.02);
                }
            });
        }
        SkillFx.every(hits, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.zonePulse(level, g, i % 2 == 0 ? WATER : FOAM, radius, 0.4);
            SkillFx.play(level, "block.bubble_column.bubble_pop", g, 0.7 + i * 0.05, 0.8);
            for (LivingEntity e : Kit.around(sp, g, radius, 3)) {
                PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                    if (RuneKit.fxQuota("maelstrom", 6)) {
                        Vec3 b = Kit.bodyPoint(e);
                        spray(level, b, 3, 0.25);
                        SkillFx.fx(level, "rpg:whirlpool", V.up(e.position(), 0.1), Opt.of("dir", V.ZERO, "size", 0.9 + Kit.bodyPad(e), "life", 0.3, "spin", Kit.rand()
                                * 360, "speed", -720.0, "color", FOAM, "alpha", 0.7));
                    }
                });
                RuneKit.effect(e, "slowness", (int) k.num("slowTicks"), (int) k.num("slowAmp"));
                if (PairKit.boss(e)) continue;
                // 가운데로 (+ 조금 돌며) 끌어당김
                Vec3 to = V.sub(g, e.position());
                double dist = Math.hypot(to.x, to.z);
                if (dist < 0.6) continue;
                Vec3 in = V.flat(to);
                Vec3 tan = new Vec3(-in.z, 0, in.x);
                double pull = k.num("pull") * Math.min(1, dist / 2.5);
                Kit.knockback(e, in.x * pull + tan.x * pull * 0.5, in.z * pull + tan.z * pull * 0.5, 0.05);
            }
        });
        // 물기둥
        RuneKit.later(crushAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            double sc = RuneKit.nearK(sp, V.up(g, 2), 6, 0.45);
            RuneKit.rfx(level, "fountain", V.up(g, 2.6 * sc), Opt.of("size", 2.8 * sc, "life", 0.7, "fade", 0.5, "color", WATER, "alpha", 0.95, "frames", 4.0, "fps",
                    8.0));
            RuneKit.rfx(level, "fountain", V.up(g, 2.2 * sc), Opt.of("size", 2.2 * sc, "life", 0.6, "fade", 0.5, "color", FOAM, "alpha", 0.9, "frames", 4.0, "fps",
                    10.0));
            crown(level, g, 2.4 * sc, 0.7, 1.0);
            for (int j = 0; j < 8; j++) {
                double a = a0 + j * Math.PI / 4;
                Vec3 p = PairKit.ring(g, radius * 0.55, a);
                if (!PairKit.nearCam(sp, V.up(p, 1), 3.4)) RuneKit.later(j % 2, () -> crest(level, p, 0.9, 0.4, 0.85));
            }
            spray(level, V.up(g, 1.5), 16, 1.2);
            SkillFx.particle(level, ParticleTypes.SPLASH, V.up(g, 2.5), 60, 1.2, 0.4);
            RuneKit.zonePulse(level, g, FOAM, radius, 0.5);
            RuneKit.novaRing(level, g, 1.2, 2.6, WATER, FOAM, Opt.of("life", 0.5));
            RuneKit.shakeAround(level, g, 10, 0.25, 0.3);
            SkillFx.play(level, "entity.generic.splash", g, 0.6, 1.0);
            SkillFx.play(level, "block.bubble_column.upwards_inside", g, 0.9, 1.0);
            SkillFx.play(level, "item.trident.riptide_3", g, 1.1, 0.8);
            for (LivingEntity e : Kit.around(sp, g, radius, 4)) {
                PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.SURGE, life, dmg -> {
                    if (!PairKit.boss(e)) Kit.knockback(e, 0, 0, 0.75);
                    if (RuneKit.fxQuota("maelstromCrush", 6)) soak(level, e, 1.2);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 영웅: 해일

    /**
     * 해일: 앞으로 거대한 파도 (rpg:tide_wave 여러 겹 · 물보라 왕관 · 물보라) 가 폭 span 칸으로 speed 칸/초로 length 칸 밀려감 — 파도가 닿은 적 (위력 × mult) 은
     * 파도에 휩쓸려 함께 밀려가고 (보스 제외), 끝에서 파도가 부서지며 휩쓸린 적 · 부서진 자리 둘레 radius 칸의 적을 한 번 더 (위력 × crush)
     */
    static boolean tidalWave(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 side = V.rightOf(f);
        double length = k.num("length");
        double span = k.num("span");
        double band = k.num("width");
        double speed = k.num("speed");
        double s0 = 1.0;
        int dur = Math.max(2, (int) Math.round((length - s0) / speed * 20));
        double lifeS = dur / 20.0;
        Vec3 start = V.add(c0, f, s0);
        // 파도 (세 겹 — 앞의 큰 물마루 · 뒤의 낮은 물결 · 앞의 흰 거품): 옆으로 2.5칸마다
        for (double l = -span / 2; l <= span / 2 + 0.01; l += 2.5) {
            Vec3 at = V.add(start, side, l);
            SkillFx.fx(level, "rpg:tide_wave", V.up(at, 1.7), Opt.of("dir", f, "speed", speed, "size", 2.0, "thick", 1.7, "life", lifeS, "color", SEA, "alpha", 0.92));
            SkillFx.fx(level, "rpg:tide_wave", V.add(V.up(at, 0.9), f, -0.8), Opt.of("dir", f, "speed", speed, "size", 2.3, "thick", 1.2, "life", lifeS, "color", WATER,
                    "alpha", 0.9));
            SkillFx.fx(level, "rpg:tide_wave", V.add(V.up(at, 2.8), f, 0.3), Opt.of("dir", f, "speed", speed, "size", 1.6, "thick", 0.6, "life", lifeS, "color",
                    FOAM, "alpha", 0.85));
        }
        SkillFx.play(level, "ambient.weather.thunder", c0, 0.6, 0.4);
        SkillFx.play(level, "entity.generic.splash", c0, 0.5, 1.0);
        SkillFx.play(level, "item.trident.riptide_3", c0, 0.7, 0.9);
        RuneKit.selfCue(sp, WATER, FOAM, true);
        Map<UUID, LivingEntity> carried = new LinkedHashMap<>();
        for (int t = 0; t <= dur; t++) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double s = s0 + speed * tt / 20.0;
                Vec3 front = V.add(c0, f, s);
                // 파도 앞 물보라 · 물보라 왕관 (2틱마다)
                if (tt % 2 == 0) {
                    for (int j = 0; j < 3; j++) {
                        Vec3 p = V.add(front, side, (Kit.rand() - 0.5) * span);
                        p = PairKit.onGround(level, p);
                        if (!PairKit.nearCam(sp, V.up(p, 1.2), 2.2)) crown(level, p, 1.1 + Kit.rand() * 0.5, 0.3, 0.9);
                    }
                    SkillFx.particle(level, ParticleTypes.SPLASH, V.up(front, 1.2), 24, span * 0.3, 0.3);
                    SkillFx.particle(level, ParticleTypes.FALLING_WATER, V.up(front, 3.0), 8, span * 0.3, 0);
                }
                if (tt % 6 == 0) SkillFx.play(level, "entity.generic.splash", front, 0.6 + Kit.rand() * 0.2, 0.7);
                // 파도가 닿은 적: 한 번 (위력 × mult) → 휩쓸림
                for (LivingEntity e : Kit.around(sp, V.add(front, f, -band / 4), Math.max(band, span) / 2 + 1.5, 3)) {
                    if (carried.containsKey(e.getUUID())) continue;
                    Vec3 rel = V.sub(e.position(), c0);
                    double along = V.dot(rel, f);
                    double lat = Math.abs(V.dot(rel, side));
                    double pad = Kit.bodyPad(e);
                    if (along < 0.4 || along > s + pad + 0.5 || along < s - band - pad || lat > span / 2 + pad + 0.5) continue;
                    carried.put(e.getUUID(), e);
                    PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                        soak(level, e, 1.2);
                        SkillFx.play(level, "entity.hostile.splash", e.position(), 1.0, 0.8);
                    });
                }
                // 휩쓸린 적은 파도를 따라 밀려감 (2틱마다)
                if (tt % 2 == 0 && tt < dur) {
                    for (LivingEntity e : carried.values()) {
                        if (e.isRemoved() || PairKit.boss(e)) continue;
                        double lag = s - V.dot(V.sub(e.position(), c0), f);
                        double kk = k.num("knock") * Math.max(0.4, Math.min(1.4, 0.6 + lag * 0.25));
                        Kit.knockback(e, f.x * kk, f.z * kk, 0.12);
                        if (RuneKit.fxQuota("carried", 4)) SkillFx.particle(level, ParticleTypes.SPLASH, Kit.bodyPoint(e), 6, 0.3, 0.1);
                    }
                }
            });
        }
        // 파도가 부서짐
        RuneKit.later(dur + 1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 end = V.add(c0, f, length);
            for (double l = -span / 2; l <= span / 2 + 0.01; l += 2.0) {
                Vec3 p = PairKit.onGround(level, V.add(end, side, l));
                crest(level, p, 1.6, 0.45, 0.95);
                RuneKit.later(2, () -> crown(level, p, 1.5, 0.5, 0.95));
                spray(level, V.up(p, 0.8), 3, 0.6);
            }
            SkillFx.particle(level, ParticleTypes.SPLASH, V.up(end, 1.0), 80, span * 0.35, 0.5);
            RuneKit.zonePulse(level, end, FOAM, k.num("radius"), 0.5);
            RuneKit.shakeAround(level, end, 14, 0.3, 0.35);
            SkillFx.play(level, "entity.generic.splash", end, 0.5, 1.0);
            SkillFx.play(level, "item.trident.thunder", end, 1.6, 0.4);
            Map<UUID, LivingEntity> got = new LinkedHashMap<>(carried);
            for (double l = -span / 2; l <= span / 2 + 0.01; l += k.num("radius")) {
                for (LivingEntity e : Kit.around(sp, V.add(end, side, l), k.num("radius"), 3)) got.putIfAbsent(e.getUUID(), e);
            }
            for (LivingEntity e : got.values()) {
                if (e.isRemoved()) continue;
                PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.SURGE, life, dmg -> {
                    if (!PairKit.boss(e)) RuneKit.knock(e, f, k.num("knock"), 0.35);
                    if (RuneKit.fxQuota("waveBreak", 6)) soak(level, e, 1.3);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 초월: 심해의 군주

    private static final BlockState SCALE = Blocks.PRISMARINE.defaultBlockState();
    private static final BlockState SCALE2 = Blocks.PRISMARINE_BRICKS.defaultBlockState();
    private static final BlockState DARK = Blocks.DARK_PRISMARINE.defaultBlockState();
    private static final BlockState LANTERN = Blocks.SEA_LANTERN.defaultBlockState();
    /** 바다뱀이 솟아 똬리를 트는 틱 (delay 와 같게 맞춤) · 내리꽂히는 틱 · 몸의 마디 간격 (칸) · 똬리 반지름 · 높이 · 바퀴 */
    private static final int DIVE = 6;
    private static final double SEG = 0.95;
    private static final double COIL_R = 2.4;
    private static final double COIL_H = 4.8;
    private static final double TURNS = 1.35;

    /** 똬리 위 자리 (u = 0 땅속 ~ 1 머리) */
    private static Vec3 coil(Vec3 c, double a0, double u) {
        double a = a0 + u * TURNS * Math.PI * 2;
        double rr = COIL_R * (1.15 - 0.3 * u);
        return new Vec3(c.x + Math.cos(a) * rr, c.y - 1.2 + u * (COIL_H + 1.2), c.z + Math.sin(a) * rr);
    }

    /** 몸 마디 하나 (표시 · 다 자란 변환 — 가라앉힐 때 씀) */
    private record Seg(Display.BlockDisplay d, Transformation t) {}

    /** 바다뱀 머리 (모형 앞 = +z): 머리뼈 · 주둥이 · 아래턱 (따로 벌어짐) · 빛나는 눈 둘 · 지느러미 뿔 둘 — 처음에는 땅속에서 위를 봄 */
    private static PairKit.Rig head(ServerLevel level, Vec3 base, int life) {
        PairKit.Rig h = new PairKit.Rig(level, base, life);
        h.offset = new Vec3(0, -1.6, 0);
        h.rot = PairKit.look(V.UP);
        int jaw = h.group(new Vec3(0, -0.35, -0.5));
        h.add(DARK, new Vec3(0, 0.1, 0), 1.7, 1.1, 1.9, PairKit.LIT);
        h.add(SCALE2, new Vec3(0, 0, 1.3), 1.25, 0.65, 1.3, PairKit.LIT);
        h.add(jaw, SCALE, new Vec3(0, -0.55, 0.9), new Vec3(1.1, 0.35, 1.6), null, PairKit.LIT);
        h.add(LANTERN, new Vec3(0.62, 0.35, 0.55), 0.38, 0.32, 0.38, PairKit.GLOW);
        h.add(LANTERN, new Vec3(-0.62, 0.35, 0.55), 0.38, 0.32, 0.38, PairKit.GLOW);
        h.add(0, DARK, new Vec3(0.55, 0.85, -0.6), new Vec3(0.18, 0.9, 1.2), PairKit.about(new Vec3(1, 0, 0), -35), PairKit.LIT);
        h.add(0, DARK, new Vec3(-0.55, 0.85, -0.6), new Vec3(0.18, 0.9, 1.2), PairKit.about(new Vec3(1, 0, 0), -35), PairKit.LIT);
        return h;
    }

    /**
     * 심해의 군주: 조준한 곳 (range 칸) 에 깊은 바다 하늘 (3돌부터) · 물빛 영역 문양 · 반지름 radius 칸의 큰 소용돌이 → 소용돌이에서 바다뱀 (프리즈머린 블록 —
     * 머리 · 빛나는 눈 · 벌어지는 턱, 마디가 이어진 몸) 이 똬리를 틀며 솟음 (delay 틱) → gap 틱마다 hits 번 휘감기: 바다뱀이 입을 벌려 물줄기를 뿜고 물결이 휘돌아
     * 안의 적 (위력 × mult) → 바다뱀이 가운데로 내리꽂혀 대해일 (위력 × crush — 거대한 물보라 · 사방으로 퍼지는 파도 고리, 밀쳐냄). 대상이 없어도 씀
     */
    static boolean leviathan(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 aim = RuneKit.groundAim(sp, k.num("range"));
        double radius = k.num("radius");
        int delay = (int) k.num("delay");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int lastHit = delay + (hits - 1) * gap;
        int diveAt = lastHit + 10;
        int crushAt = diveAt + DIVE;
        int end = crushAt + 12;
        Vec3 f = V.flat(V.sub(aim, sp.position()));
        // 똬리는 조준한 곳 뒤 (시전자 화면을 덮지 않고 1인칭에서도 머리가 보이게)
        Vec3 c = PairKit.onGround(level, V.add(aim, f, 2.5));
        double a0 = Math.atan2(sp.getZ() - c.z, sp.getX() - c.x) + Math.PI * 0.6;
        RuneKit.ultSkyFor(sp, level, c, "leviathan", end + 30, null);
        RuneKit.ultFlashFor(sp, level, c, "leviathan", "flash", Opt.empty());
        RuneKit.domainSigil(level, aim, radius, "spirit", WATER, FOAM, Opt.of("life", end / 20.0, "spinRate", -24.0, "grow", 0.15, "alpha", 0.8, "fade", 0.15));
        RuneKit.selfCue(sp, WATER, FOAM, true);
        SkillFx.play(level, "mob.elderguardian.curse", c, 0.7, 0.9);
        SkillFx.play(level, "ambient.underwater.enter", c, 0.6, 1.0);
        SkillFx.play(level, "block.bubble_column.whirlpool_inside", c, 0.6, 1.0);
        SkillFx.shake(sp, 0.12, 0.4);
        // 큰 소용돌이 (끝까지) · 솟는 물보라
        for (int t = 0; t < crushAt; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                if (tt % 6 == 0) {
                    SkillFx.fx(level, "rpg:whirlpool", V.up(aim, 0.08), Opt.of("dir", V.ZERO, "size", radius, "life", 0.4, "spin", tt * 20.0, "speed", -480.0, "color",
                            DEEP, "alpha", 0.8));
                    SkillFx.fx(level, "rpg:whirlpool", V.up(aim, 0.12), Opt.of("dir", V.ZERO, "size", radius * 0.6, "life", 0.4, "spin", tt * 35.0, "speed", -760.0,
                            "color", WATER, "alpha", 0.7));
                }
                for (int j = 0; j < 3; j++) {
                    double a = a0 - tt * 0.2 + j * Math.PI * 2 / 3;
                    Vec3 p = PairKit.ring(aim, radius * (0.5 + 0.4 * Kit.rand()), a);
                    Vec3 tan = new Vec3(-Math.sin(a), 0.4, Math.cos(a));
                    level.sendParticles(ParticleTypes.SPLASH, p.x, p.y + 0.3, p.z, 0, tan.x, tan.y, tan.z, 0.7);
                }
                if (tt % 4 == 0) SkillFx.particle(level, ParticleTypes.BUBBLE_POP, V.up(c, 0.4), 8, 1.5, 0.05);
            });
        }
        // 바다뱀: 머리가 똬리를 따라 솟고 지나간 자리에 몸 마디가 남음
        int bodyLife = crushAt + 4;
        PairKit.Rig head = head(level, c, bodyLife + 2);
        List<Seg> body = new ArrayList<>();
        double pathLen = TURNS * Math.PI * 2 * COIL_R + COIL_H;
        int segs = (int) Math.round(pathLen / SEG);
        double[] placed = {0};
        for (int t = 2; t <= delay; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double u = Math.min(1, tt / (double) delay);
                Vec3 p = coil(c, a0, u);
                Vec3 tan = V.unit(V.sub(coil(c, a0, Math.min(1, u + 0.02)), coil(c, a0, Math.max(0, u - 0.02))));
                head.pose(V.sub(p, c), PairKit.look(tan), 1.0, 2, new Quaternionf());
                // 지나간 자리의 몸 마디 (머리 뒤 1.2칸부터)
                double uMax = u - 1.2 / pathLen;
                while (placed[0] < segs && placed[0] / segs <= uMax) {
                    double uu = placed[0] / segs;
                    placed[0]++;
                    Vec3 q = coil(c, a0, uu);
                    if (PairKit.nearCam(sp, q, 3.0)) continue;
                    Vec3 tq = V.unit(V.sub(coil(c, a0, Math.min(1, uu + 0.02)), coil(c, a0, Math.max(0, uu - 0.02))));
                    double s = 0.75 + 0.55 * uu;
                    Quaternionf rot = PairKit.look(tq);
                    BlockState st = ((int) placed[0]) % 3 == 0 ? SCALE2 : SCALE;
                    Transformation full = PairKit.box(Vec3.ZERO, rot, s, s, s * 1.25);
                    Display.BlockDisplay d = FxDisplays.block(level, q, st, PairKit.box(Vec3.ZERO, rot, s * 0.3, s * 0.3, s * 0.3), bodyLife - tt, PairKit.LIT);
                    if (d != null) {
                        body.add(new Seg(d, full));
                        RuneKit.later(2, () -> FxDisplays.move(d, full, 3));
                    }
                    if (((int) placed[0]) % 3 == 1) {
                        // 등지느러미 (마디의 위쪽 — 마디 축으로 돌린 위)
                        Vector3f upv = new Vector3f(0, (float) (s * 0.62), 0).rotate(new Quaternionf(rot));
                        Transformation finT = PairKit.box(new Vec3(upv.x, upv.y, upv.z), rot, 0.14, s * 0.75, s * 0.85);
                        Display.BlockDisplay fin = FxDisplays.block(level, q, DARK, finT, bodyLife - tt, PairKit.LIT);
                        if (fin != null) body.add(new Seg(fin, finT));
                    }
                    if (RuneKit.fxQuota("leviathanSplash", 2)) spray(level, q, 2, 0.4);
                }
                if (tt % 4 == 0) SkillFx.play(level, "entity.dolphin.splash", p, 0.5, 0.7);
            });
        }
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            SkillFx.play(level, "entity.ravager.roar", c, 0.5, 1.0);
            SkillFx.play(level, "mob.elderguardian.curse", c, 0.5, 0.6);
            RuneKit.shakeAround(level, c, 16, 0.25, 0.5);
        });
        // 휘감기: 입을 벌려 물줄기 · 물결이 휘돎
        Vec3 top = coil(c, a0, 1.0);
        Vec3 topTan = V.unit(V.sub(coil(c, a0, 1.0), coil(c, a0, 0.97)));
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            RuneKit.later(delay + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                List<LivingEntity> ts = Kit.around(sp, aim, radius, 5);
                // 머리가 대상 쪽을 보며 턱을 벌림 → 3틱 뒤 닫음
                Vec3 look = ts.isEmpty() ? V.flat(V.sub(aim, top)) : V.unit(V.sub(Kit.bodyPoint(ts.get(ii % ts.size())), top));
                Vec3 lookMix = V.unit(V.add(V.scale(topTan, 0.25), look));
                head.pose(V.sub(top, c), PairKit.look(lookMix), 1.0, 2, new Quaternionf());
                head.swing(1, PairKit.about(new Vec3(1, 0, 0), 38), 2);
                RuneKit.later(4, () -> head.swing(1, new Quaternionf(), 3));
                Vec3 mouth = V.add(top, lookMix, 1.6);
                for (int j = 0; j < Math.min(5, ts.size()); j++) {
                    Vec3 b = Kit.bodyPoint(ts.get(j));
                    RuneKit.flyStreak(level, mouth, b, 36, WATER, FOAM, 1.0, 0.5);
                }
                if (ts.isEmpty()) RuneKit.flyStreak(level, mouth, V.up(aim, 0.5), 36, WATER, FOAM, 1.0, 0.5);
                // 휘도는 물결 (파도 고리 — 바깥으로 돌며)
                for (int j = 0; j < 6; j++) {
                    double a = a0 + ii * 0.7 + j * Math.PI / 3;
                    Vec3 dir = new Vec3(-Math.sin(a), 0, Math.cos(a));
                    Vec3 p = PairKit.ring(aim, radius * 0.55, a);
                    if (PairKit.nearCam(sp, V.up(p, 1), 2.5)) continue;
                    SkillFx.fx(level, "rpg:tide_wave", V.up(p, 1.0), Opt.of("dir", dir, "speed", 9.0, "size", 1.6, "thick", 1.0, "life", 0.35, "color", SEA, "alpha",
                            0.9));
                }
                RuneKit.zonePulse(level, aim, ii % 2 == 0 ? WATER : FOAM, radius, 0.45);
                SkillFx.play(level, "entity.guardian.attack", top, 0.6 + ii * 0.05, 0.9);
                SkillFx.play(level, "item.trident.riptide_2", aim, 0.8, 0.7);
                for (LivingEntity e : ts) {
                    PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                        if (RuneKit.fxQuota("leviathanHit", 6)) soak(level, e, 1.1);
                        // 0.49.0 휘감김: 조이는 동안 root 틱 묶음 (보스는 느려짐 — PairKit.root)
                        if (k.numOr("root", 0) > 0) PairKit.root(e, (int) k.num("root"));
                    });
                }
            });
        }
        // 내리꽂힘: 머리가 가운데로, 몸은 바닷속으로 가라앉음
        RuneKit.later(diveAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 down = V.unit(V.sub(V.up(aim, -1.0), top));
            head.pose(V.sub(V.up(aim, 0.6), c), PairKit.look(down), 1.15, DIVE, new Quaternionf());
            head.swing(1, PairKit.about(new Vec3(1, 0, 0), 30), 2);
            for (int i = 0; i < body.size(); i++) {
                Seg sg = body.get(i);
                // 머리 쪽 (나중에 놓인 마디) 부터 먼저 가라앉음
                final int delayI = Math.min(DIVE - 1, (body.size() - 1 - i) * DIVE / Math.max(1, body.size()));
                RuneKit.later(delayI, () -> {
                    if (sg.d().isRemoved()) return;
                    Vector3fc tr = sg.t().translation();
                    Vector3fc sc = sg.t().scale();
                    FxDisplays.move(sg.d(), new Transformation(new Vector3f(tr.x(), tr.y() - 5.5F, tr.z()), sg.t().leftRotation(),
                            new Vector3f(sc.x() * 0.7F, sc.y() * 0.7F, sc.z() * 0.7F), new Quaternionf()), DIVE + 2);
                });
            }
            SkillFx.play(level, "mob.enderdragon.flap", top, 0.5, 1.0);
            SkillFx.play(level, "entity.warden.roar", c, 0.8, 0.6);
        });
        // 대해일
        RuneKit.later(crushAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            head.remove();
            RuneKit.ultFlashFor(sp, level, aim, "leviathan", "flash", Opt.empty());
            double sc = RuneKit.nearK(sp, V.up(aim, 2), 8, 0.4);
            crown(level, aim, 4.2 * sc, 0.9, 1.0);
            RuneKit.rfx(level, "fountain", V.up(aim, 4.2 * sc), Opt.of("size", 4.4 * sc, "life", 0.9, "fade", 0.5, "color", WATER, "alpha", 0.95, "frames", 4.0,
                    "fps", 7.0));
            // 사방으로 퍼지는 파도 고리
            for (int j = 0; j < 12; j++) {
                double a = j / 12.0 * Math.PI * 2;
                Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 p = V.add(V.up(aim, 1.2), dir, 1.5);
                SkillFx.fx(level, "rpg:tide_wave", p, Opt.of("dir", dir, "speed", radius * 1.6, "size", 1.6, "thick", 1.5, "life", 0.55, "color", SEA, "alpha", 0.95));
            }
            for (int j = 0; j < 10; j++) {
                double a = j / 10.0 * Math.PI * 2 + 0.3;
                Vec3 p = PairKit.onGround(level, PairKit.ring(aim, radius * 0.7, a));
                if (!PairKit.nearCam(sp, V.up(p, 1.2), 2.6)) RuneKit.later(2 + j % 3, () -> crest(level, p, 1.5, 0.5, 0.95));
            }
            spray(level, V.up(aim, 2), 24, 2.0);
            SkillFx.particle(level, ParticleTypes.SPLASH, V.up(aim, 3), 140, radius * 0.4, 0.6);
            RuneKit.novaRing(level, aim, 1.8, radius * 0.9, WATER, FOAM, Opt.of("life", 0.6));
            RuneKit.zonePulse(level, aim, FOAM, radius, 0.6);
            RuneKit.glowCrack(level, aim, radius * 0.9, WATER, FOAM, Opt.of("life", 1.2));
            RuneKit.shakeAround(level, aim, 18, 0.5, 0.5);
            SkillFx.play(level, "entity.generic.splash", aim, 0.4, 1.0);
            SkillFx.play(level, "item.trident.thunder", aim, 0.7, 0.8);
            SkillFx.play(level, "random.explode", aim, 0.6, 0.7);
            for (LivingEntity e : Kit.around(sp, aim, radius, 5)) {
                PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.SURGE, life, dmg -> {
                    if (!PairKit.boss(e)) RuneKit.knock(e, V.sub(e.position(), aim), 0.5, 0.4);
                    if (RuneKit.fxQuota("leviathanCrush", 8)) soak(level, e, 1.4);
                });
            }
        });
        return true;
    }
}
