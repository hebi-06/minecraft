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

/**
 * 0.25.0 화산 룬 (volcano — 힘 1.0 + 마력 0.5, 두 스탯 계열): 용암 · 화산탄 · 분화. 용암 기둥 · 마그마 대지 · 화산은 블록 표시 (마그마 · 현무암 — 3D),
 * 맞은 적은 화상 (dot × dots, dotGap 틱마다 — 주황 작은 숫자 BURN, 룬의 마지막 타격 뒤부터 · 마그마 대지는 대지 위에 있는 동안). 마무리 숫자는 굵은 붉은빛 ! (ERUPT).
 * 용암 강타 (땅을 내리쳐 앞 부채꼴에 용암이 튐) · 화산탄 (불타는 화산탄 셋을 던져 터뜨림) · 용암 분출 (조준한 곳에서 용암 기둥이 네 번 솟구침) ·
 * 마그마 대지 (땅이 녹아 용암 대지 → 대분출) · 대분화 (땅에서 화산이 솟아 화산탄을 퍼붓고 크게 터짐 — 잿빛 하늘, 3돌부터 분화 뒤 화상).
 */
final class RuneVolcano {
    private RuneVolcano() {}

    private static final double[][] C = RuneKit.colorOf("volcano");
    /** 용암빛 · 노란 심지 · 짙은 숯 · 연기 */
    static final double[] LAVA = C[0];
    static final double[] CORE = C[1];
    static final double[] CHAR = {0.24, 0.08, 0.03};
    static final double[] SMOKE = {0.3, 0.26, 0.24};

    static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();
    static final BlockState BASALT = Blocks.BASALT.defaultBlockState();
    static final BlockState SMOOTH_BASALT = Blocks.SMOOTH_BASALT.defaultBlockState();
    static final BlockState POLISHED_BASALT = Blocks.POLISHED_BASALT.defaultBlockState();
    static final BlockState SHROOM = Blocks.SHROOMLIGHT.defaultBlockState();

    static void register() {
        RuneCast.register("magmaSlam", RuneVolcano::magmaSlam);
        RuneCast.register("lavaBombs", RuneVolcano::lavaBombs);
        RuneCast.register("magmaGeyser", RuneVolcano::magmaGeyser);
        RuneCast.register("magmaField", RuneVolcano::magmaField);
        RuneCast.register("grandEruption", RuneVolcano::grandEruption);
    }

    // ------------------------------------------------------------ 공용: 불꽃 · 화상 · 용암 기둥 · 화산탄

    /** 몸의 불꽃 (화상 한 번) */
    static void flames(ServerLevel level, LivingEntity e) {
        Vec3 b = Kit.bodyPoint(e);
        SkillFx.particle(level, ParticleTypes.FLAME, b, 8, 0.3, 0.03);
        SkillFx.fx(level, "rpg:ember", V.up(e.position(), 0.3), Opt.of("size", 1.2, "life", 0.6, "speed", 1.5, "color", LAVA, "alpha", 0.9));
        if (RuneKit.fxQuota("burnWall", 4)) SkillFx.fx(level, "rpg:fire_wall", e.position(), Opt.of("size", 1.1 + Kit.bodyPad(e), "life", 0.45, "color", LAVA,
                "alpha", 0.85, "spin", Kit.rand() * 360));
    }

