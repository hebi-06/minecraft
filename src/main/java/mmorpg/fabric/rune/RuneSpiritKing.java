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

/**
 * 정령왕 강림 (12단계 — 정령사 초월 룬 리메이크, 자바 전용 spiritKing). 수치는 RUNES.spirit.transcend (rules_more.json), 돌파 3 · 6돌은 rune_break.json.
 * <ol>
 *   <li>네 정령 (샐러맨더 · 운디네 · 실프 · 노움) 이 화면 위쪽에 나타나 정령마다 hits ÷ 4 번 (한 번) 공격 (mult — 정령사 룬과 같은 정령 공격, first 틱 뒤부터
 *       stagger 틱씩 늦게). 한 적에게 모든 타격이 몹 무적 시간 (10틱) 을 피해 들어가도록 정령 공격은 합체 전에, 원소 타격은 10틱 간격</li>
 *   <li>ticks 틱에 합체: 네 정령이 빛줄기를 그리며 앞쪽 하늘의 한 점으로 모임 → 빛의 고치 → 거대한 정령왕 (rpg:rfx_spiritking — 약 7칸, 네 원소 빛 날개 ·
 *       원소 왕관) 이 내려와 팔을 듦. 조준한 땅 (range 칸) 의 반지름 radius 칸이 원소의 자리 (정령 문양)</li>
 *   <li>elemGap 틱마다 원소 마무리 넷: 불기둥 (불붙음 fire 초) · 해일 (가운데에서 밀쳐냄 knock) · 회오리 (가운데로 끌어당김 pull) · 바위 가시 (구속 II slowTicks) —
 *       각각 elemMult</li>
 *   <li>burstDelay 틱 뒤 원소 붕괴: 정령왕의 네 구슬에서 빛줄기가 가운데로 모여 무지갯빛 폭발 (burstMult, 6돌: 기절 stun 틱) → 정령왕이 빛으로 흩어짐</li>
 * </ol>
 * 원소 마무리 · 붕괴는 정령이 노리는 적만 (적대 몹 · 싸운 몹 — RuneSpirit.spiritFoe), 마력 피해 (runeHit). 쓰러지거나 차원을 옮기면 남은 것은 멈춤.
 * 화면 색 연출 (하늘빛 · 번쩍임) 은 3돌부터 (돌파 — 오로라 하늘 · 정령 빛)
 */
final class RuneSpiritKing {
    private RuneSpiritKing() {}

    static final double[] FIRE = {1.0, 0.45, 0.12};
    static final double[] WATER = {0.25, 0.58, 1.0};
    static final double[] WIND = {0.42, 1.0, 0.6};
    static final double[] EARTH = {0.88, 0.62, 0.3};
    static final double[] WHITE = {1, 1, 1};
    /** 정령왕 그림 칸 (rfx_spiritking: 팔을 내림 0 → 반쯤 1 → 듦 2 → 든 채 빛남 3) */
    static final int RAISED = 2;
    static final int PULSE = 3;
    /** 정령왕 키 (칸) */
    static final double KING_SIZE = 7.4;

    static void register() {
        RuneCast.register("spiritKing", RuneSpiritKing::cast);
    }

    static boolean cast(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 feet = sp.position();
        double[][] cs = RuneKit.colorOf("spirit");
        List<RuneSpirit.Spirit> spirits = RuneSpirit.spawnSpirits(sp, P, k, null);
        SkillFx.rune(level, feet, Opt.of("size", 1.8, "life", 0.9, "color", cs[0], "alpha", 0.9, "rate", 140.0, "progress", 1.0, "grow", 0.2));
        RuneKit.domainSigil(level, feet, 3.2, "spirit", cs[0], cs[1], Opt.of("life", 1.6, "spinRate", 40.0, "grow", 0.25, "alpha", 0.85));
        SkillFx.play(level, "mob.evocation_illager.prepare_summon", feet, 1.2, 0.8);
        SkillFx.play(level, "mob.allay.idle", feet, 1.0, 0.9);
        RuneKit.later(k.num("ticks"), () -> {
            if (RuneKit.sameLife(sp, life)) fuse(sp, P, k, life, spirits);
        });
        return true;
    }

    /** 원소의 자리: 조준한 땅 (range 칸) */
    static Vec3 zone(ServerPlayer sp, Obj k) {
        return RuneKit.groundAim(sp, k.num("range"));
    }

