package mmorpg.fabric.rune;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 체력 룬 (runes.js "체력" — 분홍: 지키고 버팀): 수호의 외침 · 생명의 샘 · 강철 피부 · 불굴의 방벽 · 불사의 맹약.
 * 불사의 맹약 동안 체력이 1 아래로 내려가지 않음 (RpgHealth.undying — 죽거나 나가면 끝).
 */
final class RuneVit {
    private RuneVit() {}

    /** 불사의 맹약: 플레이어 → 끝나는 틱 */
    private static final Map<UUID, Long> UNDYING = new HashMap<>();
    /** 불사 발동 연출을 너무 자주 하지 않게 (플레이어 → 연출한 틱) */
    private static final Map<UUID, Long> UNDYING_FX_AT = new HashMap<>();

    static void register() {
        RuneCast.register("guardShout", RuneVit::guardShout);
        RuneCast.register("lifeSpring", RuneVit::lifeSpring);
        RuneCast.register("ironSkin", RuneVit::ironSkin);
        RuneCast.register("bulwark", RuneVit::bulwark);
        RuneCast.register("undyingPact", RuneVit::undyingPact);
        // 불사의 맹약 (hp.js setUndying): 쓰러지지 않는 상태인지 · 그것으로 버텼을 때 연출
        RpgHealth.undying = RuneVit::isUndying;
        RpgHealth.undyingSaved = RuneVit::onUndyingSaved;
        // 진짜로 죽으면 불사도 끝 (불사의 토템으로 산 것은 죽음이 아님)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer sp) UNDYING.remove(sp.getUUID());
        });
        RuneSystem.onForget(id -> {
            UNDYING.remove(id);
            UNDYING_FX_AT.remove(id);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            UNDYING.clear();
            UNDYING_FX_AT.clear();
        });
    }

    /**
     * 수호의 외침: 주위 적에게 피해 (→ 나를 노림), ticks 동안 받는 피해 -reduce — 앞 머리 위에 커다란 방패 문장이 번쩍이고,
     * 맞은 적의 머리 위에 작은 방패 표식 (도발), 버티는 동안 몸 양옆 뒤쪽에 작은 방패 둘이 따라다님
     */
    static boolean guardShout(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double[][] cs = RuneKit.colorOf("vit");
        double[] glow = cs[0];
        double[] core = cs[1];
        Vec3 eye = sp.getEyePosition();
        double radius = k.num("radius");
        int ticks = (int) k.num("ticks");
        SkillFx.ring(level, eye, sp.getViewVector(1F), glow, 1.4, 0.5, 4);
        RuneKit.zonePulse(level, c, glow, radius, 0.55);
        RuneKit.zonePulse(level, c, core, radius * 0.5, 0.4);
        SkillFx.rune(level, c, Opt.of("size", 1.6, "life", 1.0, "color", glow, "alpha", 0.85, "rate", 120.0, "grow", 0.3));
        RuneBoost.bloom(level, c, glow, core, 2.5); // 0.50.0 2D 강화 (아래 등급 터짐): 외치는 순간의 빛 (쓴 자리)
        // 2026-11-13 3D (사용자 "이재 다른 룬들도 모션하고 이펙트 추가하자"): 머리 위 앞의 탑 방패 문장 · 옆 · 뒤로 퍼지는 육각 판 · 버티는 동안 따라오는 작은 방패 둘
        // (예전 2D 큰 방패 문장 · 10틱마다 다시 그린 2D 작은 방패 대신)
        shoutShield(sp, glow, core);
        shoutPanels(sp, radius, glow, core);
        guardShields(sp, ticks, glow, core);
        SkillFx.sparks(level, V.up(c, 1), core, 8, 4);
        SkillFx.play(level, "mob.ravager.roar", eye, 1.4, 0.8);
        SkillFx.play(level, "random.anvil_land", c, 0.6, 0.4);
        Guards.set(sp, ticks, k.num("reduce"), null, null);
        int marked = 0;
        for (LivingEntity e : Kit.around(sp, c, radius)) {
            if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                // 번쩍임 16명 · 도발 표식 (머리 위 작은 방패 1초) 8명까지 (파티클 예산)
                if (marked < 16) SkillFx.burst(level, Kit.bodyPoint(e), glow, 0.6, 0.25);
                if (marked++ < 8) {
                    RuneKit.shieldEmblem(sp, level, RuneKit.overHead(e), 0.3, glow, Opt.of("life", 1.0, "fade", 0.35, "grow", 0.2, "vel", RuneKit.entVel(e),
                            "full", 3.0, "min", 0.4));
                }
            }
        }
        return true;
    }

    /**
     * 2026-11-13 수호의 외침의 3D 탑 방패 문장 (tower_shield — 앞면 (룬 십자) 이 나를 봄): 머리 위 앞 (1.4칸 앞 · 3.7 높이) 에 작게 나타나 2.95 높이로 내려오며
     * 커지고 (2틱 — 외치는 때) 4틱에 번쩍 → 14틱에 위로 작아지며 사라짐. 1인칭에서는 화면 위쪽 가장자리에 걸침
     */
    private static void shoutShield(ServerPlayer sp, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        mmorpg.fabric.vfx.Vfx.Fx fx = RuneProps.fx(level, c, 24);
        double s = RuneProps.scaleFor(1.3, RuneProps.TOWER_SHIELD_H);
        Vec3 at = V.up(V.add(c, f, 1.5), 2.95);
        Vec3 back = V.scale(f, -1);
        org.joml.Quaternionf rot = RuneProps.facing(back, V.unit(V.add(V.UP, f, 0.25)));
        mmorpg.fabric.vfx.Vfx.Prop p = RuneProps.prop(fx, RuneProps.TOWER_SHIELD, V.up(at, 0.75), RuneProps.TOWER_SHIELD_MID, RuneProps.facing(back, V.UP), s * 0.3,
                glow, core);
        fx.on(1, () -> p.to(at, rot, s * 1.18, 2));
        fx.on(3, () -> p.size(s, 3));
        fx.on(8, () -> p.size(s * 1.06, 2));
        fx.on(10, () -> p.size(s, 3));
        fx.on(14, () -> p.to(V.up(at, 0.9), null, s * 0.15, 6));
        fx.on(20, p::remove);
    }

    /** 2026-11-13 외침의 물결: 3D 육각 판 여섯이 몸 옆 · 뒤 (보는 쪽에서 80° ~ 280° — 1인칭 화면 앞은 비움) 에서 바깥으로 퍼지며 작아짐 (10틱) */
    private static void shoutPanels(ServerPlayer sp, double radius, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        mmorpg.fabric.vfx.Vfx.Fx fx = RuneProps.fx(level, c, 14);
        double s = RuneProps.scaleFor(1.0, RuneProps.HEX_PANEL_H);
        double far = Math.min(radius, 5.0) * 0.85;
        for (int j = 0; j < 6; j++) {
            Vec3 d = RuneKit.turn(f, 80 + j * 40);
            Vec3 a1 = V.up(V.add(c, d, far), 1.35);
            mmorpg.fabric.vfx.Vfx.Prop p = RuneProps.prop(fx, RuneProps.HEX_PANEL, V.up(V.add(c, d, 0.8), 1.0), RuneProps.MID, RuneProps.facing(d, V.UP), s * 0.45,
                    j % 2 == 0 ? glow : core, core);
            fx.on(1, () -> p.to(a1, null, s, 5));
            fx.on(6, () -> p.size(s * 0.15, 4));
            fx.on(10, p::remove);
        }
    }

    /**
     * 2026-11-13 버티는 동안 몸 양옆 뒤쪽 (보는 쪽에서 ±125° · 0.75칸) 의 작은 3D 탑 방패 둘 (바깥을 봄): 틱마다 몸을 따라오며 숨 쉬듯 오르내림 → ticks 틱에 작아지며
     * 사라짐 (죽거나 나가면 바로)
     */
    private static void guardShields(ServerPlayer sp, int ticks, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        mmorpg.fabric.vfx.Vfx.Fx fx = RuneProps.fx(level, sp.position(), ticks + 10);
        double s = RuneProps.scaleFor(0.85, RuneProps.TOWER_SHIELD_H);
        int[] sds = {-1, 1};
        mmorpg.fabric.vfx.Vfx.Prop[] ps = new mmorpg.fabric.vfx.Vfx.Prop[2];
        java.util.function.BiFunction<Integer, Integer, Vec3> spot = (i, t) -> V.up(V.add(sp.position(), RuneKit.turn(Kit.facingOf(sp), sds[i] * 125), 0.75),
                1.05 + 0.06 * Math.sin(t * 0.25 + i * 1.7));
        for (int i = 0; i < 2; i++) {
            Vec3 d = RuneKit.turn(Kit.facingOf(sp), sds[i] * 125);
            ps[i] = RuneProps.prop(fx, RuneProps.TOWER_SHIELD, V.up(spot.apply(i, 0), -0.4), RuneProps.TOWER_SHIELD_MID, RuneProps.facing(d, V.UP), s * 0.2, glow, core);
        }
        boolean[] done = {false};
        Runnable end = () -> {
            if (done[0]) return;
            done[0] = true;
            int a = fx.age();
            for (mmorpg.fabric.vfx.Vfx.Prop p : ps) p.size(s * 0.1, 5);
            fx.on(a + 5, () -> {
                for (mmorpg.fabric.vfx.Vfx.Prop p : ps) p.remove();
            });
        };
        fx.each(1, ticks, 1, t -> {
            if (done[0]) return;
            if (!RuneKit.sameLife(sp, life)) {
                end.run();
                return;
            }
            for (int i = 0; i < 2; i++) {
                Vec3 d = RuneKit.turn(Kit.facingOf(sp), sds[i] * 125);
                ps[i].to(spot.apply(i, t), RuneProps.facing(d, V.UP), t <= 3 ? s * (0.2 + 0.8 * t / 3.0) : -1, 1);
            }
        });
        fx.on(ticks, end);
    }

    /**
     * 생명의 샘: pulses 번 나눠 체력 회복 (최대 체력 × heal × 체력 비례), 주위 아군은 share 만큼 — 몸 앞 양옆에 빛의 샘 둘이 솟음
     * (rpg:rfx_fountain — 8장이 흐름, 몸을 따라옴. 1인칭에서는 화면 아래 양쪽에 보임)
     */
    static boolean lifeSpring(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        double[][] cs = RuneKit.colorOf("vit");
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] green = {0.5, 1.0, 0.6};
        int pulses = (int) k.num("pulses");
        int gap = (int) k.num("gap");
        double radius = k.num("radius");
        // 한 번에 회복하는 양: 그 사람의 최대 체력 × heal × 쓴 사람의 체력 비례 ÷ pulses
        ToDoubleFunction<ServerPlayer> amountOf = pl -> RpgHealth.maxOf(pl) * k.num("heal") * Runes.supportScale(RpgStats.effective(sp), r.family()) / pulses;
        ServerLevel level0 = sp.level();
        SkillFx.rune(level0, sp.position(), Opt.of("size", 2.6, "life", (pulses * gap) / 20.0 + 0.4, "color", glow, "alpha", 0.85, "rate", 40.0, "grow", 0.1));
        RuneBoost.bloom(level0, sp.position(), glow, core, 2.8); // 0.50.0 2D 강화 (아래 등급 터짐): 샘이 솟는 순간의 빛 (쓴 자리)
        SkillFx.play(level0, "beacon.power", sp.position(), 1.5, 0.8);
        SkillFx.play(level0, "bucket.empty_water", sp.position(), 1.2, 0.6);
        springColumns(sp, pulses * gap, gap);
        SkillFx.every(pulses, gap, i -> {
            if (sp.isRemoved()) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            double healed = RpgHealth.healBy(sp, amountOf.applyAsDouble(sp));
            if (healed > 0) DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
            RuneKit.selfCue(sp, green, glow, i == 0);
            boolean last = i == pulses - 1;
            // 샘 둘 (보는 쪽에서 ±42°, 1.8칸 — 1인칭 화면 아래 양쪽) 은 3D 물기둥 (springColumns) — 회복마다 물기둥 꼭대기에서 생명빛 물보라
            for (int sd : new int[] {-1, 1}) {
                Vec3 top = V.up(V.add(c, RuneKit.turn(f, sd * 42), 1.8), 1.6);
                SkillFx.sparks(level, top, sd > 0 ? green : glow, 5, 3);
                SkillFx.burst(level, top, sd > 0 ? green : core, 0.5, 0.25);
            }
            for (int j = 0; j < 6; j++) {
                double a = (j / 6.0) * Math.PI * 2 + i;
                SkillFx.fx(level, "rpg:arrow_spark", new Vec3(c.x + Math.cos(a) * 0.8, c.y + 0.2, c.z + Math.sin(a) * 0.8), Opt.of("dir", V.UP, "speed", 2.2,
                        "size", 0.24, "grow", 1.4, "life", 0.8, "color", j % 2 == 1 ? green : glow, "alpha", 1.0));
            }
            RuneKit.zonePulse(level, c, green, radius, 0.5); // 나눠 받는 아군 범위
            SkillFx.vanilla(level, "minecraft:heart_particle", V.up(c, 2.1));
            SkillFx.play(level, "random.orb", c, 0.8 + i * 0.1, 0.6);
            // 결투 중인 사람 (내 결투 상대 · 남의 결투) 은 나눠 받지 않음 (playersAround 가 뺌)
            for (ServerPlayer pl : RuneKit.playersAround(level, c, radius, sp)) {
                if (pl.getUUID().equals(sp.getUUID())) continue;
                double got = RpgHealth.healBy(pl, amountOf.applyAsDouble(pl) * k.num("share"));
                if (got > 0) {
                    DamageNumbers.show(pl, got, DamageNumbers.Style.HEAL, "", 0);
                    SkillFx.vanilla(level, "minecraft:heart_particle", V.up(pl.position(), 2.1));
                    // 나눠 받은 아군: 발밑에서 작은 샘
                    RuneKit.rfx(level, "fountain", pl.position(), Opt.of("size", 0.45, "life", 0.6, "color", green, "alpha", 0.75, "frames", 8.0, "fps", 16.0,
                            "loop", true, "phase", Math.floor(Kit.rand() * 8), "fade", 0.4));
                }
            }
            if (last) SkillFx.sparks(level, V.up(c, 1), core, 8, 3);
        });
        return true;
    }

    /**
     * 2026-11-13 생명의 샘의 3D 물기둥 둘 (water_column — 2D 빛의 샘 대신): 몸 앞 양옆 (보는 쪽에서 ±42° · 1.8칸 — 1인칭 화면 아래 양쪽) 땅에서 솟아 (3틱)
     * 2틱마다 몸을 따라오며 소용돌이가 돎 (좌우 반대로), 회복 틱 (gap 마다) 에 번쩍 커졌다 돌아옴 → span 틱 뒤 무너져 가라앉음
     */
    private static void springColumns(ServerPlayer sp, int span, int gap) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        double h = 1.7;
        double s = RuneProps.scaleFor(h, RuneProps.WATER_H);
        mmorpg.fabric.vfx.Vfx.Fx fx = RuneProps.fx(level, sp.position(), span + 16);
        int[] sds = {-1, 1};
        java.util.function.IntFunction<Vec3> spot = i -> V.add(sp.position(), RuneKit.turn(Kit.facingOf(sp), sds[i] * 42), 1.8);
        mmorpg.fabric.vfx.Vfx.Group[] cs = new mmorpg.fabric.vfx.Vfx.Group[2];
        for (int i = 0; i < 2; i++) {
            cs[i] = RuneProps.shape(fx, RuneProps.WATER_COLUMN, V.up(spot.apply(i), -h * 0.5), RuneProps.along(V.UP, new Vec3(1, 0, 0)), s * 0.4);
        }
        fx.on(1, () -> {
            for (int i = 0; i < 2; i++) cs[i].to(spot.apply(i), RuneProps.along(V.UP, RuneKit.turn(new Vec3(1, 0, 0), sds[i] * 24)), s, 3);
        });
        boolean[] done = {false};
        Runnable end = () -> {
            if (done[0]) return;
            done[0] = true;
            int a = fx.age();
            for (int i = 0; i < 2; i++) cs[i].to(V.up(spot.apply(i), -h * 0.6), null, s * 0.5, 6);
            fx.on(a + 6, () -> {
                for (mmorpg.fabric.vfx.Vfx.Group g : cs) g.remove();
            });
        };
        fx.each(4, span, 2, t -> {
            if (done[0]) return;
            if (!RuneKit.sameLife(sp, life)) {
                end.run();
                return;
            }
            boolean pulse = t % gap == 0;
            for (int i = 0; i < 2; i++) {
                double a = sds[i] * (24 + (t - 2) * 12.0);
                cs[i].to(spot.apply(i), RuneProps.along(V.UP, RuneKit.turn(new Vec3(1, 0, 0), a)), pulse ? s * 1.15 : s, 2);
            }
        });
        fx.on(span + 4, end);
    }

    /**
     * 강철 피부: 주위 충격 + ticks 동안 받는 피해 -reduce, 때린 몹에게 위력 × reflect 반사 —
     * 2026-11-13 3D 쇳빛 육각 판 여덟 (hex_panel — 2D 판 대신, ironPlates): 몸 옆 · 뒤 (±100° ~ ±178° — 1인칭 화면에는 들지 않음) 1.9칸에서 돌며 몸으로 날아들어
     * 붙는 순간 (4틱) 번쩍 → 몸속으로 작아지며 스며듦. 버티는 동안 몸 양옆 뒤의 작은 판 둘이 몸을 따라오며 20틱마다 번쩍 (ironGuard)
     */
    static boolean ironSkin(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double[][] cs = RuneKit.colorOf("vit");
        double[] glow = cs[0];
        double[] core = cs[1];
        int gather = 4; // 모여드는 틱
        double radius = k.num("radius");
        int ticks = (int) k.num("ticks");
        ironPlates(sp, gather, glow);
        ironGuard(sp, ticks, glow);
        SkillFx.play(level, "armor.equip_iron", c, 0.8, 1);
        SkillFx.play(level, "random.anvil_use", c, 1.2, 0.6);
        SkillFx.play(level, "mob.irongolem.repair", c, 1.0, 0.8);
        Guards.set(sp, ticks, k.num("reduce"), Guards.Reflect.amount(P * k.num("reflect"), DamageNumbers.Style.RUNE), null);
        // 판이 몸에 붙는 순간: 강철 빛 · 충격
        Tasks.later(gather, () -> {
            if (sp.isRemoved()) return;
            Vec3 pc = sp.position();
            SkillFx.burst(level, V.up(pc, 1), RuneKit.STEEL, 1.6, 0.35);
            RuneKit.selfCue(sp, RuneKit.STEEL, RuneKit.WHITE, true);
            RuneKit.zonePulse(level, pc, RuneKit.STEEL, radius, 0.45);
            RuneBoost.bloom(level, pc, glow, core, 3.2); // 0.50.0 2D 강화 (아래 등급 터짐): 판이 몸에 붙는 순간의 빛
            SkillFx.sparks(level, V.up(pc, 1), RuneKit.STEEL, 10, 4);
            SkillFx.play(level, "random.anvil_land", pc, 1.6, 0.5);
        });
        for (int t = 20; t < ticks; t += 20) {
            Tasks.later(t, () -> {
                if (!sp.isRemoved()) SkillFx.sparks(sp.level(), V.up(sp.position(), 1), glow, 3, 2);
            });
        }
        Tasks.later(ticks, () -> {
            if (!sp.isRemoved()) SkillFx.play(sp.level(), "armor.equip_iron", sp.position(), 0.6, 0.6);
        });
        for (LivingEntity e : Kit.around(sp, c, radius)) {
            if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) RuneKit.knock(e, V.sub(e.position(), c), 0.5, 0.2);
        }
        return true;
    }

    /** 2026-11-13 강철 피부의 3D 판 여덟: 몸 옆 · 뒤 1.9칸 → gather 틱에 몸 0.5칸까지 돌며 날아듦 (몸을 따라감) → 번쩍 → 몸속으로 작아짐 */
    private static void ironPlates(ServerPlayer sp, int gather, double[] glow) {
        ServerLevel level = sp.level();
        mmorpg.fabric.vfx.Vfx.Fx fx = RuneProps.fx(level, sp.position(), gather + 14);
        double s = RuneProps.scaleFor(0.72, RuneProps.HEX_PANEL_H);
        Vec3 f0 = Kit.facingOf(sp);
        for (int i = 0; i < 8; i++) {
            double deg = (i % 2 == 1 ? 1 : -1) * (100 + (i / 2) * 26);
            double y = 0.5 + (i % 3) * 0.55;
            double spin0 = i * 40;
            Vec3 d0 = RuneKit.turn(f0, deg);
            mmorpg.fabric.vfx.Vfx.Prop p = RuneProps.prop(fx, RuneProps.HEX_PANEL, V.up(V.add(sp.position(), d0, 1.9), y), RuneProps.MID, RuneProps.facing(d0, V.UP), s * 0.6,
                    RuneKit.STEEL, glow);
            for (int t = 1; t <= gather; t++) {
                final double u = t / (double) gather;
                fx.on(t, () -> {
                    Vec3 d = RuneKit.turn(Kit.facingOf(sp), deg);
                    Vec3 at = V.up(V.add(sp.position(), d, 1.9 + (0.5 - 1.9) * u), y);
                    Vec3 up = RuneSwords.rot(V.UP, d, spin0 + u * 160);
                    p.to(at, RuneProps.facing(d, up), s * (0.6 + 0.4 * u), 1);
                });
            }
            fx.on(gather + 1, () -> p.size(s * 1.3, 1));
            fx.on(gather + 2, () -> {
                Vec3 d = RuneKit.turn(Kit.facingOf(sp), deg);
                p.to(V.up(V.add(sp.position(), d, 0.15), y), null, s * 0.1, 5);
            });
            fx.on(gather + 7, p::remove);
        }
    }

    /** 2026-11-13 버티는 동안 몸 양옆 뒤 (보는 쪽에서 ±130° · 0.6칸 · 높이 1.15) 의 작은 3D 판 둘 (바깥을 봄 — 천천히 돎): 틱마다 몸을 따라오고 20틱마다 번쩍 → 끝에 작아짐 */
    private static void ironGuard(ServerPlayer sp, int ticks, double[] glow) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        mmorpg.fabric.vfx.Vfx.Fx fx = RuneProps.fx(level, sp.position(), ticks + 10);
        double s = RuneProps.scaleFor(0.55, RuneProps.HEX_PANEL_H);
        int[] sds = {-1, 1};
        mmorpg.fabric.vfx.Vfx.Prop[] ps = new mmorpg.fabric.vfx.Vfx.Prop[2];
        java.util.function.BiFunction<Integer, Integer, Vec3> spot = (i, t) -> V.up(V.add(sp.position(), RuneKit.turn(Kit.facingOf(sp), sds[i] * 130), 0.6),
                1.15 + 0.07 * Math.sin(t * 0.2 + i * 2.1));
        for (int i = 0; i < 2; i++) {
            Vec3 d = RuneKit.turn(Kit.facingOf(sp), sds[i] * 130);
            ps[i] = RuneProps.prop(fx, RuneProps.HEX_PANEL, spot.apply(i, 0), RuneProps.MID, RuneProps.facing(d, V.UP), s * 0.1, RuneKit.STEEL, glow);
        }
        boolean[] done = {false};
        Runnable end = () -> {
            if (done[0]) return;
            done[0] = true;
            int a = fx.age();
            for (mmorpg.fabric.vfx.Vfx.Prop p : ps) p.size(s * 0.1, 5);
            fx.on(a + 5, () -> {
                for (mmorpg.fabric.vfx.Vfx.Prop p : ps) p.remove();
            });
        };
        fx.each(5, ticks, 1, t -> {
            if (done[0]) return;
            if (!RuneKit.sameLife(sp, life)) {
                end.run();
                return;
            }
            boolean flash = t % 20 == 0;
            for (int i = 0; i < 2; i++) {
                Vec3 d = RuneKit.turn(Kit.facingOf(sp), sds[i] * 130);
                Vec3 up = RuneSwords.rot(V.UP, d, t * 4.0 * sds[i]);
                double sc = t <= 8 ? s * (0.1 + 0.9 * (t - 4) / 4.0) : flash ? s * 1.35 : s;
                ps[i].to(spot.apply(i, t), RuneProps.facing(d, up), sc, 1);
            }
        });
        fx.on(ticks, end);
    }

    /**
     * 2026-11-13 불굴의 방벽의 3D 빛의 육각 판 열둘 (hex_panel — 바깥을 봄, 셋째마다 금빛 · 거의 투명한 판에 밝은 테 · 가운데 보석): 땅속에서 차례로 솟음 (보는 쪽부터
     * — 5틱), pulse 틱마다 모두 번쩍 커졌다 돌아옴 (안의 적을 밀어내는 틱), ticks 틱에 차례로 가라앉음. 예전 2D 육각 판 (10틱마다 다시 그림) 대신
     */
    private static void bulwarkPanels(ServerPlayer sp, Vec3 c, double radius, double plate, int ticks, int pulse, double[] glow, double[] gold, double[] core) {
        ServerLevel level = sp.level();
        mmorpg.fabric.vfx.Vfx.Fx fx = RuneProps.fx(level, c, ticks + 24);
        int n = 12;
        double sc = RuneProps.scaleFor(plate * 2, RuneProps.HEX_PANEL_H);
        double yaw0 = Math.atan2(Kit.facingOf(sp).z, Kit.facingOf(sp).x);
        for (int j = 0; j < n; j++) {
            double a = ((double) j / n) * Math.PI * 2;
            Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 at = V.up(V.add(c, d, radius), plate * 0.93);
            org.joml.Quaternionf rot = RuneProps.facing(d, V.UP);
            double diff = Math.abs(Math.atan2(Math.sin(a - yaw0), Math.cos(a - yaw0)));
            int delay = 1 + (int) Math.round(diff / Math.PI * 6);
            mmorpg.fabric.vfx.Vfx.Prop p = RuneProps.prop(fx, RuneProps.HEX_PANEL, V.up(at, -plate * 2.4), new double[] {8, 8, 8}, rot, sc * 0.3,
                    j % 3 != 0 ? glow : gold, core);
            fx.on(delay, () -> p.to(at, null, sc, 5));
            for (int t = pulse; t < ticks; t += pulse) {
                fx.on(t, () -> p.size(sc * 1.09, 2));
                fx.on(t + 3, () -> p.size(sc, 5));
            }
            fx.on(ticks + delay, () -> p.to(V.up(at, -plate * 2.4), null, sc * 0.4, 8));
            fx.on(ticks + delay + 9, p::remove);
        }
    }

    /**
     * 불굴의 방벽: 선 자리에 방벽 (ticks). 안의 아군은 받는 피해 -reduce, 안의 적은 pulse 틱마다 피해 + 밀려남 —
     * 둘레에 빛의 육각 판 12장이 벽을 이룸 (2026-11-13 3D — bulwarkPanels: 판마다 바깥을 봄, 금빛 셋째마다 · 거의 투명한 판에 밝은 테)
     */
    static boolean bulwark(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double[][] cs = RuneKit.colorOf("vit");
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] gold = {1.0, 0.82, 0.45};
        int n = 12;
        double radius = k.num("radius");
        int ticks = (int) k.num("ticks");
        int pulse = (int) k.num("pulse");
        double plate = (2 * Math.PI * radius) / n / 2 / 0.785; // 판 반 크기 (육각 판의 너비 = 크기 × 2 × 0.785 → 이웃과 맞닿음)
        SkillFx.rune(level, c, Opt.of("size", radius, "life", ticks / 20.0, "color", glow, "alpha", 0.75, "rate", 20.0, "grow", 0.0));
        RuneBoost.bloom(level, c, glow, core, 3.5); // 0.50.0 2D 강화 (아래 등급 터짐): 방벽이 서는 순간의 빛 (방벽 가운데)
        SkillFx.play(level, "beacon.activate", c, 1.0, 1);
        SkillFx.play(level, "random.totem", c, 1.6, 0.3);
        bulwarkPanels(sp, c, radius, plate, ticks, pulse, glow, gold, core);
        for (int t = 0; t < ticks; t += 10) {
            Tasks.later(t, () -> RuneKit.zonePulse(level, V.up(c, plate * 1.85), gold, radius, 0.55)); // 위쪽 빛 고리 (방벽 지붕)
        }
        int pulses = ticks / pulse;
        for (int i = 0; i < pulses; i++) {
            final int ii = i;
            Tasks.later(i * pulse, () -> {
                RuneKit.zonePulse(level, c, ii % 2 == 1 ? core : glow, radius, 0.5);
                SkillFx.play(level, "beacon.ambient", c, 1.4, 0.5);
                // 결투 중인 사람은 빼고 (내 결투 상대는 안에 있으면 적처럼 맞음 · 남의 결투는 밖에서 돕지 못함 — playersAround 가 뺌)
                for (ServerPlayer pl : RuneKit.playersAround(level, c, radius, sp)) Guards.set(pl, pulse + 5, k.num("reduce"), null, null);
                if (sp.isRemoved()) return;
                int flashed = 0;
                for (LivingEntity e : Kit.around(sp, c, radius, 3)) {
                    if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                        RuneKit.knock(e, V.sub(e.position(), c), 0.7, 0.25);
                        // 번쩍임 12명 · 방벽에 튕겨 나가는 판 4명까지 (파티클 예산 — 방벽 판 24장과 같은 틱)
                        if (flashed++ < 12) SkillFx.burst(level, Kit.bodyPoint(e), gold, 0.6, 0.2);
                        if (flashed > 4) continue;
                        Vec3 d = V.flat(V.sub(e.position(), c));
                        RuneKit.hexPlate(level, V.up(V.add(c, d, radius), 1.2), d, 0.7, RuneKit.WHITE, Opt.of("life", 0.25, "fade", 0.6, "alpha", 0.8,
                                "grow", 0.3, "core", gold));
                    }
                }
            });
        }
        Tasks.later(ticks, () -> SkillFx.play(level, "beacon.deactivate", c, 1.0, 1));
        return true;
    }

    /** 이름으로 찾은 블록 (자바 판마다 이름표 필드가 바뀌어도 — 26.3 에는 Blocks.RED_STAINED_GLASS 필드가 없음), 없으면 fallback */
    private static net.minecraft.world.level.block.state.BlockState blockOr(String id, net.minecraft.world.level.block.Block fallback) {
        net.minecraft.world.level.block.Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                net.minecraft.resources.Identifier.withDefaultNamespace(id));
        return (b == null || b == net.minecraft.world.level.block.Blocks.AIR ? fallback : b).defaultBlockState();
    }

    private static final net.minecraft.world.level.block.state.BlockState PACT_SHELL = blockOr("red_stained_glass",
            net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
    private static final net.minecraft.world.level.block.state.BlockState PACT_CORE = net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState();

    /**
     * 2026-11-13 불사의 맹약의 핏빛 결정 (만년빙하처럼 — 블록을 꼭짓점으로 세워 늘린 결정: 붉은 유리 겉 · 레드스톤 블록 속): 맺는 순간 둘레에서 결정 아홉이
     * 바깥으로 기울어 솟고 (앞 50° 는 비움 — 1인칭 화면) 1.5초 뒤 부서져 가라앉음, pulse 틱마다 몸 둘레에 작은 결정 둘이 솟았다 부서짐 (회복 틱). 죽으면 멈춤
     */
    private static void pactCrystals(ServerPlayer sp, Vec3 c0, int pulses, int pulse, int alive) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        int n = 9;
        for (int i = 0; i < n; i++) {
            double a = 50 + (i + 0.5) / n * 260 + (Kit.rand() - 0.5) * 14;
            Vec3 d = RuneKit.turn(f, a);
            double rr = 1.9 + Kit.rand() * 0.8;
            Vec3 b = V.add(c0, d, rr);
            Vec3 base = new Vec3(b.x, RuneKit.groundAt(level, b, c0.y), b.z);
            double h = 1.5 + Kit.rand() * 1.3;
            RuneGlacier.Crystal cr = new RuneGlacier.Crystal(RuneGlacier.tilted(d, 18 + Kit.rand() * 18), h, 0.55 + h * 0.18, Kit.rand() * 90);
            RuneGlacier.erupt(level, base, cr, PACT_CORE, PACT_SHELL, 1 + i % 3, 4, 26 + (int) (Kit.rand() * 6), 8);
        }
        SkillFx.play(level, "block.amethyst_block.break", c0, 1.2, 0.6);
        SkillFx.play(level, "block.glass.break", c0, 1.0, 0.7);
        for (int k = 1; k < pulses; k++) {
            Tasks.later(k * pulse, () -> {
                if (!RuneKit.aliveSince(sp, alive)) return;
                Vec3 c = sp.position();
                Vec3 ff = Kit.facingOf(sp);
                for (int j = 0; j < 2; j++) {
                    Vec3 d = RuneKit.turn(ff, 70 + Kit.rand() * 220);
                    Vec3 b = V.add(c, d, 1.3 + Kit.rand() * 0.6);
                    Vec3 base = new Vec3(b.x, RuneKit.groundAt(sp.level(), b, c.y), b.z);
                    double h = 0.8 + Kit.rand() * 0.6;
                    RuneGlacier.Crystal cr = new RuneGlacier.Crystal(RuneGlacier.tilted(d, 22 + Kit.rand() * 14), h, 0.4 + h * 0.2, Kit.rand() * 90);
                    RuneGlacier.erupt(sp.level(), base, cr, PACT_CORE, PACT_SHELL, 1 + j, 3, 8, 6);
                }
                SkillFx.play(sp.level(), "block.amethyst_cluster.break", c, 0.8, 0.8);
            });
        }
    }

    /**
     * 불사의 맹약: ticks 동안 체력이 1 아래로 내려가지 않음, pulse 틱마다 회복 + 주위 피해.
     * 쓸 때 눈앞에 불사의 문장 (rpg:rfx_emblem — 핏빛 심장 · 금빛 날개 · 가시 후광) 이 번쩍이고, 그 동안 머리 위에 문장이 떠 있고 몸에 핏빛 기운.
     * 초월 2차 강화: 진홍 하늘 · 붉은 번쩍임 · 발밑의 성광 문양 (진홍) · 앞쪽 하늘의 진홍 후광 · 앞쪽 핏빛 빛기둥 셋. 죽으면 하늘빛도 걷힘
     */
    static boolean undyingPact(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level0 = sp.level();
        double[] glow = RuneKit.colorOf("vit")[0];
        double[] crimson = {0.9, 0.12, 0.25};
        double[] gold = {1.0, 0.8, 0.35};
        int ticks = (int) k.num("ticks");
        int pulse = (int) k.num("pulse");
        double radius = k.num("radius");
        UNDYING.put(sp.getUUID(), Tasks.now() + ticks);
        // 8단계 3돌: 쓸 때 최대 체력의 instantHeal 만큼 바로 회복
        if (k.numOr("instantHeal", 0) > 0) {
            double healed = RpgHealth.healBy(sp, RpgHealth.maxOf(sp) * k.num("instantHeal"));
            if (healed > 0) DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
        }
        Vec3 c0 = sp.position();
        // 머리 위로 치솟는 핏빛 기둥 (몸에서 시작하면 1인칭 카메라가 기둥 안에 들어감)
        SkillFx.fx(level0, "rpg:arrow_shot", V.up(c0, 4.6), Opt.of("dir", V.UP, "speed", 0.0, "size", 2.6, "thick", 1.1, "life", 0.6, "color", crimson,
                "alpha", 0.85));
        SkillFx.fx(level0, "rpg:arrow_shot", V.up(c0, 4.6), Opt.of("dir", V.UP, "speed", 0.0, "size", 2.4, "thick", 0.35, "life", 0.5, "color", gold,
                "alpha", 1.0));
        // 눈앞 문장 (화면 위쪽, 1인칭에서 보임)
        Vec3 eye0 = sp.getEyePosition();
        Vec3 view0 = sp.getViewVector(1F);
        Vec3 emAt = V.up(V.add(eye0, view0, 2.8), 0.45);
        Opt emO = Opt.of("size", 0.62, "life", 0.9, "grow", 0.35, "fade", 0.55);
        // 0.50.0 2D 강화 (초월 나머지): 문장 후광 — 미리 칠한 그림이라 천공의 눈처럼 물들이지 않고 (흰색) 1.35배 · 옅게
        RuneBoost.aura(level0, "emblem", emAt, emO, RuneKit.WHITE, 1.35, 0.42);
        RuneKit.rfx(level0, "emblem", emAt, emO);
        SkillFx.burst(level0, V.up(c0, 1), crimson, 2.0, 0.4);
        RuneKit.selfCue(sp, crimson, gold, true);
        RuneBoost.bloom(level0, c0, crimson, gold, radius); // 0.50.0 2D 강화 (초월 나머지): 맹약을 맺는 순간의 빛
        SkillFx.vanilla(level0, "minecraft:totem_particle", V.up(c0, 1));
        SkillFx.play(level0, "random.totem", c0, 0.9, 0.9);
        SkillFx.play(level0, "mob.wither.spawn", c0, 1.8, 0.25);
        // 2차 강화 — 맹약: 진홍 하늘 · 붉은 번쩍임 · 발밑의 성광 문양 (진홍) · 앞쪽 하늘의 진홍 후광 · 앞쪽 핏빛 빛기둥 셋 (2틱 뒤)
        RuneKit.ultSkyFor(sp, level0, c0, "undyingPact", 90, null);
        RuneKit.ultFlashFor(sp, level0, c0, "undyingPact", "flash", Opt.empty());
        RuneKit.domainSigil(level0, c0, 4.6, "holy", crimson, gold, Opt.of("life", 1.6, "spinRate", 40.0, "grow", 0.3, "alpha", 0.85));
        // 하늘의 진홍 후광 (눈앞 문장과 겹치지 않게 문양)
        RuneKit.skyPlate(sp, level0, "sigil", RuneKit.skyPoint(sp, c0, 12, 22, 6, 22), 5.6, crimson, gold, Opt.of("cell", (double) RuneKit.SIGIL.get("holy"),
                "life", 2.6, "spinRate", -22.0, "grow", 0.15, "alpha", 0.8, "fade", 0.45));
        RuneKit.later(2, () -> {
            if (!sp.isRemoved()) {
                RuneKit.godRays(sp, sp.level(), sp.position(), 6, 3, crimson, gold, Opt.of("arc", new double[] {-50, 50}, "h", 15.0, "life", 0.9));
            }
        });
        int pulses = ticks / pulse;
        int alive = RuneKit.deathToken(sp); // 죽으면 끝 (회복 · 반격 · 기운 · 쓰러지지 않음 모두 — 차원 이동 · 탈것 부르기로는 끝나지 않음)
        pactCrystals(sp, c0, pulses, pulse, alive);
        for (int i = 0; i < pulses; i++) {
            final int ii = i;
            Tasks.later(i * pulse, () -> {
                if (!RuneKit.aliveSince(sp, alive)) {
                    endSky(sp); // 죽으면 진홍 하늘도 걷힘
                    return;
                }
                ServerLevel level = sp.level();
                Vec3 c = sp.position();
                SkillFx.rune(level, c, Opt.of("size", 2.2, "life", pulse / 20.0 + 0.1, "color", crimson, "alpha", 0.85, "rate", 90.0, "spin", ii * 40.0, "grow",
                        0.0));
                RuneKit.zonePulse(level, c, ii % 2 == 1 ? gold : glow, radius, 0.45);
                double healed = RpgHealth.healBy(sp, RpgHealth.maxOf(sp) * k.num("heal") * Runes.supportScale(RpgStats.effective(sp), r.family()));
                if (healed > 0) DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
                if (ii > 0) RuneKit.selfCue(sp, crimson, gold, false);
                SkillFx.vanilla(level, "minecraft:heart_particle", V.up(c, 2.1));
                SkillFx.play(level, "note.bass", c, 0.6, 0.8);
                for (LivingEntity e : Kit.around(sp, c, radius)) {
                    if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) SkillFx.burst(level, Kit.bodyPoint(e), crimson, 0.6, 0.22);
                }
            });
        }
        // 그 동안: 몸의 핏빛 기운 (2틱마다) · 머리 위 문장 (4틱마다 짧게 — 몸을 따라옴, 달리다 멈춰도 앞으로 미끄러지지 않게). 죽으면 멈춤
        for (int t = 2; t < ticks; t += 2) {
            final int tt = t;
            Tasks.later(t, () -> {
                if (!RuneKit.aliveSince(sp, alive)) return;
                RuneKit.bodyAura(sp, crimson, Opt.of("n", 4, "alpha", 0.68));
                if (tt % 12 == 0) RuneKit.auraCue(sp, tt % 24 != 0 ? gold : crimson, 2);
                if (tt % 4 == 0) {
                    Opt ho = Opt.of("size", 0.42, "life", 0.24, "vel", RuneKit.bodyVel(sp));
                    RuneBoost.aura(sp.level(), "emblem", V.up(sp.position(), 2.95), ho, RuneKit.WHITE, 1.35, 0.42); // 0.50.0 2D 강화 (초월 나머지): 머리 위 문장 후광
                    RuneKit.rfx(sp.level(), "emblem", V.up(sp.position(), 2.95), ho);
                }
            });
        }
        Tasks.later(ticks, () -> {
            if (!sp.isRemoved()) SkillFx.play(sp.level(), "beacon.deactivate", sp.position(), 1.2, 0.8);
        });
        return true;
    }

    /** 불사의 맹약의 진홍 하늘빛을 일찍 걷음 (죽음): 다시 태어났으면 새 플레이어 엔티티에게 (같은 사람) */
    private static void endSky(ServerPlayer sp) {
        ServerPlayer cur = RuneKit.player(sp.level(), sp.getUUID());
        Ult.endSky(List.of(cur != null ? cur : sp), RuneKit.ultOf("undyingPact").strOrNull("fog"));
    }

    /** 불사가 쓰러짐을 막았을 때 (RpgHealth.undyingSaved — 다음 틱): 눈앞에 불사의 문장이 크게 번쩍임 */
    static void onUndyingSaved(ServerPlayer sp) {
        long now = Tasks.now();
        if (now - UNDYING_FX_AT.getOrDefault(sp.getUUID(), -100L) < 10) return;
        UNDYING_FX_AT.put(sp.getUUID(), now);
        ServerLevel level = sp.level();
        Vec3 c = V.up(sp.position(), 1);
        SkillFx.burst(level, c, new double[] {1.0, 0.8, 0.35}, 1.8, 0.35);
        RuneKit.rfx(level, "emblem", V.up(V.add(sp.getEyePosition(), sp.getViewVector(1F), 2.6), 0.2), Opt.of("size", 0.85, "life", 0.7, "grow", 0.4,
                "fade", 0.6));
        SkillFx.vanilla(level, "minecraft:totem_particle", c);
        SkillFx.play(level, "random.totem", c, 1.4, 0.6);
        Notify.notify(sp, "§6§l불사! §r§7쓰러지지 않았습니다", 30);
    }

    /** 불사의 맹약 중인지 (RpgHealth.undying — 체력이 1 아래로 내려가지 않음) */
    static boolean isUndying(ServerPlayer sp) {
        Long until = UNDYING.get(sp.getUUID());
        return until != null && until > Tasks.now();
    }
}
