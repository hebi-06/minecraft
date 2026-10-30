package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Runes;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 중력 룬 (runes.js v2.15.0 "중력" — 자줏빛 + 어두운 핵: 끌어모아 터뜨림 · 띄우고 내리꽂기): 중력탄 · 공중 부양 · 중력장 · 중력 역전 · 블랙홀.
 * 끌기 · 띄우기는 게임 물리 (속도 · 공중 부양 효과) 라 벽을 뚫지 않음. 띄운 몹은 떨어질 때 바닐라 낙하 피해를 받지 않음 (룬 피해만)
 */
final class RuneGravity {
    private RuneGravity() {}

    private static final Obj G = Rules.get().obj("RUNE_GRAVITY");
    /** 계열 색 {자줏빛 빛, 분홍빛 흰 심지} */
    private static final double[][] COLOR = RuneKit.colorOf("gravity");
    /** 블랙홀 · 구체의 어두운 핵 색 */
    private static final double[] VOID = Kit.rgb(G.get("void"));
    private static final double[] ROCK = {0.52, 0.44, 0.5};
    private static final double[] CRACK = {0.22, 0.1, 0.3};
    private static final int PULL_EVERY = (int) G.num("pullEvery");
    private static final int LAND_WAIT = (int) G.num("landWait");
    /** 끌리거나 뜨지 않는 아주 큰 몹 (자바 이름) */
    private static final Set<String> HEAVY = new HashSet<>();
    /** 띄운 (곧 떨어질) 몹 → 바닐라 낙하 피해를 막는 마지막 틱 */
    private static final Map<UUID, Long> LIFTED = new HashMap<>();

    static {
        for (Object id : G.list("heavy")) HEAVY.add(Kit.javaType((String) id));
    }

    /** 떠 있는 적과 그 발밑 (회오리 자리) */
    private record Spot(LivingEntity e, Vec3 g) {}