    /** 화상: from 틱 뒤부터 gap 틱마다 n 번 위력 × dot (몹 무적 시간을 피해 — 다른 타격과 겹치면 미룸). 쓴 사람이 쓰러지거나 다른 삶이면 멈춤 */
    static void burn(ServerPlayer sp, Runes.Rune r, LivingEntity e, double P, double dot, int n, int gap, int from, String life) {
        if (!(dot > 0) || n <= 0) return;
        for (int j = 0; j < n; j++) {
            RuneKit.later(from + j * gap, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                PairKit.hit(sp, r, e, P, dot, DamageNumbers.Style.BURN, life, dmg -> flames(sp.level(), e));
            });
        }
    }

    /** k 의 화상 (dot · dots · dotGap) 을 from 틱 뒤부터 */
    static void burn(ServerPlayer sp, Runes.Rune r, LivingEntity e, double P, Obj k, int from, String life) {
        burn(sp, r, e, P, k.numOr("dot", 0), (int) k.numOr("dots", 0), (int) Math.max(10, k.numOr("dotGap", 20)), from, life);
    }

    /** 용암 튐 (바닥에서 튀어 떨어지는 용암 방울 n 개 · 용암 알갱이) */
    static void splatter(ServerLevel level, Vec3 at, int n, double speed) {
        for (int i = 0; i < n; i++) {
            double a = Kit.rand() * Math.PI * 2;
            SkillFx.fx(level, "rpg:glob", at, Opt.of("dir", V.unit(new Vec3(Math.cos(a), 1.4 + Kit.rand(), Math.sin(a))), "speed", speed * (0.7 + Kit.rand() * 0.6),
                    "size", 0.18 + Kit.rand() * 0.12, "life", 0.7, "spin", Kit.rand() * 360, "color", i % 3 == 0 ? CORE : LAVA, "alpha", 1.0));
        }
        SkillFx.particle(level, ParticleTypes.LAVA, at, Math.max(2, n / 2), 0.3, 0);
    }

    /** 폭발 (rpg:rfx_boom — 미리 칠한 불꽃, 크기 배율 k) · 불길 고리 · 연기 */
    static void blast(ServerPlayer sp, ServerLevel level, Vec3 at, double radius, double k) {
        double sc = RuneKit.nearK(sp, V.up(at, radius * 0.4), 6, 0.45) * k;
        RuneKit.rfx(level, "boom", V.up(at, radius * 0.35 * sc), Opt.of("size", radius * 0.75 * sc, "life", 0.55, "frames", 8.0, "fps", 15.0));
        for (int j = 0; j < 8; j++) {
            double a = j / 8.0 * Math.PI * 2 + Kit.rand() * 0.3;
            Vec3 p = PairKit.ring(at, radius * 0.6, a);
            if (!PairKit.nearCam(sp, V.up(p, 0.8), 2.2)) SkillFx.fx(level, "rpg:fire_wall", p, Opt.of("size", 1.3, "life", 0.55, "color", LAVA, "alpha", 0.9, "spin",
                    Kit.rand() * 360));
        }
        RuneKit.zonePulse(level, at, LAVA, radius, 0.4);
        SkillFx.particle(level, ParticleTypes.LARGE_SMOKE, V.up(at, 1.0), 12, radius * 0.3, 0.05);
    }

    /**
     * 용암 기둥 (블록 표시 — 마그마가 땅에서 솟구쳤다 가라앉음): base 에 지름 w · 높이 h, delay 틱 뒤 2틱 동안 솟고 hold 틱 뒤 4틱 동안 가라앉음 +
     * 위의 노란 불기둥 · 튀는 용암
     */
    static void geyser(ServerPlayer sp, ServerLevel level, Vec3 base, double w, double h, int hold) {
        if (PairKit.nearCam(sp, V.up(base, 1.5), 2.6)) {
            splatter(level, V.up(base, 0.3), 6, 7);
            return;
        }
        Quaternionf q = PairKit.about(V.UP, Kit.rand() * 90);
        Transformation low = PairKit.box(new Vec3(0, -0.05, 0), q, w * 0.6, 0.1, w * 0.6);
        Display.BlockDisplay col = FxDisplays.block(level, base, MAGMA, low, hold + 10, PairKit.GLOW);
        Display.BlockDisplay core = FxDisplays.block(level, base, SHROOM, PairKit.box(new Vec3(0, -0.05, 0), q, w * 0.35, 0.1, w * 0.35), hold + 10, PairKit.GLOW);
        RuneKit.later(2, () -> {
            FxDisplays.move(col, PairKit.box(new Vec3(0, h / 2, 0), q, w, h, w), 2);
            FxDisplays.move(core, PairKit.box(new Vec3(0, h * 0.55, 0), q, w * 0.55, h * 1.1, w * 0.55), 2);
        });
        RuneKit.later(4 + hold, () -> {
            FxDisplays.move(col, PairKit.box(new Vec3(0, h * 0.1, 0), q, w * 0.5, h * 0.2, w * 0.5), 4);
            FxDisplays.move(core, PairKit.box(new Vec3(0, h * 0.1, 0), q, w * 0.3, h * 0.2, w * 0.3), 4);
        });
        RuneKit.later(3, () -> {
            RuneKit.rfx(level, "fountain", V.up(base, h + 0.6), Opt.of("size", w * 0.9, "life", 0.45, "fade", 0.5, "color", CORE, "alpha", 0.9, "frames", 4.0, "fps",
                    10.0));
            splatter(level, V.up(base, h), 8, 7);
            SkillFx.particle(level, ParticleTypes.FLAME, V.up(base, h * 0.5), 14, w * 0.4, 0.08);
        });
    }

    /** 화산탄 하나: from 에서 to 로 ticks 틱 포물선 (불타는 덩어리 rpg:glob · 연기 꼬리) */
    static void bomb(ServerLevel level, Vec3 from, Vec3 to, int ticks, double size) {
        double t = ticks / 20.0;
        Vec3 v = PairKit.lob(from, to, t, PairKit.GRAVITY);
        SkillFx.fx(level, "rpg:glob", from, Opt.of("dir", V.unit(v), "speed", V.len(v), "size", size, "life", t, "spin", Kit.rand() * 360, "color", LAVA, "alpha",
                1.0));
        SkillFx.fx(level, "rpg:glob", from, Opt.of("dir", V.unit(v), "speed", V.len(v), "size", size * 0.6, "life", t, "spin", Kit.rand() * 360, "color", CORE,
                "alpha", 1.0));
        for (int i = 1; i < ticks; i++) {
            final double s = i / 20.0;
            RuneKit.later(i, () -> {
                Vec3 p = new Vec3(from.x + v.x * s, from.y + v.y * s - PairKit.GRAVITY * s * s / 2, from.z + v.z * s);
                SkillFx.particle(level, ParticleTypes.LARGE_SMOKE, p, 1, 0.05, 0.01);
                SkillFx.particle(level, ParticleTypes.FLAME, p, 2, 0.08, 0.01);
            });
        }
    }

    // ------------------------------------------------------------ 일반: 용암 강타

    /**
     * 용암 강타: 땅을 내리쳐 앞 radius 칸 · cone 도에 용암이 갈라져 튐 (갈라진 땅 · 불길 · 튀는 용암) — 맞은 적 (위력 × mult) 은 화상 (dotGap 틱 뒤부터)
     */
    static boolean magmaSlam(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double radius = k.num("radius");
        double cone = k.num("cone");
        SkillFx.arc(level, V.up(V.add(c, f, 2.4), 0.7), f, Opt.of("shape", "heavy", "flip", false, "size", 1.5, "life", 0.2, "speed", 1.0, "spin", -90.0, "alpha", 0.8, "color",
                LAVA, "aura", CHAR));
        Vec3 hit = V.add(c, f, 1.4);
        SkillFx.fx(level, "rpg:ground_crack", V.up(hit, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 1.1, "life", 1.4, "color", LAVA,
                "alpha", 0.95));
        RuneKit.glowCrack(level, hit, radius * 0.9, LAVA, CORE, Opt.of("life", 1.4, "v", 1.0));
        for (int j = -2; j <= 2; j++) {
            Vec3 dj = RuneKit.turn(f, j * cone / 5.5);
            for (int s = 1; s <= 2; s++) {
                Vec3 p = V.add(c, dj, radius * (0.35 + 0.3 * s));
                final int ss = s;
                if (!PairKit.nearCam(sp, V.up(p, 0.8), 2.0)) RuneKit.later(ss, () -> SkillFx.fx(level, "rpg:fire_wall", p, Opt.of("size", 1.1 + 0.3 * ss, "life", 0.6,
                        "color", LAVA, "alpha", 0.92, "spin", Kit.rand() * 360)));
            }
            SkillFx.fx(level, "rpg:light_cut", V.add(V.up(c, 0.06), dj, radius * 0.55), Opt.of("dir", dj, "size", radius * 0.45, "thick", 0.3, "life", 0.9, "delay",
                    0.6, "color", LAVA, "alpha", 0.9));
        }
        splatter(level, V.up(hit, 0.3), 10, 6);
        SkillFx.burst(level, V.up(hit, 0.5), LAVA, 1.8, 0.3);
        RuneKit.shakeAround(level, c, 8, 0.2, 0.2);
        SkillFx.play(level, "mace.smash_ground", c, 0.8, 1.0);
        SkillFx.play(level, "item.bucket.empty_lava", c, 0.8, 1.0);
        SkillFx.play(level, "fire.ignite", c, 0.8, 0.8);
        for (LivingEntity e : RuneKit.coneAround(sp, c, f, radius, cone)) {
            PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                flames(level, e);
                SkillFx.burst(level, Kit.bodyPoint(e), LAVA, 0.9, 0.22);
            });
            burn(sp, r, e, P, k, (int) Math.max(10, k.numOr("dotGap", 20)), life);
        }
        return true;
    }

    // ------------------------------------------------------------ 희귀: 화산탄

    /**
     * 화산탄: 조준한 곳 (range 칸) 으로 불타는 화산탄 hits 개를 gap 틱 간격으로 던짐 (포물선 · 연기 꼬리) — 떨어진 자리마다 폭발 (반지름 radius 칸,
     * 위력 × mult), 처음 맞은 적은 화상 (마지막 화산탄 뒤부터)
     */
    static boolean lavaBombs(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 g = RuneKit.groundAim(sp, k.num("range"));
        Vec3 f = V.flat(V.sub(g, sp.position()));
        Vec3 side = V.rightOf(f);
        double radius = k.num("radius");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int fly = 8;
        int lastLand = fly + (hits - 1) * gap;
        Map<UUID, LivingEntity> burned = new LinkedHashMap<>();
        SkillFx.rune(level, g, Opt.of("size", radius, "life", (lastLand + 4) / 20.0, "color", LAVA, "alpha", 0.75, "rate", 60.0, "progress", 1.0));
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            Vec3 land = PairKit.onGround(level, V.add(g, side, i == 0 ? 0 : (i % 2 == 1 ? 1.2 : -1.2)));
            RuneKit.later(i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 hand = V.add(V.up(sp.position(), 1.6), Kit.facingOf(sp), 0.8);
                bomb(level, hand, V.up(land, 0.3), fly, 0.75);
                SkillFx.play(level, "entity.blaze.shoot", sp.position(), 0.8 + ii * 0.1, 0.8);
                SkillFx.play(level, "item.firecharge.use", sp.position(), 0.9, 0.6);
            });
            RuneKit.later(i * gap + fly, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                blast(sp, level, land, radius, 0.5);
                splatter(level, V.up(land, 0.3), 10, 6);
                RuneKit.glowCrack(level, land, radius * 0.8, LAVA, CORE, Opt.of("life", 1.0));
                RuneKit.shakeAround(level, land, 10, 0.2, 0.2);
                SkillFx.play(level, "random.explode", land, 1.1, 0.7);
                SkillFx.play(level, "block.lava.pop", land, 0.8, 1.0);
                for (LivingEntity e : Kit.around(sp, land, radius, 3)) {
                    PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> flames(level, e));
                    if (burned.putIfAbsent(e.getUUID(), e) == null) burn(sp, r, e, P, k, lastLand - (ii * gap + fly) + (int) Math.max(10, k.numOr("dotGap", 20)), life);
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 고귀: 용암 분출

    /**
     * 용암 분출: 조준한 곳 (range 칸) 의 땅이 용암빛으로 갈라지고 gap 틱마다 hits 번 용암 기둥 (블록 표시 — 마그마 · 노란 불기둥) 이 솟구쳐
     * 반지름 radius 칸의 적 (위력 × mult, 띄움 lift), 처음 맞은 적은 화상 (마지막 분출 뒤부터)
     */
    static boolean magmaGeyser(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 g = RuneKit.groundAim(sp, k.num("range"));
        double radius = k.num("radius");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int first = 6;
        int last = first + (hits - 1) * gap;
        Map<UUID, LivingEntity> burned = new LinkedHashMap<>();
        RuneKit.glowCrack(level, g, radius * 1.05, LAVA, CORE, Opt.of("life", (last + 14) / 20.0, "v", 1.0));
        SkillFx.fx(level, "rpg:ground_crack", V.up(g, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 1.3, "life", (last + 14) / 20.0,
                "color", CHAR, "alpha", 0.9));
        SkillFx.play(level, "block.basalt.break", g, 0.6, 1.0);
        SkillFx.play(level, "block.lava.ambient", g, 0.8, 1.0);
        for (int t = 0; t < first; t += 2) {
            RuneKit.later(t, () -> {
                splatter(level, V.up(g, 0.2), 2, 3);
                SkillFx.particle(level, ParticleTypes.SMOKE, V.up(g, 0.3), 8, radius * 0.4, 0.02);
            });
        }
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            RuneKit.later(first + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                // 가운데의 큰 기둥 + 둘레의 작은 기둥 둘
                geyser(sp, level, g, 1.5, 4.5 + ii * 0.4, 4);
                for (int j = 0; j < 2; j++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 p = PairKit.onGround(level, PairKit.ring(g, radius * (0.45 + 0.3 * Kit.rand()), a));
                    final int jj = j;
                    RuneKit.later(1 + j, () -> geyser(sp, level, p, 0.8, 2.4 + jj * 0.5, 3));
                }
                RuneKit.zonePulse(level, g, ii % 2 == 0 ? LAVA : CORE, radius, 0.4);
                RuneKit.shakeAround(level, g, 10, 0.2, 0.25);
                SkillFx.play(level, "item.bucket.empty_lava", g, 0.6 + ii * 0.08, 1.0);
                SkillFx.play(level, "mob.ghast.fireball", g, 0.8, 0.7);
                for (LivingEntity e : Kit.around(sp, g, radius, 3)) {
                    PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                        flames(level, e);
                        if (!PairKit.boss(e)) Kit.knockback(e, 0, 0, k.num("lift"));
                    });
                    if (burned.putIfAbsent(e.getUUID(), e) == null) burn(sp, r, e, P, k, last - (first + ii * gap) + (int) Math.max(10, k.numOr("dotGap", 20)), life);
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 영웅: 마그마 대지

    /** 마그마 대지의 판 하나 (표시 · 다 퍼진 변환) */
    private record Tile(Display.BlockDisplay d, Transformation t) {}

    /**
     * 마그마 대지: 조준한 곳 (range 칸) 의 땅이 가운데부터 녹아 반지름 radius 칸의 마그마 대지 (블록 표시 마그마 판 · 갈라진 용암빛) 가 됨 — 녹을 때 안의 적
     * (위력 × mult) → 대지 위의 적은 dotGap 틱마다 화상 (위력 × dot, dots 번 — 대분출 틱은 비움) → delay 틱 뒤 대분출 (용암 기둥 다섯 · 폭발 — 위력 × crush, 띄움)
     */
    static boolean magmaField(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 g = RuneKit.groundAim(sp, k.num("range"));
        double radius = k.num("radius");
        int delay = (int) k.num("delay");
        int dots = (int) k.num("dots");
        int dotGap = (int) Math.max(10, k.num("dotGap"));
        // 화상 틱: dotGap 마다, 대분출 틱 (delay) 과 겹치는 것은 건너뜀
        List<Integer> burnTicks = new ArrayList<>();
        for (int t = dotGap; burnTicks.size() < dots; t += dotGap) if (Math.abs(t - delay) >= 10) burnTicks.add(t);
        int end = burnTicks.get(burnTicks.size() - 1) + 8;
        // 마그마 판 (가운데 → 바깥 고리)
        List<Tile> tiles = new ArrayList<>();
        int ring = 0;
        for (double rr = 0; rr <= radius - 0.6; rr += 1.6, ring++) {
            int n = rr < 0.1 ? 1 : (int) Math.round(Math.PI * 2 * rr / 1.6);
            for (int j = 0; j < n; j++) {
                double a = j / (double) n * Math.PI * 2 + ring * 0.4;
                Vec3 p = PairKit.ring(g, rr, a);
                double gy = PairKit.groundY(level, p.x, p.z, g.y);
                if (Math.abs(gy - g.y) > 2.5) continue;
                Vec3 base = new Vec3(p.x, gy, p.z);
                Quaternionf q = PairKit.about(V.UP, Kit.rand() * 90);
                double s = 1.9 + Kit.rand() * 0.3;
                Transformation full = PairKit.box(new Vec3(0, 0.04, 0), q, s, 0.1, s);
                final int delayT = ring * 2;
                Display.BlockDisplay d = FxDisplays.block(level, base, MAGMA, PairKit.box(new Vec3(0, 0.02, 0), q, 0.2, 0.04, 0.2), end + 6, PairKit.GLOW);
                if (d == null) continue;
                tiles.add(new Tile(d, full));
                RuneKit.later(2 + delayT, () -> FxDisplays.move(d, full, 3));
            }
        }
        SkillFx.fx(level, "rpg:ground_crack", V.up(g, 0.12), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 1.2, "life", end / 20.0, "color", CHAR,
                "alpha", 0.85));
        RuneKit.glowCrack(level, g, radius, LAVA, CORE, Opt.of("life", end / 20.0, "v", 1.0, "spinRate", 4.0));
        SkillFx.play(level, "item.bucket.empty_lava", g, 0.6, 1.0);
        SkillFx.play(level, "block.lava.ambient", g, 0.7, 1.0);
        // 녹아내림
        splatter(level, V.up(g, 0.3), 12, 5);
        RuneKit.zonePulse(level, g, LAVA, radius, 0.5);
        for (LivingEntity e : Kit.around(sp, g, radius, 3)) {
            PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> flames(level, e));
        }
        // 대지의 숨 (용암 거품 · 불길 · 연기)
        for (int t = 4; t < end; t += 4) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (int j = 0; j < 2; j++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 p = PairKit.ring(g, Math.sqrt(Kit.rand()) * radius * 0.9, a);
                    SkillFx.particle(level, ParticleTypes.LAVA, V.up(p, 0.2), 1, 0.1, 0);
                    if (!PairKit.nearCam(sp, V.up(p, 0.8), 2.0)) SkillFx.fx(level, "rpg:fire_wall", p, Opt.of("size", 0.9 + Kit.rand() * 0.4, "life", 0.5, "color",
                            LAVA, "alpha", 0.8, "spin", Kit.rand() * 360));
                }
                SkillFx.particle(level, ParticleTypes.SMOKE, V.up(g, 0.4), 6, radius * 0.4, 0.02);
                if (tt % 12 == 0) SkillFx.play(level, "block.lava.pop", g, 0.8 + Kit.rand() * 0.4, 0.8);
            });
        }
        // 화상 (대지 위에 있는 동안)
        for (int bt : burnTicks) {
            RuneKit.later(bt, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                RuneKit.zonePulse(level, g, CHAR, radius, 0.3);
                for (LivingEntity e : Kit.around(sp, g, radius, 2.5)) {
                    PairKit.hit(sp, r, e, P, k.num("dot"), DamageNumbers.Style.BURN, life, dmg -> flames(level, e));
                }
            });
        }
        // 대분출
        RuneKit.later(delay - 6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.glowCrack(level, g, radius * 1.1, CORE, LAVA, Opt.of("life", 0.5, "grow", 0.2));
            SkillFx.play(level, "block.lava.extinguish", g, 0.5, 1.0);
            RuneKit.shakeAround(level, g, 12, 0.2, 0.3);
        });
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            geyser(sp, level, g, 2.0, 6.0, 6);
            for (int j = 0; j < 4; j++) {
                double a = j / 4.0 * Math.PI * 2 + 0.4;
                Vec3 p = PairKit.onGround(level, PairKit.ring(g, radius * 0.6, a));
                final int jj = j;
                RuneKit.later(jj, () -> geyser(sp, level, p, 1.2, 3.6 + jj * 0.3, 5));
            }
            blast(sp, level, g, radius, 0.75);
            RuneKit.novaRing(level, g, 1.2, radius * 0.9, LAVA, CORE, Opt.of("life", 0.5));
            RuneKit.shakeAround(level, g, 16, 0.4, 0.4);
            SkillFx.play(level, "random.explode", g, 0.6, 1.0);
            SkillFx.play(level, "mob.ghast.fireball", g, 0.6, 1.0);
            for (LivingEntity e : Kit.around(sp, g, radius, 3)) {
                PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.ERUPT, life, dmg -> {
                    flames(level, e);
                    if (!PairKit.boss(e)) Kit.knockback(e, 0, 0, 0.6);
                });
            }
        });
        // 식음: 판이 오그라들며 가라앉음
        RuneKit.later(end, () -> {
            for (Tile t : tiles) {
                var tr = t.t().translation();
                FxDisplays.move(t.d(), new Transformation(new org.joml.Vector3f(tr.x() * 0.4F, tr.y() - 0.1F, tr.z() * 0.4F), t.t().leftRotation(),
                        new org.joml.Vector3f(0.4F, 0.05F, 0.4F), new Quaternionf()), 5);
            }
            SkillFx.play(level, "block.lava.extinguish", g, 0.8, 0.8);
            SkillFx.particle(level, ParticleTypes.LARGE_SMOKE, V.up(g, 0.4), 20, radius * 0.4, 0.04);
        });
        return true;
    }

    // ------------------------------------------------------------ 초월: 대분화

    /** 화산 모형 (현무암 층 · 용암이 흐르는 비탈 · 빛나는 분화구 — 키 약 5.5칸, 꼭대기 = (0, 5.4, 0)) */
    private static PairKit.Rig volcano(ServerLevel level, Vec3 base, int life, Vec3 hidden) {
        PairKit.Rig v = new PairKit.Rig(level, base, life);
        v.offset = hidden;
        double[][] layers = {{6.2, 1.3, 0.65, 0}, {5.0, 1.3, 1.9, 20}, {3.8, 1.3, 3.15, 40}, {2.8, 1.2, 4.35, 10}};
        BlockState[] sts = {BASALT, SMOOTH_BASALT, BASALT, POLISHED_BASALT};
        for (int i = 0; i < layers.length; i++) {
            double[] l = layers[i];
            v.add(0, sts[i], new Vec3(0, l[2], 0), new Vec3(l[0], l[1], l[0]), PairKit.about(V.UP, l[3]), PairKit.LIT);
        }
        v.add(MAGMA, new Vec3(0, 5.05, 0), 2.3, 0.3, 2.3, PairKit.GLOW);
        v.add(SHROOM, new Vec3(0, 5.25, 0), 1.4, 0.2, 1.4, PairKit.GLOW);
        // 비탈의 용암 줄기 셋
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(i * 120 + 30);
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 axis = V.cross(V.UP, out);
            v.add(0, MAGMA, V.add(new Vec3(0, 3.0, 0), out, 1.95), new Vec3(0.45, 3.6, 0.2), PairKit.about(axis, 38), PairKit.GLOW);
        }
        return v;
    }

    /**
     * 대분화: 조준한 곳 (range 칸) 에 잿빛 하늘 (3돌부터) · 붉은 영역 문양 → 조준한 곳 너머의 땅에서 화산 (블록 표시 — 현무암 층 · 용암 줄기 · 빛나는 분화구)
     * 이 delay 틱 동안 솟아 연기를 뿜음 → gap 틱마다 hits 번 분화구에서 화산탄이 반지름 radius 칸의 적마다 날아와 터짐 (위력 × mult) → 대분화 (용암 기둥 ·
     * 거대한 폭발 · 불길 고리 — 위력 × crush, 띄움), 3돌부터 분화 뒤 화상 (dot × dots). 대상이 없어도 씀
     */
    static boolean grandEruption(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 aim = RuneKit.groundAim(sp, k.num("range"));
        Vec3 f = V.flat(V.sub(aim, sp.position()));
        double radius = k.num("radius");
        int delay = (int) k.num("delay");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int fly = 6;
        int lastHit = delay + (hits - 1) * gap;
        int crushAt = lastHit + 16;
        int end = crushAt + 20;
        Vec3 vc = PairKit.onGround(level, V.add(aim, f, 4.6));
        RuneKit.ultSkyFor(sp, level, aim, "grandEruption", end + 30, null);
        RuneKit.ultFlashFor(sp, level, aim, "grandEruption", "flash", Opt.empty());
        RuneKit.domainSigil(level, aim, radius, "arcane", LAVA, CORE, Opt.of("life", end / 20.0, "spinRate", 20.0, "grow", 0.15, "alpha", 0.8, "fade", 0.15));
        RuneKit.selfCue(sp, LAVA, CORE, true);
        SkillFx.play(level, "block.lava.ambient", vc, 0.5, 1.0);
        SkillFx.play(level, "mob.warden.emerge", vc, 0.7, 1.0);
        SkillFx.play(level, "mob.wither.spawn", sp.position(), 1.5, 0.4);
        // 화산이 솟음
        PairKit.Rig vol = volcano(level, vc, end + 4, new Vec3(0, -6.2, 0));
        RuneKit.later(2, () -> vol.pose(new Vec3(0, -0.4, 0), new Quaternionf(), 1.0, delay - 4));
        RuneKit.later(delay - 2, () -> vol.pose(Vec3.ZERO, new Quaternionf(), 1.0, 2));
        RuneKit.glowCrack(level, vc, 4.0, LAVA, CORE, Opt.of("life", (end + 4) / 20.0, "v", 1.0));
        for (int t = 0; t < crushAt; t += 3) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double u = Math.min(1, tt / (double) delay);
                Vec3 top = V.up(vc, 5.4 * u - 0.4);
                SkillFx.particle(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, V.up(top, 0.6), 3, 0.5, 0.02);
                SkillFx.particle(level, ParticleTypes.LARGE_SMOKE, V.up(top, 0.4), 4, 0.6, 0.05);
                SkillFx.particle(level, ParticleTypes.LAVA, top, 2, 0.4, 0);
                if (tt < delay) {
                    RuneKit.shakeAround(level, vc, 18, 0.1 + 0.15 * u, 0.2);
                    SkillFx.particle(level, ParticleTypes.DUST_PLUME, V.up(vc, 0.3), 6, 2.5, 0.05);
                    if (tt % 9 == 0) SkillFx.play(level, "block.basalt.break", vc, 0.5, 1.0);
                }
            });
        }
        // 화산탄
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            RuneKit.later(delay + i * gap - fly, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 top = V.up(vc, 5.6);
                List<LivingEntity> ts = Kit.around(sp, aim, radius, 5);
                List<Vec3> spots = new ArrayList<>();
                for (int j = 0; j < Math.min(6, ts.size()); j++) spots.add(PairKit.onGround(level, ts.get(j).position()));
                for (int j = 0; j < 2; j++) {
                    double a = Kit.rand() * Math.PI * 2;
                    spots.add(PairKit.onGround(level, PairKit.ring(aim, Math.sqrt(Kit.rand()) * radius, a)));
                }
                for (Vec3 p : spots) bomb(level, top, V.up(p, 0.4), fly, 0.85);
                splatter(level, top, 6, 6);
                SkillFx.play(level, "mob.ghast.fireball", top, 0.6 + ii * 0.05, 1.0);
                RuneKit.later(fly, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    for (Vec3 p : spots) {
                        if (RuneKit.fxQuota("eruptBomb", 8)) {
                            RuneKit.rfx(level, "boom", V.up(p, 0.9), Opt.of("size", 1.6, "life", 0.45, "frames", 8.0, "fps", 18.0));
                            SkillFx.fx(level, "rpg:shock_ring", V.up(p, 0.08), Opt.of("dir", V.ZERO, "size", 0.9, "life", 0.35, "color", LAVA, "alpha", 0.9));
                        }
                    }
                    RuneKit.zonePulse(level, aim, ii % 2 == 0 ? LAVA : CORE, radius, 0.4);
                    SkillFx.play(level, "random.explode", aim, 1.2, 0.6);
                    for (LivingEntity e : ts) PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                        if (RuneKit.fxQuota("eruptFlame", 6)) flames(level, e);
                    });
                });
            });
        }
        // 대분화
        RuneKit.later(crushAt - 8, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            SkillFx.play(level, "block.lava.extinguish", vc, 0.5, 1.0);
            SkillFx.play(level, "mob.warden.sonic_charge", vc, 0.6, 1.0);
            RuneKit.shakeAround(level, aim, 20, 0.25, 0.4);
            vol.pose(new Vec3(0, 0.3, 0), new Quaternionf(), 1.06, 6);
        });
        RuneKit.later(crushAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.ultFlashFor(sp, level, aim, "grandEruption", "flash", Opt.empty());
            Vec3 top = V.up(vc, 5.6);
            geyser(sp, level, V.up(vc, 4.8), 2.4, 7.0, 8);
            for (int j = 0; j < 5; j++) {
                double a = j / 5.0 * Math.PI * 2 + 0.3;
                Vec3 p = PairKit.onGround(level, PairKit.ring(aim, radius * 0.55, a));
                final int jj = j;
                RuneKit.later(jj, () -> geyser(sp, level, p, 1.3, 3.8 + jj * 0.3, 6));
            }
            for (int j = 0; j < 10; j++) {
                double a = Kit.rand() * Math.PI * 2;
                bomb(level, top, V.up(PairKit.onGround(level, PairKit.ring(aim, radius * (0.5 + 0.5 * Kit.rand()), a)), 0.3), 10, 0.7);
            }
            blast(sp, level, aim, radius, 0.9);
            RuneKit.rfx(level, "boom", V.up(top, 1.5), Opt.of("size", 4.0, "life", 0.8, "frames", 8.0, "fps", 10.0));
            RuneKit.novaRing(level, aim, 1.8, radius * 0.9, LAVA, CORE, Opt.of("life", 0.6));
            RuneKit.glowCrack(level, aim, radius * 1.1, LAVA, CORE, Opt.of("life", 2.0, "v", 1.0));
            SkillFx.particle(level, ParticleTypes.LARGE_SMOKE, V.up(top, 2), 40, 2.5, 0.12);
            RuneKit.shakeAround(level, aim, 26, 0.6, 0.6);
            SkillFx.play(level, "random.explode", aim, 0.45, 1.0);
            SkillFx.play(level, "mob.ghast.fireball", top, 0.4, 1.0);
            SkillFx.play(level, "item.bucket.empty_lava", aim, 0.5, 1.0);
            int burnFrom = (int) Math.max(10, k.numOr("dotGap", 20));
            for (LivingEntity e : Kit.around(sp, aim, radius, 5)) {
                PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.ERUPT, life, dmg -> {
                    flames(level, e);
                    if (!PairKit.boss(e)) Kit.knockback(e, 0, 0, 0.7);
                });
                burn(sp, r, e, P, k, burnFrom, life);
            }
        });
        // 화산이 무너져 가라앉음
        RuneKit.later(crushAt + 10, () -> {
            vol.crumble(10, 0.15, 3.0, 0.6);
            SkillFx.particle(level, ParticleTypes.LARGE_SMOKE, V.up(vc, 2), 30, 2.5, 0.05);
            SkillFx.play(level, "block.basalt.break", vc, 0.4, 1.0);
        });
        return true;
    }
}
