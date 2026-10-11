package mmorpg.fabric.rune;

import java.util.ArrayList;
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
        // 큰 방패 문장: 앞 1.8칸 · 머리 위쪽 (1인칭 화면 위쪽에 보이고, 3인칭에서는 몸 앞 위)
        Vec3 em = V.up(V.add(c, Kit.facingOf(sp), 1.8), 2.35);
        RuneKit.shieldEmblem(sp, level, em, 0.6, glow, Opt.of("life", 0.8, "grow", 0.35, "fade", 0.5, "alpha", 0.85, "vel", RuneKit.bodyVel(sp), "full", 3.0,
                "min", 0.45));
        SkillFx.sparks(level, V.up(c, 1), core, 8, 4);
        SkillFx.play(level, "mob.ravager.roar", eye, 1.4, 0.8);
        SkillFx.play(level, "random.anvil_land", c, 0.6, 0.4);
        Guards.set(sp, ticks, k.num("reduce"), null, null);
        // 버티는 동안: 몸 양옆 뒤쪽 (보는 쪽에서 ±115°) 의 작은 방패 둘 (1인칭 화면 밖 · 3인칭에서 보임). 10틱마다 다시 그림 (몸을 따라옴)
        for (int t = 4; t < ticks; t += 10) {
            Tasks.later(t, () -> {
                if (sp.isRemoved()) return;
                Vec3 pc = sp.position();
                Vec3 f = Kit.facingOf(sp);
                Vec3 vel = RuneKit.bodyVel(sp);
                for (int sd : new int[] {-1, 1}) {
                    Vec3 d = RuneKit.turn(f, sd * 115);
                    RuneKit.shieldEmblem(sp, sp.level(), V.up(V.add(pc, d, 0.8), 1.15), 0.3, glow, Opt.of("life", 0.55, "fade", 0.15, "alpha", 0.85, "vel", vel));
                }
            });
        }
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
        SkillFx.play(level0, "beacon.power", sp.position(), 1.5, 0.8);
        SkillFx.play(level0, "bucket.empty_water", sp.position(), 1.2, 0.6);
        SkillFx.every(pulses, gap, i -> {
            if (sp.isRemoved()) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            Vec3 vel = RuneKit.bodyVel(sp);
            double healed = RpgHealth.healBy(sp, amountOf.applyAsDouble(sp));
            if (healed > 0) DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
            RuneKit.selfCue(sp, green, glow, i == 0);
            // 빛의 샘 둘 (보는 쪽에서 ±42°, 1.8칸 — 1인칭 화면 아래 양쪽): 다음 회복까지 이어짐 (+0.1초 겹침 — 깜빡임 없게), 마지막은 천천히 사라짐
            boolean last = i == pulses - 1;
            for (int sd : new int[] {-1, 1}) {
                Vec3 at = V.add(c, RuneKit.turn(f, sd * 42), 1.8);
                RuneKit.rfx(level, "fountain", at, Opt.of("size", 1.0, "life", last ? 0.8 : gap / 20.0 + 0.1, "color", sd > 0 ? green : core, "alpha", 0.95,
                        "frames", 8.0, "fps", 16.0, "loop", true, "phase", (double) (i * 3 + (sd > 0 ? 4 : 0)), "fade", last ? 0.5 : 0.05, "grow",
                        i == 0 ? 0.15 : 0.0, "vel", vel));
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

    /** 강철 피부의 판 하나: 몸에서의 방향 · 높이 · 돎 */
    private record Plate(Vec3 d, double y, double spin) {}

    /**
     * 강철 피부: 주위 충격 + ticks 동안 받는 피해 -reduce, 때린 몹에게 위력 × reflect 반사 — 쇳빛 육각 판 (rpg:rfx_hex, 분홍빛 테두리) 여덟 장이
     * 몸 옆 · 뒤 (±100° ~ ±178° — 1인칭 화면에는 들지 않음) 에서 몸으로 모여들어 붙고 번쩍임 → 버티는 동안 몸 옆 · 뒤에서 판이 반짝임
     */
    static boolean ironSkin(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double[][] cs = RuneKit.colorOf("vit");
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] gun = {0.5, 0.56, 0.66}; // 쇳빛 판 + 분홍빛 흰 테두리 (체력 계열 색)
        Vec3 f = Kit.facingOf(sp);
        Vec3 vel = RuneKit.bodyVel(sp);
        double gather = 0.2; // 모여드는 초
        double r0 = 1.9;
        double r1 = 0.55;
        double radius = k.num("radius");
        int ticks = (int) k.num("ticks");
        List<Plate> plates = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double deg = (i % 2 == 1 ? 1 : -1) * (100 + (i / 2) * 26); // 옆 · 뒤 (±100° ~ ±178° — 1인칭 화면에는 들지 않음)
            Vec3 d = RuneKit.turn(f, deg);
            double y = 0.5 + (i % 3) * 0.55;
            plates.add(new Plate(d, y, i * 20));
            RuneKit.hexPlate(level, V.up(V.add(c, d, r0), y), d, 0.36, gun, Opt.of("speed", -(r0 - r1) / gather, "life", gather, "fade", 0.0, "alpha", 0.95,
                    "spin", i * 20.0, "vel", vel, "core", core));
        }
        // 몸에 붙은 판 (모여든 자리 — 날아온 판이 끝나기 1틱 전에 나타남, 0.4초 남았다 사라짐)
        Tasks.later(Math.max(1, (int) Math.round(gather * 20) - 1), () -> {
            if (sp.isRemoved()) return;
            Vec3 pc = sp.position();
            Vec3 pv = RuneKit.bodyVel(sp);
            for (Plate q : plates) {
                RuneKit.hexPlate(sp.level(), V.up(V.add(pc, q.d(), r1), q.y()), q.d(), 0.36, gun, Opt.of("life", 0.4, "fade", 0.6, "alpha", 0.95, "spin", q.spin(),
                        "vel", pv, "core", core));
            }
        });
        SkillFx.play(level, "armor.equip_iron", c, 0.8, 1);
        SkillFx.play(level, "random.anvil_use", c, 1.2, 0.6);
        SkillFx.play(level, "mob.irongolem.repair", c, 1.0, 0.8);
        Guards.set(sp, ticks, k.num("reduce"), Guards.Reflect.amount(P * k.num("reflect"), DamageNumbers.Style.RUNE), null);
        // 판이 몸에 붙는 순간: 강철 빛 · 충격
        Tasks.later((int) Math.round(gather * 20), () -> {
            if (sp.isRemoved()) return;
            Vec3 pc = sp.position();
            SkillFx.burst(level, V.up(pc, 1), RuneKit.STEEL, 1.6, 0.35);
            RuneKit.selfCue(sp, RuneKit.STEEL, RuneKit.WHITE, true);
            RuneKit.zonePulse(level, pc, RuneKit.STEEL, radius, 0.45);
            SkillFx.sparks(level, V.up(pc, 1), RuneKit.STEEL, 10, 4);
            SkillFx.play(level, "random.anvil_land", pc, 1.6, 0.5);
        });
        for (int t = 10; t < ticks; t += 10) {
            final int tt = t;
            Tasks.later(t, () -> {
                if (sp.isRemoved()) return;
                Vec3 pc = sp.position();
                Vec3 pf = Kit.facingOf(sp);
                Vec3 pv = RuneKit.bodyVel(sp);
                // 몸 옆 · 뒤의 작은 강철 판 둘 (번갈아 양쪽)
                for (int j = 0; j < 2; j++) {
                    Vec3 d = RuneKit.turn(pf, (j == 1 ? 1 : -1) * (100 + Kit.rand() * 60));
                    RuneKit.hexPlate(sp.level(), V.up(V.add(pc, d, 0.45), 0.5 + Kit.rand() * 1.1), d, 0.3, gun, Opt.of("life", 0.45, "fade", 0.5, "alpha", 0.9,
                            "grow", 0.2, "vel", pv, "core", core));
                }
                if (tt % 20 == 0) SkillFx.sparks(sp.level(), V.up(pc, 1), glow, 3, 2);
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

    /**
     * 불굴의 방벽: 선 자리에 방벽 (ticks). 안의 아군은 받는 피해 -reduce, 안의 적은 pulse 틱마다 피해 + 밀려남 —
     * 둘레에 빛의 육각 판 12장이 벽을 이룸 (판마다 바깥을 봄, 금빛 셋째마다 — 밝은 테두리 · 옅은 판). 쓴 사람이 보는 쪽의 판은 테두리만,
     * 카메라 가까이의 판은 흐리게
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
        SkillFx.play(level, "beacon.activate", c, 1.0, 1);
        SkillFx.play(level, "random.totem", c, 1.6, 0.3);
        for (int t = 0; t < ticks; t += 10) {
            final int tt = t;
            Tasks.later(t, () -> {
                boolean first = tt == 0;
                boolean valid = !sp.isRemoved();
                Vec3 eye = valid ? sp.getEyePosition() : null;
                Vec3 view = valid ? V.flat(sp.getViewVector(1F)) : null;
                for (int j = 0; j < n; j++) {
                    double a = ((double) j / n) * Math.PI * 2;
                    Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 p = V.up(V.add(c, d, radius), plate * 0.93);
                    // 카메라 (1인칭 눈 · 3인칭 뒤 4.5칸) 가까이의 판은 흐리게 — 화면을 덮지 않게
                    double near = valid ? Math.max(0.15, Math.min(1, (RuneKit.camDist(sp, p) - 1) / 2.5)) : 1;
                    // 쓴 사람이 보는 쪽 (±60°) 의 판은 테두리만 (1인칭에서 벽 너머의 적 · 조준점이 보이게)
                    boolean front = eye != null && V.dot(V.unit(V.flat(V.sub(p, eye))), view) > 0.5;
                    RuneKit.hexPlate(level, p, d, plate, j % 3 != 0 ? glow : gold, Opt.of("life", 0.55, "fade", tt + 10 >= ticks ? 0.4 : 0.0, "alpha",
                            (front ? 0.1 : 0.3) * near, "coreAlpha", (front ? 0.7 : 0.95) * near, "grow", first ? 0.08 : 0.0, "core", core));
                }
                RuneKit.zonePulse(level, V.up(c, plate * 1.85), gold, radius, 0.55); // 위쪽 빛 고리 (방벽 지붕)
            });
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
        Vec3 c0 = sp.position();
        // 머리 위로 치솟는 핏빛 기둥 (몸에서 시작하면 1인칭 카메라가 기둥 안에 들어감)
        SkillFx.fx(level0, "rpg:arrow_shot", V.up(c0, 4.6), Opt.of("dir", V.UP, "speed", 0.0, "size", 2.6, "thick", 1.1, "life", 0.6, "color", crimson,
                "alpha", 0.85));
        SkillFx.fx(level0, "rpg:arrow_shot", V.up(c0, 4.6), Opt.of("dir", V.UP, "speed", 0.0, "size", 2.4, "thick", 0.35, "life", 0.5, "color", gold,
                "alpha", 1.0));
        // 눈앞 문장 (화면 위쪽, 1인칭에서 보임)
        Vec3 eye0 = sp.getEyePosition();
        Vec3 view0 = sp.getViewVector(1F);
        RuneKit.rfx(level0, "emblem", V.up(V.add(eye0, view0, 2.8), 0.45), Opt.of("size", 0.62, "life", 0.9, "grow", 0.35, "fade", 0.55));
        SkillFx.burst(level0, V.up(c0, 1), crimson, 2.0, 0.4);
        RuneKit.selfCue(sp, crimson, gold, true);
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
                if (tt % 4 == 0) RuneKit.rfx(sp.level(), "emblem", V.up(sp.position(), 2.95), Opt.of("size", 0.42, "life", 0.24, "vel", RuneKit.bodyVel(sp)));
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