    static void register() {
        RuneCast.register("gravityOrb", RuneGravity::gravityOrb);
        RuneCast.register("liftSlam", RuneGravity::liftSlam);
        RuneCast.register("gravityWell", RuneGravity::gravityWell);
        RuneCast.register("gravityReverse", RuneGravity::gravityReverse);
        RuneCast.register("blackHole", RuneGravity::blackHole);
        // 띄운 몹의 낙하 피해를 막음 (피해 전 — false 면 취소) · 오래된 기록 지우기 (20틱마다)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !noFallAfterLift(entity, source));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (Tasks.now() % 20 == 0) gravityTick();
        });
        // (기록은 몹마다 — 나간 사람의 것은 없음)
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LIFTED.clear());
    }

    // ------------------------------------------------------------ 끌기 · 띄우기 · 내리꽂기

    /** 끌거나 띄울 수 있는 몹: 보스 · 보스 소품 · 아주 큰 몹 · 플레이어는 아님 (피해 · 효과만) */
    static boolean gravityMovable(Entity e) {
        if (e == null || e.isRemoved() || e instanceof Player || HEAVY.contains(Kit.typeId(e))) return false;
        return !(Kit.hasFamily(e, "rpg_boss") || Kit.hasFamily(e, "rpg_boss_prop"));
    }

    /** 훈련용 허수아비 (스크립트가 맞을 때마다 제자리로 옮기므로 끌지 않음 — 띄우기만) */
    private static boolean isDummy(Entity e) {
        return Kit.hasFamily(e, "rpg_dummy");
    }

    /** 끌어당김 한 번 (끌 수 있는 몹만, 허수아비 제외): c 쪽 가로로, 거리 × pullK (pullMax 까지). 가운데 pullStop 칸 안은 멈춤 (그 몹의 속도를 지움) */
    private static void pullToward(Entity e, Vec3 c) {
        if (!gravityMovable(e) || isDummy(e)) return;
        double dx = c.x - e.getX();
        double dz = c.z - e.getZ();
        double dist = Math.hypot(dx, dz);
        double s = dist < G.num("pullStop") ? 0 : Math.min(G.num("pullMax"), dist * G.num("pullK"));
        // 속도를 지우고 가로 힘 (clearVelocity + applyImpulse)
        if (s > 0) Kit.launch(e, dx / dist * s, 0, dz / dist * s);
        else Kit.launch(e, 0, 0, 0);
    }

    /** 띄움: 공중 부양 ticks 틱 (amp) + hold 틱 동안 공격 못 함 (기절과 같은 효과) · 떨어질 때 낙하 피해 없음. 띄웠으면 true */
    private static boolean liftUp(LivingEntity e, int ticks, int amp, int hold) {
        if (!gravityMovable(e)) return false;
        RuneKit.effect(e, "levitation", ticks, amp);
        RuneKit.effect(e, "slowness", hold, 10);
        RuneKit.effect(e, "weakness", hold, 4);
        LIFTED.merge(e.getUUID(), Tasks.now() + hold + 60, Math::max);
        return true;
    }

    /** 내리꽂음: 공중 부양을 지우고 아래로 slamPower → 땅에 닿으면 (landWait 틱까지 기다림) fn. 끌 수 없는 몹 (보스) 은 바로 fn */
    private static void slamDown(ServerPlayer sp, LivingEntity e, String life, Consumer<LivingEntity> fn) {
        if (gravityMovable(e)) {
            e.removeEffect(MobEffects.LEVITATION);
            Kit.launch(e, 0, -G.num("slamPower"), 0);
            LIFTED.merge(e.getUUID(), Tasks.now() + 60, Math::max);
        }
        slamWait(sp, e, life, fn, 0);
    }

    /** 땅에 닿기를 기다림 (n = 기다린 틱) */
    private static void slamWait(ServerPlayer sp, LivingEntity e, String life, Consumer<LivingEntity> fn, int n) {
        if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
        if (n > 0 && (n >= LAND_WAIT || e.onGround())) {
            fn.accept(e);
            return;
        }
        if (n == 0 && !gravityMovable(e)) {
            fn.accept(e);
            return;
        }
        RuneKit.later(1, () -> slamWait(sp, e, life, fn, n + 1));
    }

    /** 피해 전: 중력 룬으로 띄운 몹의 낙하 피해를 막음 (내리꽂기 피해는 룬이 따로 넣음). 막으면 true */
    private static boolean noFallAfterLift(LivingEntity e, DamageSource source) {
        if (LIFTED.isEmpty() || !source.is(DamageTypeTags.IS_FALL)) return false;
        Long until = LIFTED.get(e.getUUID());
        return until != null && Tasks.now() <= until;
    }

    /** 오래된 띄움 기록 지우기 */
    private static void gravityTick() {
        long now = Tasks.now();
        LIFTED.values().removeIf(t -> now > t);
    }

    // ------------------------------------------------------------ 자리 · 그림

    /** 눈에서 가까울수록 작게 (1인칭 화면을 덮지 않게): 눈에서 at 까지 1칸 → 0.3배, 6칸 이상 → 1배 */
    private static double nearScale(ServerPlayer sp, Vec3 at) {
        if (sp.isRemoved()) return 1;
        return Math.max(0.3, Math.min(1, (V.len(V.sub(at, sp.getEyePosition())) - 1) / 5));
    }

    /** 수평으로 min 칸보다 가까운 조준점은 보는 방향으로 min 칸까지 밀어냄 (가까이에 쓴 중력장 · 블랙홀이 화면을 덮지 않게) */
    private static Vec3 aimAtLeast(ServerPlayer sp, Vec3 g, double min) {
        Vec3 pl = sp.position();
        if (Math.hypot(g.x - pl.x, g.z - pl.z) >= min) return g;
        Vec3 p = V.add(pl, V.flat(sp.getViewVector(1F)), min);
        return new Vec3(p.x, RuneKit.groundAt(sp.level(), V.up(p, 0.5), pl.y), p.z);
    }

    /** 가운데 c 로 빨려드는 빛 알갱이 n 개 (반지름 R 에서 life 초 동안) */
    private static void inwardSparks(ServerLevel level, Vec3 c, double R, int n, double life, double[] color, double hSpread) {
        for (int i = 0; i < n; i++) {
            double a = Kit.rand() * Math.PI * 2;
            Vec3 from = new Vec3(c.x + Math.cos(a) * R, c.y + (Kit.rand() - 0.4) * hSpread, c.z + Math.sin(a) * R);
            Vec3 d = V.sub(c, from);
            SkillFx.fx(level, "rpg:arrow_spark", from, Opt.of("dir", V.unit(d), "speed", V.len(d) / life, "size", 0.15, "grow", 0.3, "life", life, "color", color,
                    "alpha", 1.0));
        }
    }

    /** 끌려 오는 돌 조각: 반지름 R 의 한 점에서 c 쪽으로 조금 돌며 (life 초) */
    private static void inwardRock(ServerLevel level, Vec3 c, double R, double life, double low) {
        double a = Kit.rand() * Math.PI * 2;
        Vec3 from = new Vec3(c.x + Math.cos(a) * R, c.y - low + Kit.rand() * 0.6, c.z + Math.sin(a) * R);
        Vec3 d = V.sub(c, from);
        Vec3 dir = V.unit(V.add(d, new Vec3(-Math.sin(a), 0, Math.cos(a)), R * 0.22));
        SkillFx.fx(level, "rpg:boulder", from, Opt.of("dir", dir, "speed", V.len(d) / life, "size", 0.18 + Kit.rand() * 0.12, "life", life, "spin",
                Kit.rand() * 360, "color", ROCK, "alpha", 1.0));
    }

    /** 땅에서 천천히 솟는 돌 조각 n 개 (feet 둘레 spread 칸) */
    private static void risingRocks(ServerLevel level, Vec3 feet, int n, double spread, double speed, double life) {
        for (int i = 0; i < n; i++) {
            double a = Kit.rand() * Math.PI * 2;
            double rr = Kit.rand() * spread;
            SkillFx.fx(level, "rpg:boulder", new Vec3(feet.x + Math.cos(a) * rr, feet.y + 0.2, feet.z + Math.sin(a) * rr), Opt.of("dir", V.UP, "speed", speed,
                    "size", 0.18 + Kit.rand() * 0.14, "life", life, "spin", Kit.rand() * 360, "color", ROCK, "alpha", 1.0));
        }
    }

    /** 띄운 적: 발밑 고리 + 솟는 빛기둥 + 함께 솟는 돌 */
    private static void liftFx(ServerLevel level, LivingEntity e) {
        double[] glow = COLOR[0];
        Vec3 feet = e.position();
        SkillFx.rune(level, feet, Opt.of("size", 0.9, "life", 0.8, "color", glow, "alpha", 0.9, "rate", 160.0, "grow", 0.3));
        SkillFx.fx(level, "rpg:arrow_shot", V.up(feet, 0.6), Opt.of("dir", V.UP, "speed", 5.0, "size", 1.0, "thick", 0.35, "life", 0.45, "color", glow,
                "alpha", 0.85));
        risingRocks(level, feet, 2, 0.8, 2.6, 0.9);
    }

    /** 떠 있는 적 둘레의 자줏빛 (4틱마다 부름) */
    private static void floatAura(ServerLevel level, LivingEntity e) {
        if (e.isRemoved()) return;
        SkillFx.fx(level, "rpg:arrow_spark", Kit.bodyPoint(e), Opt.of("dir", V.UP, "speed", 0.6, "size", 0.7, "grow", 1.2, "life", 0.25, "color", COLOR[0],
                "alpha", 0.5));
    }

    /** 위에서 내리꽂는 빛줄기 (at = 몸 가운데, h 칸 위에서) */
    private static void slamStreak(ServerLevel level, Vec3 at, double h) {
        Vec3 top = V.up(at, h);
        SkillFx.fx(level, "rpg:arrow_shot", top, Opt.of("dir", V.DOWN, "speed", h / 0.12, "size", 1.1, "thick", 0.55, "life", 0.12, "color", COLOR[0],
                "alpha", 1.0));
        SkillFx.fx(level, "rpg:arrow_shot", top, Opt.of("dir", V.DOWN, "speed", h / 0.12, "size", 0.8, "thick", 0.2, "life", 0.12, "color", COLOR[1],
                "alpha", 1.0));
    }

    /** 작은 블랙홀 (rpg:rfx_hole — 도는 빛 원반 + 어두운 핵): size · life 초. o: spin (이어 돌기) · spinRate · alpha · grow (음수면 꺼짐) · fade · noCore */
    private static void miniHole(ServerLevel level, Vec3 at, double size, double life, Opt o) {
        RuneKit.rfx(level, "hole", at, Opt.of("size", size, "life", life, "cell", 0.0, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360,
                "spinRate", o.num("spinRate", 300), "alpha", o.num("alpha", 1), "grow", o.num("grow", 0), "fade", o.num("fade", 0)));
        if (!o.truthy("noCore")) {
            RuneKit.rfx(level, "hole", at, Opt.of("size", size, "life", life, "cell", (double) RuneKit.HOLE_CORE, "grow", o.num("grow", 0), "fade",
                    o.num("fade", 0), "alpha", o.num("alpha", 1)));
        }
    }

    /**
     * 내리꽂힌 자리: 충격 고리 · 땅의 금 · 먼지 · 튀는 돌 (size = 반지름) — v2.25.0 n = 같은 틱에 내리꽂힌 몇 번째 적 (파티클 예산):
     * 0~1 번째는 둘레에 자줏빛 바위 가시 셋 (rpg:rfx_rock), 5~11 번째는 번쩍임만, 12 번째부터는 그리지 않음
     */
    private static void slamFx(ServerLevel level, Vec3 feet, double size, ServerPlayer sp, int n) {
        double[] glow = COLOR[0];
        double[] core = COLOR[1];
        if (n >= 5) {
            if (n < 12) SkillFx.burst(level, V.up(feet, 0.4), core, size * 0.6, 0.25);
            return;
        }
        RuneKit.zonePulse(level, feet, glow, size, 0.4);
        SkillFx.fx(level, "rpg:ground_crack", V.up(feet, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", size * 1.3, "life", 1.0, "color", CRACK,
                "alpha", 0.9));
        SkillFx.fx(level, "rpg:dust", V.up(feet, size * 0.3), Opt.of("size", size * 0.9, "life", 0.7, "color", new double[] {0.42, 0.32, 0.46}, "alpha", 0.75));
        SkillFx.burst(level, V.up(feet, 0.4), core, size * 0.6, 0.25);
        if (n < 2 && sp != null && !sp.isRemoved()) RuneKit.rockRing(sp, level, feet, size * 0.7, 3, 0.45, glow, Opt.of("rock", ROCK, "life", 0.8, "minDist", 1.6));
        for (int i = 0; i < 2; i++) {
            double a = Kit.rand() * Math.PI * 2;
            SkillFx.fx(level, "rpg:boulder", V.up(feet, 0.3), Opt.of("dir", V.unit(new Vec3(Math.cos(a) * 0.6, 1, Math.sin(a) * 0.6)), "speed", 5.0, "size", 0.22,
                    "life", 0.6, "spin", Kit.rand() * 360, "color", ROCK, "alpha", 1.0));
        }
    }

    /** 안으로 꺼졌다 터지는 붕괴 (핵 at, 땅 g, 반지름 R, 크기 big 0~1, sc = 눈에서 가까우면 작게) */
    private static void collapseFx(ServerLevel level, Vec3 at, Vec3 g, double R, double big, double sc) {
        double[] glow = COLOR[0];
        double[] core = COLOR[1];
        SkillFx.fx(level, "rpg:arrow_spark", at, Opt.of("dir", V.UP, "speed", 0.0, "size", (2.2 + big * 2) * sc, "grow", 0.08, "life", 0.16, "color",
                RuneKit.WHITE, "alpha", 1.0)); // 안으로 꺼지는 번쩍임
        SkillFx.burst(level, at, glow, R * 0.8 * sc, 0.4);
        SkillFx.burst(level, at, core, R * 0.4 * sc, 0.3);
        SkillFx.fx(level, "rpg:dust", V.up(g, R * 0.3 * sc), Opt.of("size", R * 0.75 * sc, "life", 0.8, "color", VOID, "alpha", 0.8 * sc));
        int rings = big > 0.5 ? 4 : 3;
        for (int i = 0; i < rings; i++) {
            final int ii = i;
            RuneKit.later(i * 2, () -> RuneKit.zonePulse(level, g, ii % 2 == 1 ? core : glow, R * (0.55 + 0.45 * (ii + 1) / rings), 0.45 + ii * 0.05));
        }
        SkillFx.fx(level, "rpg:ground_crack", V.up(g, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", R * 1.2, "life", 1.2, "color", CRACK,
                "alpha", 0.9));
        int n = big > 0.5 ? 8 : 4;
        for (int i = 0; i < n; i++) {
            double a = (double) i / n * Math.PI * 2 + Kit.rand() * 0.4;
            SkillFx.fx(level, "rpg:boulder", V.up(g, 0.6), Opt.of("dir", V.unit(new Vec3(Math.cos(a), 0.9, Math.sin(a))), "speed", 6 + big * 4, "size", 0.26,
                    "life", 0.7, "spin", Kit.rand() * 360, "color", ROCK, "alpha", 1.0));
        }
        if (big > 0.5) {
            for (int i = 0; i < 10; i++) {
                Vec3 dd = RuneKit.turn(new Vec3(1, 0, 0), i * 36 + Kit.rand() * 12);
                SkillFx.fx(level, "rpg:light_cut", V.add(V.up(g, 0.08), dd, R * 0.35), Opt.of("dir", dd, "size", R * 0.3, "thick", 0.35, "life", 1.1, "delay", 0.6,
                        "color", glow, "alpha", 0.9));
            }
        }
        SkillFx.sparks(level, at, core, 10 + (int) Math.round(big * 8), 6 + big * 2);
    }

    // ------------------------------------------------------------ 룬

    /**
     * 중력탄: 조준한 몹 (없으면 조준한 곳) 으로 어두운 구체 → 도착하면 pull 칸 안을 pullTicks 틱 끌어당긴 뒤 터짐 (radius 칸, 위력 × mult) —
     * v2.25.0 날아가는 작은 블랙홀 (rpg:rfx_hole) → 도착한 자리에서 커지며 빨아들이다가 안으로 꺼지며 터짐
     */
    static boolean gravityOrb(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        double[] glow = COLOR[0];
        double[] core = COLOR[1];
        double range = k.num("range");
        LivingEntity target = Kit.pickTarget(sp, range, 20);
        Vec3 hand = RuneKit.handOf(sp).hand();
        Vec3 to0 = target != null ? Kit.bodyPoint(target) : V.up(RuneKit.groundAim(sp, range), 1.0);
        double speed = k.num("speed");
        int ticks = Math.max(1, (int) Math.round(V.len(V.sub(to0, hand)) / speed * 20));
        String life = RuneKit.lifeToken(sp);
        SkillFx.fx(level, "rpg:magic_circle_flash", hand, Opt.of("size", 0.16, "life", 0.22, "spin", Kit.rand() * 360, "color", glow, "alpha", 1.0)); // 손 앞 작은 마법진 (1인칭 화면을 가리지 않게)
        RuneKit.flyStreak(level, hand, to0, speed, glow, core, 0.45, 0.16);
        // 날아가는 작은 블랙홀 (손 바로 앞에는 그리지 않음)
        for (int t = 2; t <= ticks; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> miniHole(level, V.add(hand, V.sub(to0, hand), Math.min(1, (double) tt / ticks)), 0.32, 0.14, Opt.of("spin", tt * 15.0)));
        }
        SkillFx.play(level, "mob.shulker.shoot", hand, 1.2, 0.6);
        double pull = k.num("pull");
        int pullTicks = (int) k.num("pullTicks");
        double radius = k.num("radius");
        RuneKit.later(ticks, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 c = target != null && !target.isRemoved() && V.len(V.sub(Kit.bodyPoint(target), to0)) < 3 ? Kit.bodyPoint(target) : to0;
            Vec3 floor = new Vec3(c.x, RuneKit.groundAt(level, c, c.y - 1), c.z);
            double sc = nearScale(sp, c);
            SkillFx.rune(level, floor, Opt.of("size", pull, "life", pullTicks / 20.0 + 0.05, "color", glow, "alpha", 0.85, "rate", -260.0, "grow", -0.8)); // 줄어드는 고리
            inwardSparks(level, c, pull, 8, pullTicks / 20.0, core, 1.2);
            // 커지는 블랙홀 (2틱마다 다시 그림: 0.4 → 0.9)
            for (int t = 0; t < pullTicks; t += 2) {
                final int tt = t;
                RuneKit.later(t, () -> {
                    if (RuneKit.sameLife(sp, life)) miniHole(level, c, (0.4 + 0.5 * (tt + 2) / pullTicks) * sc, 0.14, Opt.of("spin", tt * 20.0, "spinRate", 400.0));
                });
            }
            SkillFx.play(level, "mob.endermen.portal", c, 1.1, 0.9);
            for (int t = 0; t < pullTicks; t += PULL_EVERY) {
                RuneKit.later(t, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    for (LivingEntity e : Kit.around(sp, c, pull, 2.5)) pullToward(e, c);
                });
            }
            RuneKit.later(pullTicks, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                miniHole(level, c, 1.0 * sc, 0.25, Opt.of("grow", -0.9, "spinRate", 900.0, "noCore", true)); // 안으로 꺼짐
                collapseFx(level, c, floor, radius, 0, sc);
                SkillFx.play(level, "random.explode", c, 0.7, 1.5);
                SkillFx.play(level, "mob.shulker.bullet.hit", c, 1.2, 0.7);
                for (LivingEntity e : Kit.around(sp, c, radius, 2.5)) {
                    if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) RuneKit.knock(e, V.UP, 0, 0.25);
                }
            });
        });
        return true;
    }

    /**
     * 공중 부양: 앞 cone 도 · range 칸의 적에게 위력 × mult + 띄움 (lift 틱, 그 동안 공격 못 함) → gap 틱 뒤 내리꽂음 (땅에 닿을 때 위력 × mult) —
     * v2.25.0 띄운 적의 발밑에 자줏빛 회오리 (rpg:rfx_vortex) 가 돌고 돌이 함께 떠오름, 내리꽂힌 자리에 바위 가시
     */
    static boolean liftSlam(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        double[] glow = COLOR[0];
        double[] core = COLOR[1];
        Vec3 f = V.flat(sp.getViewVector(1F));
        String life = RuneKit.lifeToken(sp);
        double cone = k.num("cone");
        double mult = k.num("mult");
        int gap = (int) k.num("gap");
        // 앞 땅을 따라 번지는 자줏빛 물결 (1인칭 화면 아래쪽)
        for (int i = 0; i < 5; i++) {
            Vec3 d = RuneKit.turn(f, -cone / 2 + cone * i / 4);
            SkillFx.fx(level, "rpg:light_cut", V.up(V.add(sp.position(), d, 2.6 + (i % 2) * 0.8), 0.1), Opt.of("dir", V.rightOf(d), "size", 1.1, "thick", 0.22,
                    "life", 0.5, "delay", Math.abs(i - 2) * 0.03, "color", i % 2 == 1 ? core : glow, "alpha", 0.9));
        }
        SkillFx.play(level, "mob.breeze.charge", sp.position(), 1.2, 0.8);
        SkillFx.play(level, "mob.evocation_illager.cast_spell", sp.position(), 0.9, 1.2);
        List<LivingEntity> hitList = new ArrayList<>();
        List<LivingEntity> floating = new ArrayList<>();
        for (LivingEntity e : RuneKit.coneTargets(sp, k.num("range"), cone, 10)) {
            RuneKit.runeHit(sp, e, P, mult, r.stat());
            if (!Kit.canHit(sp, e)) continue; // 쓰러진 적은 빼고 (첫 피해가 무적 시간에 막혀도 띄우고 내리꽂음)
            hitList.add(e);
            if (liftUp(e, (int) k.num("lift"), (int) k.num("liftAmp"), gap + LAND_WAIT)) {
                floating.add(e);
                if (floating.size() <= 8) liftFx(level, e); // 8명까지 (파티클 예산)
            }
        }
        // 떠 있는 동안: 발밑 회오리 (땅에 붙음) + 둘레의 자줏빛 (4틱마다 — 앞 원뿔의 10명까지라 파티클은 4틱에 20개 안)
        List<Spot> spots = new ArrayList<>();
        for (LivingEntity e : floating) spots.add(new Spot(e, e.position()));
        for (int t = 0; t < gap; t += 4) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (Spot s : spots) {
                    if (s.e().isRemoved()) continue;
                    RuneKit.vortexAt(level, s.g(), 0.75 + Kit.bodyPad(s.e()) * 0.5, glow, Opt.of("life", 0.24, "alpha", 0.75, "fps", 24.0, "fade",
                            tt + 4 >= gap ? 0.5 : 0.0));
                    if (tt > 0) floatAura(level, s.e());
                }
            });
        }
        RuneKit.later(gap, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            List<LivingEntity> alive = new ArrayList<>();
            for (LivingEntity e : hitList) if (!e.isRemoved()) alive.add(e);
            if (!alive.isEmpty()) {
                SkillFx.play(level, "mace.smash_air", sp.position(), 1.0, 0.9);
                RuneKit.shakeAround(level, sp.position(), 8, 0.2, 0.25);
            }
            Map<Long, Integer> landed = new HashMap<>(); // 틱 → 그 틱에 내리꽂힌 수 (파티클 예산)
            for (LivingEntity e : alive) {
                slamStreak(level, Kit.bodyPoint(e), 3.5);
                slamDown(sp, e, life, x -> {
                    long now = Tasks.now();
                    int n = landed.getOrDefault(now, 0);
                    landed.put(now, n + 1);
                    slamFx(level, x.position(), 1.4, sp, n);
                    SkillFx.play(level, "mace.heavy_smash_ground", x.position(), 0.9, 0.9);
                    RuneKit.runeHit(sp, x, P, mult, r.stat());
                });
            }
        });
        return true;
    }

    /**
     * 중력장: 조준한 곳 (range 칸) 에 hits × gap 틱 — pull 칸 안을 끌어모으며 gap 틱마다 radius 칸 위력 × mult + 구속 → 붕괴 (radius 칸 위력 × crush) —
     * v2.25.0 가운데 블랙홀 (rpg:rfx_hole) 이 자라며 돌고, 끝에 안으로 꺼지며 붕괴
     */
    static boolean gravityWell(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        double[] glow = COLOR[0];
        double[] core = COLOR[1];
        Vec3 g = aimAtLeast(sp, RuneKit.groundAim(sp, k.num("range")), k.num("minDist"));
        Vec3 hole = V.up(g, 1.3);
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int T = hits * gap;
        String life = RuneKit.lifeToken(sp);
        double sc = nearScale(sp, hole);
        double radius = k.num("radius");
        double pull = k.num("pull");
        SkillFx.play(level, "mob.warden.sonic_charge", g, 0.9, 1.3);
        SkillFx.play(level, "portal.trigger", g, 0.6, 1.4);
        for (int t = 0; t < T; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                if (tt % 6 == 0) SkillFx.fx(level, "rpg:whirlpool", V.up(g, 0.1), Opt.of("dir", V.ZERO, "size", radius, "life", 0.45, "spin", tt * 12.0,
                        "speed", -260.0, "color", glow, "alpha", 0.7));
                if (tt % 4 == 0) miniHole(level, hole, (0.7 + (double) tt / T * 0.6) * sc, 0.24, Opt.of("spin", (double) ((tt * 15) % 360), "spinRate", 300.0));
                inwardSparks(level, hole, radius, 2, 0.5, tt % 4 != 0 ? core : glow, 1.6);
                if (tt % 10 == 0) SkillFx.rune(level, g, Opt.of("size", radius, "life", 0.5, "color", glow, "alpha", 0.8, "rate", -200.0, "grow", -0.85));
                if (tt % 8 == 4) inwardRock(level, hole, radius, 0.55, 1.0);
            });
        }
        for (int t = 0; t < T; t += PULL_EVERY) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (LivingEntity e : Kit.around(sp, g, pull, 3)) pullToward(e, g);
            });
        }
        SkillFx.every(hits, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            if (i % 2 == 0) SkillFx.play(level, "mob.endermen.portal", g, 0.8, 0.6 + i * 0.1);
            for (LivingEntity e : Kit.around(sp, g, radius, 3)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) RuneKit.effect(e, "slowness", gap + 4, (int) k.num("slowAmp"));
            }
        });
        RuneKit.later(T, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            miniHole(level, hole, 1.5 * sc, 0.3, Opt.of("grow", -0.9, "spinRate", 900.0, "noCore", true)); // 안으로 꺼짐
            collapseFx(level, hole, g, radius, 0.4, sc);
            SkillFx.play(level, "random.explode", g, 0.8, 1.2);
            SkillFx.play(level, "mob.warden.sonic_boom", g, 0.6, 1.3);
            RuneKit.shakeAround(level, g, 12, 0.3, 0.3);
            for (LivingEntity e : Kit.around(sp, g, radius, 3)) {
                if (RuneKit.runeHit(sp, e, P, k.num("crush"), r.stat()) > 0) RuneKit.knock(e, V.UP, 0, 0.35);
            }
        });
        return true;
    }

    /**
     * 중력 역전: 내 주위 radius 칸의 적에게 위력 × mult + 모두 띄움 (lift 틱, 공격 못 함) → gap 틱 뒤 한꺼번에 내리꽂음 (땅에 닿을 때 위력 × crush + 구속) —
     * v2.25.0 앞 반원에 자줏빛 바위 가시가 솟고 돌이 떠오름, 머리 위 4.6칸에 거꾸로 된 마법진 (돌아가는 소용돌이) + 앞 위 하늘의 중력 문장 (1인칭) →
     * 하늘에서 적마다 빛줄기가 내리꽂힘
     */
    static boolean gravityReverse(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        double[] glow = COLOR[0];
        double[] core = COLOR[1];
        Vec3 c = sp.position();
        Vec3 f = V.flat(sp.getViewVector(1F));
        String life = RuneKit.lifeToken(sp);
        double sky = 4.6;
        int gap = (int) k.num("gap");
        double radius = k.num("radius");
        // 1인칭에서도 보이는 중력 문장: 앞 3.2칸 · 위 3.3칸 (화면을 보는 그림 — 하늘의 마법진은 1인칭에서 보이지 않으므로)
        Vec3 sigil = V.up(V.add(c, f, 3.2), 3.3);
        RuneKit.zonePulse(level, c, glow, radius, 0.5);
        SkillFx.rune(level, c, Opt.of("size", radius, "life", (gap + 4) / 20.0, "color", glow, "alpha", 0.85, "rate", 120.0, "grow", 0.0));
        // 앞 반원에서 솟는 바위 가시 · 떠오르는 돌 (1인칭 화면 — 발밑 룬은 화면 밖)
        for (int i = 0; i < 7; i++) {
            Vec3 d = RuneKit.turn(f, (i / 6.0 - 0.5) * 160);
            Vec3 p = V.add(c, d, 2.3 + (i % 3) * 1.0);
            RuneKit.rockSpike(sp, level, p, 0.5 + (i % 3) * 0.1, glow, Opt.of("rock", ROCK, "life", gap / 20.0, "fade", 0.25));
            if (i % 2 == 0) SkillFx.fx(level, "rpg:boulder", V.up(p, 0.2), Opt.of("dir", V.UP, "speed", 2.4, "size", 0.24, "life", gap / 20.0, "spin",
                    Kit.rand() * 360, "color", ROCK, "alpha", 1.0));
        }
        // 하늘의 거꾸로 된 마법진 (gap 동안 4틱마다 — 내리꽂을 때 번쩍)
        for (int t = 0; t < gap; t += 4) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                SkillFx.rune(level, V.up(c, sky), Opt.of("size", radius * 0.75, "life", 0.24, "color", glow, "alpha", 0.85, "progress",
                        Math.min(1, (tt + 4) / (gap * 0.6)), "rate", 0.0, "spin", -tt * 6.0));
                RuneKit.rfx2(level, "sun", sigil, Opt.of("size", (0.8 + 0.9 * (tt + 4) / gap) * RuneKit.nearK(sp, sigil, 3.5, 0.5), "life", 0.24, "spin", -tt * 9.0,
                        "alpha", 0.85), glow, core);
                if (tt % 8 == 0) SkillFx.fx(level, "rpg:whirlpool", V.up(c, sky - 0.1), Opt.of("dir", V.ZERO, "size", radius * 0.6, "life", 0.42, "spin", tt * 15.0,
                        "speed", 300.0, "color", core, "alpha", 0.55));
            });
        }
        SkillFx.play(level, "mob.warden.sonic_charge", c, 1.0, 1.1);
        SkillFx.play(level, "mob.breeze.charge", c, 1.2, 0.7);
        List<LivingEntity> hitList = new ArrayList<>();
        List<LivingEntity> floating = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, c, radius, 3)) {
            RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat());
            if (!Kit.canHit(sp, e)) continue; // 쓰러진 적은 빼고 (첫 피해가 무적 시간에 막혀도 띄우고 내리꽂음)
            hitList.add(e);
            if (liftUp(e, (int) k.num("lift"), (int) k.num("liftAmp"), gap + LAND_WAIT)) {
                floating.add(e);
                if (floating.size() <= 8) liftFx(level, e); // 8명까지 (파티클 예산)
            }
        }
        List<LivingEntity> aura = floating.size() > 10 ? new ArrayList<>(floating.subList(0, 10)) : floating;
        for (int t = 4; t < gap; t += 4) {
            RuneKit.later(t, () -> {
                if (RuneKit.sameLife(sp, life)) for (LivingEntity e : aura) floatAura(level, e);
            });
        }
        RuneKit.later(gap - 4, () -> {
            if (RuneKit.sameLife(sp, life)) SkillFx.play(level, "mace.smash_air", sp.position(), 1.2, 0.7);
        });
        RuneKit.later(gap, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 pc = sp.position();
            RuneKit.zonePulse(level, pc, glow, radius, 0.5);
            SkillFx.rune(level, V.up(c, sky), Opt.of("size", radius * 0.85, "life", 0.3, "color", RuneKit.WHITE, "alpha", 0.9, "grow", 0.3, "rate", 0.0, "spin", 0.0));
            RuneKit.rfx2(level, "sun", sigil, Opt.of("size", 1.9 * RuneKit.nearK(sp, sigil, 3.5, 0.5), "life", 0.3, "grow", 0.4, "fade", 0.7, "spin", -gap * 9.0),
                    RuneKit.WHITE, core);
            RuneKit.shakeAround(level, pc, 12, 0.35, 0.35);
            SkillFx.play(level, "random.explode", pc, 0.7, 0.9);
            Map<Long, Integer> landed = new HashMap<>(); // 틱 → 그 틱에 내리꽂힌 수 (파티클 예산)
            int streaks = 0;
            for (LivingEntity e : hitList) {
                if (e.isRemoved()) continue;
                if (streaks++ < 12) slamStreak(level, Kit.bodyPoint(e), 4.5); // 하늘에서 내리꽂는 빛줄기는 12명까지 (파티클 예산)
                slamDown(sp, e, life, x -> {
                    long now = Tasks.now();
                    int n = landed.getOrDefault(now, 0);
                    landed.put(now, n + 1);
                    slamFx(level, x.position(), 1.8, sp, n);
                    SkillFx.play(level, "mace.heavy_smash_ground", x.position(), 1.0, 0.8);
                    if (RuneKit.runeHit(sp, x, P, k.num("crush"), r.stat()) <= 0) return;
                    // 짓눌림: 띄운 동안의 구속 XI 이 남아 있으면 더 약한 구속 II 로 바뀌지 않으므로 지우고 검 (나약함은 남은 몇 틱 그대로)
                    x.removeEffect(MobEffects.SLOWNESS);
                    RuneKit.effect(x, "slowness", (int) k.num("slowTicks"), (int) k.num("slowAmp"));
                });
            }
        });
        return true;
    }

    /** 원반이 이어 돌게 (240도/초) */
    private static double holeSpin(int t) {
        return (t * 12) % 360;
    }

    /**
     * 블랙홀: 조준한 곳 (range 칸) 위 height 칸에 charge 틱 동안 블랙홀 (rpg:rfx_hole — 도는 빛 원반 + 어두운 핵) 이 자람 → hits × gap 틱 동안 pull 칸 안을
     * 빨아들이고 (핵 가까이 3칸 안은 띄움 — 공격 못 함), gap 틱마다 radius 칸 위력 × mult → 붕괴 (radius 칸 위력 × crush, 떠 있던 적은 내리꽂힘)
     */
    static boolean blackHole(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        double[] glow = COLOR[0];
        double[] core = COLOR[1];
        Vec3 g = aimAtLeast(sp, RuneKit.groundAim(sp, k.num("range")), k.num("minDist"));
        double height = k.num("height");
        Vec3 hole = V.up(g, height);
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int charge = (int) k.num("charge");
        int T = hits * gap;
        String life = RuneKit.lifeToken(sp);
        double sc = nearScale(sp, hole);
        double radius = k.num("radius");
        double pull = k.num("pull");
        SkillFx.play(level, "mob.warden.sonic_charge", g, 1.0, 0.8);
        SkillFx.play(level, "conduit.activate", g, 1.0, 0.6);
        // v2.27.0 2차 강화 — 예고: 공허의 하늘 (어둠) · 겨눈 땅의 거대한 비전 문양 (거꾸로 돌며 빨려 듦) · 뒤쪽 하늘의 검은 일식 (아래) · 핵이 다 열리면 빛을 삼키는 어두운 번쩍임
        RuneKit.ultSkyFor(sp, level, g, "blackHole", charge + T + 34, null);
        RuneKit.domainSigil(level, g, radius * 1.15, "arcane", glow, core, Opt.of("life", (charge + T + 1) / 20.0, "spinRate", -70.0, "grow", -0.25, "alpha", 0.75,
                "fade", 0.1));
        RuneKit.later(charge, () -> {
            if (RuneKit.sameLife(sp, life)) RuneKit.ultFlashFor(sp, level, g, "blackHole", "dark", Opt.of("time", new double[] {0.05, 0.05, 0.5}));
        });
        for (int t = 0; t < charge; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double q = (tt + 2.0) / charge;
                double s = (0.35 + q * 1.55) * sc;
                RuneBoost.aura(level, "hole", hole, Opt.of("size", s, "life", 0.14, "spin", -holeSpin(tt), "spinRate", -160.0, "alpha", 0.6 + 0.4 * q), glow, 1.4,
                        0.4); // 0.48.2 2D 강화: 거꾸로 도는 큰 소용돌이 (후광)
                RuneKit.rfx(level, "hole", hole, Opt.of("size", s, "life", 0.14, "cell", 0.0, "spin", holeSpin(tt), "spinRate", 240.0, "alpha", 0.6 + 0.4 * q));
                RuneKit.rfx(level, "hole", hole, Opt.of("size", s, "life", 0.14, "cell", (double) RuneKit.HOLE_CORE));
                inwardSparks(level, hole, 3.5, 3, 0.35, tt % 4 != 0 ? core : glow, 2);
                SkillFx.rune(level, g, Opt.of("size", radius * 0.8, "life", 0.14, "color", glow, "alpha", 0.9, "progress", q, "rate", 0.0, "spin", -tt * 4.0));
            });
        }
        for (int t = 0; t < T; t += 2) {
            final int tt = t;
            RuneKit.later(charge + t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                if (tt % 4 == 0) {
                    RuneBoost.aura(level, "hole", hole, Opt.of("size", 1.9 * sc, "life", 0.24, "spin", -holeSpin(charge + tt), "spinRate", -160.0), glow, 1.4, 0.4);
                    RuneKit.rfx(level, "hole", hole, Opt.of("size", 1.9 * sc, "life", 0.24, "cell", 0.0, "spin", holeSpin(charge + tt), "spinRate", 240.0));
                    RuneKit.rfx(level, "hole", hole, Opt.of("size", 1.9 * sc, "life", 0.24, "cell", (double) RuneKit.HOLE_CORE));
                }
                inwardSparks(level, hole, radius, 3, 0.7, tt % 4 != 0 ? core : glow, 3);
                if (tt % 6 == 0) SkillFx.fx(level, "rpg:whirlpool", V.up(g, 0.1), Opt.of("dir", V.ZERO, "size", radius, "life", 0.5, "spin", tt * 10.0,
                        "speed", -300.0, "color", glow, "alpha", 0.6));
                if (tt % 8 == 0) SkillFx.fx(level, "rpg:dust", hole, Opt.of("size", 2.6 * sc, "life", 0.6, "color", VOID, "alpha", 0.6 * sc));
                if (tt % 4 == 2) inwardRock(level, hole, radius - 1, 0.7, height);
                if (tt % 20 == 0) SkillFx.play(level, "mob.endermen.portal", hole, 1.2, 0.5);
            });
        }
        // 빨아들임 (핵 가까이는 핵 높이 쪽으로 띄움)
        for (int t = 0; t < T; t += PULL_EVERY) {
            RuneKit.later(charge + t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (LivingEntity e : Kit.around(sp, g, pull, 5)) {
                    pullToward(e, g);
                    if (Math.hypot(e.getX() - g.x, e.getZ() - g.z) < 3 && e.getY() < hole.y - 1.0) liftUp(e, PULL_EVERY + 3, 2, PULL_EVERY + 14); // 몸 가운데가 핵 높이쯤까지
                }
            });
        }
        for (int i = 0; i < hits; i++) {
            RuneKit.later(charge + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (LivingEntity e : Kit.around(sp, g, radius, 5)) RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat());
            });
        }
        // v2.27.0 뒤쪽 하늘의 검은 일식 (블랙홀 그림을 크게 — 원반 + 핵): 붕괴 때 섬광으로 터짐
        Vec3 eclipse = RuneKit.skyPoint(sp, g, 8, 21, 6, 22);
        RuneKit.rfx(level, "hole", eclipse, Opt.of("size", 3.6, "life", (charge + T + 2) / 20.0, "cell", 0.0, "spinRate", 50.0, "alpha", 0.85, "fade", 0.1));
        RuneKit.rfx(level, "hole", eclipse, Opt.of("size", 3.6, "life", (charge + T + 2) / 20.0, "cell", (double) RuneKit.HOLE_CORE, "alpha", 0.95, "fade", 0.1));
        RuneKit.later(charge + T, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            // 붕괴: 원반이 안으로 꺼지며 (커졌다 사라짐) 터짐
            RuneKit.rfx(level, "hole", hole, Opt.of("size", 2.2 * sc, "life", 0.3, "cell", 0.0, "spin", holeSpin(charge + T), "spinRate", 900.0, "grow", -0.9));
            collapseFx(level, hole, g, radius, 1, sc);
            RuneBoost.impact(level, g, glow, core, radius); // 0.48.2 2D 강화: 붕괴의 터짐
            SkillFx.play(level, "mob.warden.sonic_boom", g, 1.0, 0.8);
            SkillFx.play(level, "random.explode", g, 0.8, 0.9);
            RuneKit.shakeAround(level, g, 24, 0.5, 0.5);
            // v2.27.0 발동 (붕괴): 흰 보랏빛 번쩍임 · 퍼지는 고리 · 하늘의 일식이 터지는 섬광 (1틱 뒤) · 공허의 금 (2틱 뒤)
            RuneKit.ultFlashFor(sp, level, g, "blackHole", "flash", Opt.empty());
            RuneKit.later(1, () -> {
                RuneKit.novaRing(level, g, 2.0, 4.5, glow, core, Opt.of("life", 0.55));
                RuneKit.skyFlare(level, eclipse, 4.2, glow, core, Opt.of("life", 0.6));
            });
            RuneKit.later(2, () -> RuneKit.glowCrack(level, g, radius * 1.1, glow, core, Opt.of("life", 2.2, "v", 1.0)));
            int stun = (int) k.numOr("stun", 0); // 8단계 6돌: 붕괴에 맞은 적 기절
            int slowTicks = (int) k.numOr("slowTicks", 0); // 8단계 3돌: 붕괴 뒤 짓눌림 (구속)
            for (LivingEntity e : Kit.around(sp, g, radius, 5)) {
                boolean landed = RuneKit.runeHit(sp, e, P, k.num("crush"), r.stat()) > 0;
                if (landed && slowTicks > 0) RuneKit.effect(e, "slowness", Math.max(slowTicks, stun), (int) k.numOr("slowAmp", 0));
                if (landed && stun > 0) RuneKit.stun(e, stun, glow, RuneKit.fxQuota("holeStun", 10));
                if (Kit.canHit(sp, e) && gravityMovable(e) && !e.onGround()) {
                    slamStreak(level, Kit.bodyPoint(e), 3);
                    slamDown(sp, e, life, x -> {});
                }
            }
        });
        return true;
    }
}