    /** 정령왕이 설 자리: 원소의 자리 너머 (나에게서 멀어지는 쪽 — 자리가 가까우면 보는 쪽) 반지름 + 1.5칸, 땅 높이 */
    static Vec3 kingBase(ServerPlayer sp, Vec3 z, double r) {
        Vec3 d = V.flat(V.sub(z, sp.position()));
        Vec3 u = V.len(d) > 1.5 ? V.unit(d) : Kit.facingOf(sp);
        Vec3 b = V.add(z, u, r + 1.5);
        Double g = RuneKit.solidGround(sp.level(), V.up(b, 3), 8);
        return new Vec3(b.x, g != null ? g : z.y, b.z);
    }

    /** 합체 → 강림 → 원소 마무리 → 붕괴 */
    private static void fuse(ServerPlayer sp, double P, Obj k, String life, List<RuneSpirit.Spirit> spirits) {
        ServerLevel level = sp.level();
        double r = k.num("radius");
        Vec3 z = zone(sp, k);
        Vec3 base = kingBase(sp, z, r);
        Vec3 center = V.up(base, KING_SIZE * 0.5);
        Vec3 heart = V.up(base, KING_SIZE * 0.6); // 가슴의 원소 결정 (합체하는 점)
        // 1) 네 정령이 빛줄기를 그리며 한 점으로 모임
        List<Vec3> from = RuneSpirit.recallSpirits(sp, spirits);
        String[] kinds = {"salamander", "undine", "sylph", "gnome"};
        for (int i = 0; i < from.size() && i < spirits.size(); i++) {
            double[][] c = RuneSpirit.spiritColor(spirits.get(i).kind);
            RuneKit.flyStreak(level, from.get(i), heart, 24, c[0], c[1], 0.5, 0.35);
            RuneKit.rfx(level, "spirit", from.get(i), Opt.of("size", 0.4, "life", 0.4, "cell", (double) RuneKit.SPIRIT_CELL.getOrDefault(spirits.get(i).kind, 0),
                    "frames", 4.0, "fps", 10.0, "loop", true, "dir", V.unit(V.sub(heart, from.get(i))), "speed", V.len(V.sub(heart, from.get(i))) / 0.4, "fade", 0.3));
        }
        if (from.isEmpty()) {
            for (int i = 0; i < 4; i++) {
                double[][] c = RuneSpirit.spiritColor(kinds[i]);
                RuneKit.streak(level, V.up(sp.getEyePosition(), 0.6), heart, c[0], 0.1, 0.4, 0.8, i * 0.05);
            }
        }
        SkillFx.play(level, "beacon.power", sp.position(), 1.5, 0.9);
        SkillFx.play(level, "mob.allay.item_given", heart, 1.2, 1.0);
        // 2) 빛의 고치 → 번쩍 → 정령왕 강림 (팔을 듦)
        RuneKit.later(6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.rfx2(level, "nova", heart, Opt.of("size", 0.6, "grow", 4.0, "life", 0.5, "fade", 0.6, "alpha", 0.9, "dir", RuneKit.towardEye(sp, heart)), WHITE,
                    WHITE, new int[] {0, 1}, null);
            RuneKit.skyFlare(level, heart, 3.0, new double[] {1, 0.95, 0.8}, WHITE, Opt.of("life", 0.5, "grow", 1.2));
            RuneKit.ultSkyFor(sp, level, z, "spiritKing", 110, "rpg:ult_aurora");
            RuneKit.ultFlashFor(sp, level, z, "spiritKing", "flash", Opt.empty());
            SkillFx.play(level, "random.totem", heart, 1.2, 0.7);
            SkillFx.play(level, "beacon.activate", heart, 0.8, 1.0);
        });
        int elemGap = (int) k.num("elemGap");
        int burstAt = 14 + elemGap * 3 + (int) k.num("burstDelay");
        double kingLife = (burstAt - 8 + 14) / 20.0;
        RuneKit.later(8, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            // 내려옴 (위에서 0.4초) → 그 자리에서 팔을 들고 머묾
            double drop = 3.0;
            RuneKit.rfx(level, "spiritking", V.up(center, drop), Opt.of("size", KING_SIZE, "life", 0.4, "cell", 0.0, "frames", 2.0, "fps", 5.0, "alpha", 0.95,
                    "vel", new Vec3(0, -drop / 0.4, 0), "fade", 0.05));
            RuneKit.later(8, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                RuneKit.rfx(level, "spiritking", center, Opt.of("size", KING_SIZE, "life", kingLife - 0.4, "cell", 1.0, "frames", 2.0, "fps", 4.0, "alpha", 0.95,
                        "fade", 0.25, "grow", 0.03));
                RuneKit.domainSigil(level, base, 3.4, "spirit", new double[] {0.42, 1, 0.76}, WHITE, Opt.of("life", kingLife - 0.3, "spinRate", 25.0, "alpha", 0.8));
                RuneKit.domainSigil(level, z, r, "spirit", new double[] {0.42, 1, 0.76}, WHITE, Opt.of("life", kingLife - 0.3, "spinRate", -20.0, "alpha", 0.7));
                RuneKit.godRays(sp, level, base, 2.2, 4, WHITE, WHITE, Opt.of("h", 14.0, "life", 1.0, "colors",
                        new double[][][] {{FIRE, WHITE}, {WATER, WHITE}, {WIND, WHITE}, {EARTH, WHITE}}, "skipNear", true));
                SkillFx.play(level, "mob.evocation_illager.cast_spell", base, 0.7, 1.0);
                SkillFx.shake(sp, 0.25, 0.3);
            });
        });
        // 3) 원소 마무리 넷
        RuneKit.later(14, () -> elemFire(sp, P, k, life, z, center));
        RuneKit.later(14 + elemGap, () -> elemWater(sp, P, k, life, z, base));
        RuneKit.later(14 + elemGap * 2, () -> elemWind(sp, P, k, life, z));
        RuneKit.later(14 + elemGap * 3, () -> elemEarth(sp, P, k, life, z));
        // 4) 원소 붕괴
        RuneKit.later(burstAt, () -> burst(sp, P, k, life, z, center));
    }

    /** 원소의 자리 안의 적 (정령이 노리는 적) */
    static List<LivingEntity> foes(ServerPlayer sp, Vec3 z, double r) {
        return RuneSpirit.spiritFoesAround(sp, z, r);
    }

    /**
     * 원소 타격 한 번 (마력 — runeHit): 몹 무적 시간을 피해 넣음 (앞의 정령 공격 · 원소 타격에서 10틱 뒤로 — Kit.reserveHit · hitAt),
     * 들어가면 then (밀치기 · 상태)
     */
    static void elemHit(ServerPlayer sp, String life, LivingEntity e, double P, double mult, java.util.function.Consumer<LivingEntity> then) {
        Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), () -> RuneKit.sameLife(sp, life), () -> {
            if (RuneKit.runeHit(sp, e, P, mult, "int") > 0 && then != null) then.accept(e);
        });
    }

    /** 정령왕이 빛남 (든 채 빛나는 그림을 잠깐 겹침) */
    private static void pulse(ServerPlayer sp, Vec3 center) {
        RuneKit.rfx(sp.level(), "spiritking", center, Opt.of("size", KING_SIZE * 1.01, "life", 0.3, "cell", (double) PULSE, "alpha", 0.7, "fade", 0.6));
    }

    private static void elemFire(ServerPlayer sp, double P, Obj k, String life, Vec3 z, Vec3 center) {
        if (!RuneKit.sameLife(sp, life)) return;
        ServerLevel level = sp.level();
        double r = k.num("radius");
        pulse(sp, center);
        Vec3 hand = V.up(center, 1.4);
        List<LivingEntity> list = foes(sp, z, r);
        List<Vec3> spots = new ArrayList<>();
        for (LivingEntity e : list) spots.add(e.position());
        for (int i = spots.size(); i < 4; i++) {
            double a = Kit.rand() * Math.PI * 2;
            double rr = r * Math.sqrt(Kit.rand()) * 0.8;
            spots.add(new Vec3(z.x + Math.cos(a) * rr, z.y, z.z + Math.sin(a) * rr));
        }
        for (Vec3 s : spots) {
            RuneKit.flyStreak(level, hand, V.up(s, 0.5), 40, FIRE, new double[] {1, 0.9, 0.5}, 0.5, 0.35);
            SkillFx.fx(level, "rpg:fire_wall", s, Opt.of("size", 1.6, "life", 0.8, "color", FIRE, "alpha", 0.95, "spin", Kit.rand() * 360));
            RuneKit.rfx(level, "fountain", s, Opt.of("size", 2.6, "life", 0.7, "fade", 0.5, "color", FIRE, "alpha", 0.9, "frames", 4.0, "fps", 8.0));
            SkillFx.burst(level, V.up(s, 1.0), FIRE, 1.2, 0.3);
        }
        SkillFx.play(level, "mob.blaze.shoot", z, 0.7, 1.0);
        SkillFx.play(level, "fire.ignite", z, 0.8, 1.0);
        for (LivingEntity e : list) elemHit(sp, life, e, P, k.num("elemMult"), x -> RuneKit.burn(x, k.num("fire")));
    }

    private static void elemWater(ServerPlayer sp, double P, Obj k, String life, Vec3 z, Vec3 base) {
        if (!RuneKit.sameLife(sp, life)) return;
        ServerLevel level = sp.level();
        double r = k.num("radius");
        // 정령왕 쪽에서 자리를 가로지르는 해일 (커다란 물결 두 겹) + 물보라
        Vec3 u = V.flat(V.sub(z, base));
        u = V.len(u) > 0.2 ? V.unit(u) : Kit.facingOf(sp);
        Vec3 start = V.up(V.add(z, u, -r * 0.8), 1.2);
        RuneKit.rfx2(level, "wave", start, Opt.of("dir", u, "size", r * 1.6, "life", 0.6, "speed", r * 2.6, "fade", 0.5, "alpha", 0.9), WATER,
                new double[] {0.85, 0.95, 1}, new int[] {0, 1}, null);
        RuneKit.rfx2(level, "wave", V.up(start, 0.8), Opt.of("dir", u, "size", r * 1.2, "life", 0.55, "speed", r * 2.4, "fade", 0.5, "alpha", 0.7), WATER, WHITE,
                new int[] {0, 1}, null);
        for (int i = 0; i < 5; i++) {
            Vec3 p = V.add(V.add(z, u, (i - 2) * r * 0.35), V.rightOf(u), (Kit.rand() - 0.5) * r);
            SkillFx.fx(level, "rpg:splash", V.up(p, 0.8), Opt.of("size", 1.4, "life", 0.6, "color", WATER, "alpha", 0.95));
        }
        SkillFx.play(level, "random.splash", z, 0.6, 1.0);
        SkillFx.play(level, "bucket.empty_water", z, 0.7, 1.0);
        for (LivingEntity e : foes(sp, z, r)) elemHit(sp, life, e, P, k.num("elemMult"), x -> RuneKit.knock(x, V.sub(x.position(), z), k.num("knock"), 0.3));
    }

    private static void elemWind(ServerPlayer sp, double P, Obj k, String life, Vec3 z) {
        if (!RuneKit.sameLife(sp, life)) return;
        ServerLevel level = sp.level();
        double r = k.num("radius");
        // 가운데의 커다란 회오리 + 둘레를 도는 작은 회오리 셋 → 적을 가운데로 끌어당김
        RuneKit.vortexAt(level, z, r * 0.55, WIND, Opt.of("life", 0.8, "alpha", 0.9));
        for (int i = 0; i < 3; i++) {
            double a = i / 3.0 * Math.PI * 2;
            Vec3 at = new Vec3(z.x + Math.cos(a) * r * 0.6, z.y, z.z + Math.sin(a) * r * 0.6);
            RuneKit.vortexAt(level, at, r * 0.25, WIND, Opt.of("life", 0.6, "alpha", 0.8, "vel", V.scale(new Vec3(-Math.sin(a), 0, Math.cos(a)), 4)));
        }
        SkillFx.fx(level, "rpg:whirlpool", V.up(z, 0.1), Opt.of("dir", V.ZERO, "size", r * 0.9, "life", 0.7, "spin", 0.0, "speed", -600.0, "color", WIND, "alpha", 0.6));
        SkillFx.play(level, "mob.breeze.whirl", z, 0.7, 1.0);
        SkillFx.play(level, "wind_charge.burst", z, 0.8, 0.9);
        for (LivingEntity e : foes(sp, z, r)) elemHit(sp, life, e, P, k.num("elemMult"), x -> RuneKit.knock(x, V.sub(z, x.position()), k.num("pull"), 0.25));
    }

    private static void elemEarth(ServerPlayer sp, double P, Obj k, String life, Vec3 z) {
        if (!RuneKit.sameLife(sp, life)) return;
        ServerLevel level = sp.level();
        double r = k.num("radius");
        List<LivingEntity> list = foes(sp, z, r);
        RuneKit.rockRing(sp, level, z, r * 0.7, 8, 1.6, EARTH, Opt.of("life", 1.0));
        for (LivingEntity e : list) RuneKit.rockSpike(sp, level, e.position(), 2.0, EARTH, Opt.of("life", 1.0));
        RuneKit.glowCrack(level, z, r, EARTH, new double[] {1, 0.9, 0.66}, Opt.of("life", 1.4));
        RuneKit.rockBurst(level, V.up(z, 0.3), 8, 6, 0.35);
        SkillFx.play(level, "mace.heavy_smash_ground", z, 0.6, 1.0);
        SkillFx.play(level, "dig.stone", z, 0.5, 1.0);
        SkillFx.shake(sp, 0.2, 0.25);
        for (LivingEntity e : list) {
            elemHit(sp, life, e, P, k.num("elemMult"), x -> {
                RuneKit.effect(x, "slowness", (int) k.num("slowTicks"), 1);
                RuneKit.knock(x, V.UP, 0, 0.35);
            });
        }
    }

    private static void burst(ServerPlayer sp, double P, Obj k, String life, Vec3 z, Vec3 center) {
        if (!RuneKit.sameLife(sp, life)) return;
        ServerLevel level = sp.level();
        double r = k.num("radius");
        pulse(sp, center);
        // 정령왕의 네 구슬 (어깨 위) 에서 빛줄기가 가운데로 모임 → 무지갯빛 폭발
        double[][] cols = {FIRE, WATER, WIND, EARTH};
        Vec3 side = V.rightOf(V.flat(V.sub(center, sp.position())).lengthSqr() > 0.01 ? V.unit(V.flat(V.sub(center, sp.position()))) : Kit.facingOf(sp));
        for (int i = 0; i < 4; i++) {
            Vec3 orb = V.add(V.up(center, 2.2 + (i % 2) * 0.6), side, (i < 2 ? -1 : 1) * (1.6 + (i % 2) * 0.5));
            RuneKit.zap(level, orb, V.up(z, 1.0), cols[i], WHITE, 0.22, 0.35);
            SkillFx.burst(level, orb, cols[i], 0.8, 0.3);
        }
        RuneKit.later(3, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            for (int i = 0; i < 4; i++) {
                RuneKit.novaRing(level, z, r * (0.25 + 0.1 * i), 1.8 + 0.4 * i, cols[i], WHITE, Opt.of("life", 0.55 + 0.05 * i));
            }
            RuneKit.skyFlare(level, V.up(z, 1.2), r * 0.9, new double[] {1, 0.95, 0.85}, WHITE, Opt.of("life", 0.55, "grow", 0.8));
            RuneKit.godRays(sp, level, z, r * 0.6, 8, WHITE, WHITE, Opt.of("h", 16.0, "life", 0.9, "colors",
                    new double[][][] {{FIRE, WHITE}, {WATER, WHITE}, {WIND, WHITE}, {EARTH, WHITE}}));
            SkillFx.burst(level, V.up(z, 1.0), WHITE, r * 0.5, 0.4);
            SkillFx.play(level, "random.explode", z, 0.8, 1.0);
            SkillFx.play(level, "firework.large_blast", z, 0.7, 1.0);
            SkillFx.play(level, "ambient.weather.thunder", z, 1.4, 0.6);
            SkillFx.shake(sp, 0.4, 0.4);
            int stun = (int) k.numOr("stun", 0);
            for (LivingEntity e : foes(sp, z, r)) {
                elemHit(sp, life, e, P, k.num("burstMult"), x -> {
                    if (stun > 0) RuneKit.stun(x, stun, WHITE, true);
                    RuneKit.knock(x, V.sub(x.position(), z), 0.5, 0.35);
                });
            }
            // 정령왕이 빛으로 흩어짐
            for (int i = 0; i < 4; i++) {
                double[][] c = RuneSpirit.spiritColor(new String[] {"salamander", "undine", "sylph", "gnome"}[i]);
                double a = i / 4.0 * Math.PI * 2;
                SkillFx.fx(level, "rpg:arrow_spark", V.add(center, new Vec3(Math.cos(a) * 1.5, Math.sin(a) * 1.5, 0)), Opt.of("dir", V.UP, "speed", 2.0, "size",
                        0.5, "grow", 1.4, "life", 0.8, "color", c[0], "alpha", 0.9));
            }
            SkillFx.play(level, "mob.allay.item_given", center, 0.8, 1.0);
        });
    }

    /** 시험용: 이 룬이 쓰는 원소의 자리 반지름 (돌파 단계가 들어간 정의) */
    static double radiusOf(Obj k) {
        return k.num("radius");
    }

    /** 시험 · 다른 곳: 정령왕 강림의 틱 (합체 → 붕괴) */
    static int burstTick(Obj k) {
        return (int) k.num("ticks") + 14 + (int) k.num("elemGap") * 3 + (int) k.num("burstDelay") + 3;
    }

    /** 정령왕 강림이 끝나는 틱 (붕괴 뒤 · 시험) */
    static long endsAt(Obj k) {
        return Tasks.now() + burstTick(k) + 2;
    }
}
