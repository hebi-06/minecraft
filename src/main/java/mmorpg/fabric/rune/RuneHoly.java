package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Px;
import mmorpg.fabric.vfx.Vfx;
import mmorpg.fabric.vfx.VfxKit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 성기사 룬 (runes.js "성기사" — 금빛: 보호막 · 정화 · 기절, 신성 피해 = 마법, 언데드 1.5배 (RUNE.undeadBonus)):
 * 빛의 보호막 · 정화의 빛 · 심판의 망치 · 천벌 · 대천사 강림
 */
final class RuneHoly {
    private RuneHoly() {}

    static void register() {
        RuneCast.register("lightBarrier", RuneHoly::lightBarrier);
        RuneCast.register("purify", RuneHoly::purify);
        RuneCast.register("judgeHammer", RuneHoly::judgeHammer);
        RuneCast.register("divineSmite", RuneHoly::divineSmite);
        RuneCast.register("archangel", RuneHoly::archangel);
    }

    /** 회복 · 보호량 배율 (supportScale — 적용 스탯 1포인트당 perPoint 의 절반) */
    private static double supportScale(ServerPlayer sp, Runes.Rune r) {
        return Runes.supportScale(RpgStats.effective(sp), r.family());
    }

    /** 보호막을 받은 사람: 몸 옆 · 뒤에 금빛 방패 문장 셋이 나타나 몸으로 스며듦 (1인칭은 눈앞 고리 · 빛 알갱이). full = false 면 방패 없이 */
    private static void shieldGainFx(ServerPlayer pl, double[] glow, double[] core, boolean full) {
        ServerLevel level = pl.level();
        Vec3 c = pl.position();
        Vec3 f = Kit.facingOf(pl);
        Vec3 vel = RuneKit.bodyVel(pl);
        // 방패 셋은 쓴 사람과 아군 둘까지 (여럿이 받으면 파티클 예산 — 나머지는 빛 알갱이 · 고리만)
        if (full) {
            for (double deg : new double[] {-105, 105, 180}) {
                Vec3 d = RuneKit.turn(f, deg);
                RuneKit.rfx2(level, "shield", V.up(V.add(c, d, 0.95), 1.15), Opt.of("dir", V.scale(d, -1), "speed", 1.2, "size", 0.34, "life", 0.5, "grow", 0.15,
                        "fade", 0.5, "alpha", 0.9, "vel", vel), glow, RuneKit.WHITE);
            }
        }
        RuneKit.selfCue(pl, glow, core, true);
        SkillFx.play(level, "armor.equip_gold", c, 1.2, 1);
    }

    /**
     * 빛의 보호막: 나와 radius 칸 안 아군에게 보호막 (최대 체력 × shield × 스탯 비례, ticks), 가까운 적 (hitRadius) 은 밀어내며 신성 피해 —
     * 앞 머리 위에 커다란 금빛 방패 문장 (rpg:rfx_shield), 받은 사람마다 몸 둘레의 방패 셋 (shieldGainFx — 쓴 사람 + 아군 둘까지)
     */
    static boolean lightBarrier(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double[][] cs = RuneKit.colorOf("holy");
        double[] glow = cs[0];
        double[] core = cs[1];
        double scaleS = supportScale(sp, r);
        int others = 0;
        for (ServerPlayer pl : RuneKit.playersAround(level, c, k.num("radius"), sp)) {
            double amt = RpgHealth.maxOf(pl) * k.num("shield") * scaleS;
            double got = RpgHealth.addShield(pl, amt, (int) k.num("ticks"), amt); // 다시 걸면 쌓이지 않고 이 양까지 채움
            if (got > 0) {
                // 받은 사람의 그림: 쓴 사람은 바로, 아군은 한 틱씩 늦게 (같은 틱에 몰리지 않게 — 보호막은 지금)
                boolean mine = pl.getUUID().equals(sp.getUUID());
                int order = mine ? 0 : ++others;
                RuneKit.later(order, () -> {
                    if (!pl.isRemoved()) shieldGainFx(pl, glow, core, mine || order <= 2);
                });
                if (!mine) Notify.notify(pl, "§6보호막 +" + Math.round(got) + " §7(" + sp.getName().getString() + "님의 " + k.str("name") + ")", 30);
            }
        }
        SkillFx.rune(level, c, Opt.of("size", 2.2, "life", 0.9, "color", glow, "alpha", 0.9, "rate", 90.0, "grow", 0.3));
        RuneKit.zonePulse(level, c, glow, k.num("radius"), 0.55);
        RuneKit.zonePulse(level, V.up(c, 1.2), core, k.num("radius") * 0.7, 0.5);
        RuneBoost.bloom(level, c, glow, core, 2.5); // 0.50.0 2D 강화 (아래 등급 터짐): 보호막이 펼쳐지는 순간 (부드러운 터짐)
        // 큰 금빛 방패 문장: 앞 1.8칸 · 머리 위쪽 (1인칭 화면 위쪽에 보이고, 3인칭에서는 몸 앞 위)
        Vec3 em = V.up(V.add(c, Kit.facingOf(sp), 1.8), 2.55);
        double ek = RuneKit.nearK(sp, em, 3, 0.45);
        RuneKit.rfx2(level, "shield", em, Opt.of("size", 0.8 * ek, "life", 0.75, "grow", 0.3, "fade", 0.55, "alpha", 0.95, "vel", RuneKit.bodyVel(sp)), glow,
                RuneKit.WHITE);
        RuneKit.rfx(level, "prop", em, Opt.of("size", 1.1 * ek, "life", 0.35, "cell", (double) RuneKit.RFX.get("glint")[0], "color", RuneKit.WHITE, "grow", 0.5,
                "fade", 0.6, "vel", RuneKit.bodyVel(sp)));
        SkillFx.sparks(level, V.up(c, 1), core, 8, 3);
        SkillFx.play(level, "beacon.activate", c, 1.6, 0.7);
        SkillFx.play(level, "random.orb", c, 0.8, 0.8);
        int flashed = 0;
        for (LivingEntity e : Kit.around(sp, c, k.num("hitRadius"))) {
            if (RuneKit.holyHit(sp, e, P, k.num("mult")) > 0) {
                RuneKit.knock(e, V.sub(e.position(), c), 0.6, 0.25);
                // 번쩍임 10명 · 밀려나는 적 앞의 방패 6명까지 (파티클 예산)
                if (flashed < 10) SkillFx.burst(level, Kit.bodyPoint(e), glow, 0.7, 0.22);
                if (flashed++ < 6) {
                    Vec3 d = V.flat(V.sub(e.position(), c));
                    Vec3 at = V.up(V.add(e.position(), d, -0.5), 1.1);
                    // 가까운 적 앞의 방패는 작게
                    RuneKit.rfx2(level, "shield", at, Opt.of("size", 0.45 * RuneKit.nearK(sp, at, 3, 0.35), "life", 0.3, "grow", 0.3, "fade", 0.6, "alpha", 0.85),
                            glow, RuneKit.WHITE);
                }
            }
        }
        return true;
    }

    /**
     * 정화의 빛: 나와 radius 칸 안 아군 회복 (최대 체력 × heal × 스탯 비례) · 나쁜 효과 제거, 가까운 적 (hitRadius) 신성 피해 —
     * 앞 위 하늘에 빛의 문장 (rpg:rfx_sun) 이 떠오르고 둘레에 빛줄기가 비처럼 내림 (쓴 사람 1.3칸 안은 비움)
     */
    static boolean purify(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double[][] cs = RuneKit.colorOf("holy");
        double[] glow = cs[0];
        double[] core = cs[1];
        double scaleS = supportScale(sp, r);
        double radius = k.num("radius");
        for (ServerPlayer pl : RuneKit.playersAround(level, c, radius, sp)) {
            double healed = RpgHealth.healBy(pl, RpgHealth.maxOf(pl) * k.num("heal") * scaleS);
            int n = RuneKit.cleanse(pl);
            if (healed > 0) DamageNumbers.show(pl, healed, DamageNumbers.Style.HEAL, "", 0);
            // 머리 위부터 내리쬐는 빛 (몸에 세우면 1인칭 카메라가 기둥 안)
            SkillFx.fx(level, "rpg:arrow_shot", V.up(pl.position(), 4.6), Opt.of("dir", V.UP, "speed", 0.0, "size", 2.6, "thick", 0.9, "life", 0.6, "color", glow,
                    "alpha", 0.75));
            SkillFx.fx(level, "rpg:arrow_shot", V.up(pl.position(), 4.6), Opt.of("dir", V.UP, "speed", 0.0, "size", 2.5, "thick", 0.25, "life", 0.5, "color",
                    RuneKit.WHITE, "alpha", 1.0));
            RuneKit.selfCue(pl, glow, core, pl.getUUID().equals(sp.getUUID()));
            if (n > 0) Notify.notify(pl, "§e정화 §7나쁜 효과 " + n + "개를 없앴습니다", 30);
        }
        // 빛의 문장: 앞 2.4칸 · 위 3칸에서 천천히 돎 (1.1초 — 1인칭 화면 위쪽에 보이고, 3인칭에서는 머리 앞 위)
        Vec3 vel = RuneKit.bodyVel(sp);
        Vec3 sunAt = V.up(V.add(c, Kit.facingOf(sp), 2.4), 3.0);
        RuneKit.rfx2(level, "sun", sunAt, Opt.of("size", 1.5 * RuneKit.nearK(sp, sunAt, 3.5, 0.5), "life", 1.1, "grow", 0.25, "fade", 0.45, "spinRate", 40.0,
                "alpha", 0.95, "vel", vel), glow, RuneKit.WHITE);
        SkillFx.rune(level, c, Opt.of("size", 2.4, "life", 1.0, "color", glow, "alpha", 0.85, "rate", 60.0, "grow", 0.3));
        RuneKit.zonePulse(level, c, glow, radius, 0.6);
        RuneBoost.bloom(level, c, glow, core, 3.0); // 0.50.0 2D 강화 (아래 등급 터짐): 정화의 빛이 내리는 순간 (부드러운 터짐)
        // 빛의 비: 둘레 (반지름 radius × 0.8 안) 에 내리꽂히는 빛줄기 12개 (3틱에 걸쳐)
        for (int i = 0; i < 12; i++) {
            final int ii = i;
            RuneKit.later(1 + ii % 3, () -> {
                double a = Kit.rand() * Math.PI * 2;
                double rr = 1.3 + Kit.rand() * (radius * 0.8 - 1.3);
                Vec3 p = new Vec3(c.x + Math.cos(a) * rr, c.y, c.z + Math.sin(a) * rr);
                SkillFx.fx(level, "rpg:arrow_shot", V.up(p, 5.5), Opt.of("dir", V.DOWN, "speed", 18.0, "size", 0.7, "thick", 0.12, "life", 0.28, "color",
                        ii % 2 == 1 ? RuneKit.WHITE : glow, "alpha", 0.9));
                RuneKit.later(6, () -> SkillFx.fx(level, "rpg:arrow_spark", V.up(p, 0.2), Opt.of("dir", V.UP, "speed", 1.6, "size", 0.18, "grow", 1.4, "life", 0.5,
                        "color", core, "alpha", 1.0)));
            });
        }
        for (int i = 0; i < 8; i++) {
            double a = (i / 8.0) * Math.PI * 2;
            Vec3 p = new Vec3(c.x + Math.cos(a) * 1.4, c.y + 0.2, c.z + Math.sin(a) * 1.4);
            SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.UP, "speed", 2.4, "size", 0.2, "grow", 1.4, "life", 0.9, "color", i % 2 == 1 ? core : glow,
                    "alpha", 1.0));
        }
        SkillFx.play(level, "random.levelup", c, 1.4, 0.5);
        SkillFx.play(level, "beacon.power", c, 1.4, 0.6);
        for (LivingEntity e : Kit.around(sp, c, k.num("hitRadius"))) {
            if (RuneKit.holyHit(sp, e, P, k.num("mult")) > 0) SkillFx.burst(level, Kit.bodyPoint(e), glow, 0.7, 0.22);
        }
        return true;
    }

    /** 심판의 망치: 대상 (range 칸) 에게 도는 금빛 망치 (rpg:rfx_prop 망치) → 신성 피해 + 기절 (stun), bounceRange 칸 안의 새 적에게 bounces 번 튐 */
    static boolean judgeHammer(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        LivingEntity first = Kit.pickTarget(sp, k.num("range"), k.num("cone"));
        if (first == null) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7" + k.text("range") + "칸 안에 대상이 없습니다");
            return false;
        }
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.colorOf("holy");
        Hammer h = new Hammer(sp, level, P, k, cs[0], cs[1]);
        h.thrown = new RuneHammer.Thrown(level, 2.1, cs[0], cs[1]); // 2026-11-12 3D 망치 (사용자 "망치 3d 연출" — 1.5칸은 멀리서 작아 2.1칸)
        h.done.add(first.getUUID());
        h.boom = r.grade(); // 0.50.0 2D 강화 (아래 등급 터짐): 처음 맞은 적에게 터짐 한 번
        h.fly(RuneKit.bowHand(sp), first, 0);
        SkillFx.play(level, "item.trident.throw", sp.position(), 0.8, 1);
        return true;
    }

    /** 심판의 망치 한 번 (judgeHammer 의 지역 상태 · fly): 이미 맞은 적 (다시 튀지 않게) */
    private static final class Hammer {
        final ServerPlayer sp;
        final ServerLevel level;
        final double P;
        final Obj k;
        final double[] glow;
        final double[] core;
        final Set<UUID> done = new HashSet<>();
        /** 0.50.0 2D 강화 (아래 등급 터짐): 처음 맞은 적에게 낼 터짐의 룬 등급 (낸 뒤 null — 한 번 던지기에 한 번) */
        String boom;
        /** 2026-11-12 3D 망치 (튈 때마다 같은 망치가 다음 적으로) */
        RuneHammer.Thrown thrown;

        Hammer(ServerPlayer sp, ServerLevel level, double P, Obj k, double[] glow, double[] core) {
            this.sp = sp;
            this.level = level;
            this.P = P;
            this.k = k;
            this.glow = glow;
            this.core = core;
        }

        /** from 에서 적 e 로 도는 망치가 날아감 (n = 튄 횟수 — 0 은 손에서 던진 것) */
        void fly(Vec3 from, LivingEntity e, int n) {
            Vec3 to = Kit.bodyPoint(e);
            Vec3 d = V.unit(V.sub(to, from));
            double len = V.len(V.sub(to, from));
            int ticks = (int) Math.max(1, Math.round(len / k.num("speed") * 20));
            // 2026-11-12 3D 망치가 세로로 돌며 날아감 (2D 망치 그림 대신 — 날아간 빛줄기는 그대로)
            if (thrown != null) thrown.fly(n == 0 ? V.add(from, d, Math.min(1.2, len * 0.3)) : from, to, ticks);
            RuneKit.streak(level, from, to, glow, 0.12, 0.3, 0.6, ticks / 40.0);
            RuneKit.later(ticks, () -> {
                if (sp.isRemoved() || e.isRemoved()) {
                    if (thrown != null) thrown.end(1);
                    return;
                }
                Vec3 at = Kit.bodyPoint(e);
                if (RuneKit.holyHit(sp, e, P, k.num("mult")) > 0) {
                    RuneKit.stun(e, (int) k.num("stun"), glow, true);
                    SkillFx.burst(level, at, glow, 1.2, 0.28);
                    RuneKit.rfx(level, "prop", V.add(at, d, -0.4), Opt.of("size", 0.9, "life", 0.3, "cell", (double) RuneKit.RFX.get("glint")[0], "spin", 45.0,
                            "color", RuneKit.WHITE, "grow", 0.5, "fade", 0.6));
                    SkillFx.ring(level, at, d, core, 0.8, 0.3, 0);
                    SkillFx.sparks(level, at, core, 8, 5);
                    if (boom != null) { // 0.50.0 2D 강화 (아래 등급 터짐): 처음 맞은 적의 발밑 (한 번 던지기에 한 번)
                        Vec3 foot = e.position();
                        RuneBoost.impact(level, new Vec3(foot.x, RuneKit.groundAt(level, foot, foot.y - 1.2), foot.z), glow, core, 2.0, boom);
                        boom = null;
                    }
                    SkillFx.play(level, "random.anvil_land", at, 1.4, 0.5);
                    SkillFx.play(level, "block.bell.hit", at, 1.2, 0.7);
                }
                if (n >= k.num("bounces")) {
                    if (thrown != null) thrown.end(2);
                    return;
                }
                Vec3 ep = e.position();
                List<LivingEntity> fresh = new ArrayList<>();
                for (LivingEntity x : Kit.around(sp, ep, k.num("bounceRange"), 3)) if (!done.contains(x.getUUID())) fresh.add(x);
                if (fresh.isEmpty()) {
                    if (thrown != null) thrown.end(2);
                    return;
                }
                LivingEntity next = RuneKit.nearestTo(fresh, ep).get(0);
                done.add(next.getUUID());
                fly(at, next, n + 1);
            });
        }
    }

    /**
     * 0.49.0 심판의 도약 (천벌 → 도약 — 사용자 "도약 스킬 같은것도 많이"): 조준한 곳 (range 칸) 으로 air 틱 동안 뛰어오름 — 머리 위로 든 금빛 망치 (3D),
     * 착지하며 망치로 내려찍음 (반지름 radius · 위력 × mult 신성 피해) → pillar 틱 뒤 그 자리에 빛기둥 (위력 × crush · 아군 회복 heal). 언데드 ×1.5 (holyHit).
     * 2026-11-12 픽셀 연출 (사용자 예시 사진 — "크루세이더 주변에 낙뢰": 바닥 균열 수십 개 · 금빛 불티 · 둘레의 낙뢰, vfx.Px): 날아가는 동안 착지할 곳에
     * 금빛 점 고리 둘이 돌며 하늘의 번쩍임이 커짐 → 착지: 충격파 · 가운데부터 바깥으로 퍼지는 균열 스물둘 · 불티 · 둘레에 낙뢰 여섯 (차례로) →
     * 빛기둥 (흰 심지 · 금빛 띠) · 다시 균열 · 떨어지는 불티
     */
    static boolean divineSmite(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.colorOf("holy");
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] gold = {1.0, 0.86, 0.45};
        Vec3 start = sp.position();
        Vec3 land = RuneKit.leapLand(sp, k.num("range"), 1.0);
        Vec3 f = V.hdist(land, start) > 0.3 ? V.flat(V.sub(land, start)) : V.flat(sp.getViewVector(1F));
        int air = (int) k.num("air");
        int pillar = (int) k.num("pillar");
        double radius = k.num("radius");
        // 2026-11-12 3D 금빛 망치 (사용자 "망치 3d 연출"): 머리 위로 들고 뛰어 → 착지하며 앞으로 내려찍어 머리가 땅에 박힘
        RuneHammer.Held held = RuneHammer.overhead(sp, level, f, air, 2.4, gold, RuneKit.WHITE, 40);
        double sky = 5; // 하늘의 번쩍임 높이
        Vfx.Fx fx = Vfx.at(level, land, air + pillar + 70);
        // 착지할 곳: 금빛 점 고리 둘 (반대로 돎) · 하늘의 번쩍임이 커짐
        Px.ring(fx, "dotring", V.up(land, 0.06), radius * 0.95, 2.5, glow, air + 6, 0);
        Px.ring(fx, "dotring", V.up(land, 0.07), radius * 0.55, -3.5, core, air + 6, 2);
        Px.Duo sun = Px.duo(fx, "star", V.up(land, sky), glow, core).size(0.6).frame(1);
        sun.to(null, 3.4, Math.max(1, air));
        sun.until(air + 3);
        SkillFx.play(level, "beacon.power", land, 1.4, 0.8);
        RuneKit.leap(sp, land, 2.4 + V.hdist(land, start) * 0.12, air, glow, gold, () -> {
            Vec3 c = sp.position();
            Vec3 g = VfxKit.ground(level, c);
            // 내려찍은 망치 (3D): 앞에 머리가 박힘
            RuneHammer.smash(held, V.add(c, f, 1.6), f, 3.0, 0.35, 9);
            // 충격파 · 번쩍임 · 균열 (가운데부터 바깥으로) · 불티
            Px.shock(fx, g, radius * 1.15, glow, core, 8, 0);
            Px.star(fx, V.up(g, 1.0), 4.0, glow, core, 0);
            Px.cracks(fx, g, 22, radius * 1.2, 2.4, glow, 7, 64, 0);
            Px.embers(fx, g, 26, radius * 0.8, 1.6, 1.4, gold, 32, 0);
            Px.impact(fx, g, radius, glow, core, r.grade(), 1);
            // 둘레의 낙뢰 여섯 (차례로)
            double a0 = Kit.rand() * 360;
            for (int i = 0; i < 6; i++) {
                Vec3 d = RuneKit.turn(f, a0 + i * 60 + Kit.rand() * 20);
                Vec3 p = VfxKit.ground(level, V.add(g, d, radius * (0.7 + Kit.rand() * 0.45)));
                Px.bolt(fx, p, 9.5, glow, RuneKit.WHITE, 4, true, 2 + i * 2);
            }
            for (int i = 0; i < 3; i++) RuneKit.later(2 + i * 4, () -> SkillFx.play(level, "item.trident.thunder", g, 0.7, 1.2));
            SkillFx.play(level, "mace.heavy_smash_ground", c, 1.0, 1.0);
            SkillFx.play(level, "item.trident.thunder", c, 1.6, 0.4);
            int shown = 0;
            for (LivingEntity e : Kit.around(sp, c, radius, 3)) {
                if (RuneKit.holyHit(sp, e, P, k.num("mult")) > 0) {
                    RuneKit.knock(e, V.sub(e.position(), c), 0.3, 0.25);
                    if (shown++ < 8) Px.star(fx, Kit.bodyPoint(e), 1.6, glow, core, 0);
                }
            }
            RuneKit.later(pillar, () -> pillar(sp, r, P, k, g, glow, core, gold, fx));
        });
        return true;
    }

    /** 심판의 도약 끝: 하늘에서 빛기둥이 내리꽂힘 (위력 × crush · 아군 회복 heal) — 2026-11-12 픽셀 빛기둥 · 충격파 · 새 균열 · 떨어지는 금빛 불티 */
    private static void pillar(ServerPlayer sp, Runes.Rune r, double P, Obj k, Vec3 g, double[] glow, double[] core, double[] gold, Vfx.Fx fx) {
        if (sp.isRemoved()) return;
        ServerLevel level = sp.level();
        double radius = k.num("radius");
        Px.pillar(fx, g, 13, radius * 0.9, glow, core, 14, 0);
        Px.shock(fx, g, radius * 1.05, glow, core, 9, 0);
        Px.star(fx, V.up(g, 1.2), 3.0, glow, core, 0);
        Px.cracks(fx, g, 10, radius * 0.9, 2.6, glow, 4, 40, 1);
        Px.embers(fx, V.up(g, 3.5), 18, radius * 0.7, 0.6, -2.6, gold, 30, 2);
        SkillFx.play(level, "item.trident.thunder", g, 1.4, 0.6);
        SkillFx.play(level, "beacon.activate", g, 1.2, 1);
        for (LivingEntity e : Kit.around(sp, g, radius, 4)) {
            if (RuneKit.holyHit(sp, e, P, k.num("crush")) > 0) Px.star(fx, Kit.bodyPoint(e), 1.6, glow, core, 0);
        }
        double scaleS = supportScale(sp, r);
        for (ServerPlayer pl : RuneKit.playersAround(level, g, radius, sp)) {
            double healed = RpgHealth.healBy(pl, RpgHealth.maxOf(pl) * k.num("heal") * scaleS);
            if (healed > 0) {
                DamageNumbers.show(pl, healed, DamageNumbers.Style.HEAL, "", 0);
                RuneKit.selfCue(pl, glow, core, false);
            }
        }
    }

    /**
     * 대천사 강림: hits × gap 틱 동안 등 뒤에 금빛 날개 (rpg:rfx_wings — 보는 방향에 수직인 면, 날갯짓). gap 틱마다 allyRadius 칸 안 아군 회복 · 보호막
     * (최대 cap), radius 칸 안 적 신성 피해. 눈앞에 금빛 깃털 (rpg:rfx_prop 깃털) 이 천천히 떨어짐 (1인칭). 죽으면 끝 (차원 이동으로는 끝나지 않음).
     * 초월 2차 강화: 천상의 금빛 하늘 (죽으면 걷힘) · 번쩍임 · 하늘의 성광 문양 (후광) · 발밑 문양 · 앞쪽 빛기둥 다섯
     */
    static boolean archangel(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int total = hits * gap;
        double[][] cs = RuneKit.colorOf("holy");
        double[] glow = cs[0];
        double[] core = cs[1];
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        double[] wingCol = {1.0, 0.9, 0.62};
        // 8단계 3돌: 강림 동안 나 · 아군 이동 속도 (allySpeed 단계 — 1 = 속도 I)
        int allySpeed = (int) k.numOr("allySpeed", 0);
        if (allySpeed > 0) {
            RuneKit.effect(sp, "speed", total, allySpeed - 1);
            for (ServerPlayer pl : RuneKit.playersAround(level0, c0, k.num("allyRadius"), sp)) RuneKit.effect(pl, "speed", total, allySpeed - 1);
        }
        SkillFx.fx(level0, "rpg:arrow_shot", V.up(c0, 7), Opt.of("dir", V.UP, "speed", 0.0, "size", 5.0, "thick", 1.4, "life", 0.8, "color", glow, "alpha", 0.85));
        SkillFx.fx(level0, "rpg:arrow_shot", V.up(c0, 7), Opt.of("dir", V.UP, "speed", 0.0, "size", 4.8, "thick", 0.4, "life", 0.7, "color", RuneKit.WHITE,
                "alpha", 1.0));
        SkillFx.burst(level0, V.up(c0, 1.2), glow, 2.4, 0.45);
        SkillFx.rune(level0, c0, Opt.of("size", 3.0, "life", 1.2, "color", glow, "alpha", 0.9, "rate", 40.0, "grow", 0.3));
        RuneKit.selfCue(sp, glow, core, true);
        RuneBoost.bloom(level0, c0, glow, core, 6.0); // 0.48.2 2D 강화: 강림하는 순간의 빛
        SkillFx.play(level0, "random.totem", c0, 1.3, 0.7);
        SkillFx.play(level0, "beacon.activate", c0, 0.9, 1);
        SkillFx.play(level0, "mob.allay.idle", c0, 0.8, 1);
        // 2차 강화 — 강림: 천상의 금빛 하늘 (죽으면 걷힘) · 흰 금빛 번쩍임 · 앞쪽 하늘의 거대한 성광 문양 (후광) · 발밑 문양 · 앞쪽 빛기둥 다섯 (2틱 뒤)
        RuneKit.ultSkyFor(sp, level0, c0, "archangel", 100, null);
        RuneKit.ultFlashFor(sp, level0, c0, "archangel", "flash", Opt.empty());
        Vec3 halo = RuneKit.skyPoint(sp, c0, 12, 22, 6, 22);
        RuneKit.skyPlate(sp, level0, "sigil", halo, 6.2, glow, RuneKit.WHITE, Opt.of("cell", (double) RuneKit.SIGIL.get("holy"), "life", 3.2, "spinRate", 18.0,
                "grow", 0.15, "alpha", 0.8, "fade", 0.4));
        RuneKit.domainSigil(level0, c0, 5.6, "holy", glow, RuneKit.WHITE, Opt.of("life", 2.2, "spinRate", 30.0, "grow", 0.2, "alpha", 0.8));
        RuneKit.later(2, () -> {
            if (!sp.isRemoved()) RuneKit.godRays(sp, sp.level(), sp.position(), 6.5, 5, glow, RuneKit.WHITE, Opt.of("arc", new double[] {-70, 70}, "h", 18.0,
                    "life", 1.3, "jitter", 10.0));
        });
        int alive = RuneKit.deathToken(sp); // 죽으면 끝 (날개 · 회복 · 보호막 · 피해 — 차원 이동 · 탈것 부르기로는 끝나지 않음)
        int[] feather = RuneKit.RFX.get("feather");
        for (int t = 0; t < total; t += 2) {
            final int tt = t;
            RuneKit.later(tt, () -> {
                if (!RuneKit.aliveSince(sp, alive)) return;
                ServerLevel level = sp.level();
                Vec3 c = sp.position();
                Vec3 f = Kit.facingOf(sp);
                // 날개: 등 뒤 0.5칸 · 어깨 높이, 날갯짓 (12틱마다 위 · 아래). 2틱마다 짧게 다시 그림 (몸을 따라옴 — 돌거나 뒷걸음질해도 눈앞을 오래 가리지 않게)
                Opt wo = Opt.of("dir", f, "size", 2.6, "life", RuneKit.BODY_LIFE, "cell", (double) ((tt / 12) % 2), "vel", RuneKit.bodyVel(sp));
                RuneBoost.aura(level, "wings", V.up(V.add(c, f, -0.55), 1.4), wo, wingCol, 1.35, 0.4); // 0.48.2 2D 강화: 날개 후광 (2.3 → 2.6)
                RuneKit.rfx2(level, "wings", V.up(V.add(c, f, -0.5), 1.4), wo, wingCol, RuneKit.WHITE, new int[] {0, RuneKit.WINGS_CORE}, 0.85);
                if (tt % 6 != 0) return;
                if (tt % 12 == 0) SkillFx.rune(level, V.up(c, 2.35), Opt.of("size", 0.4, "life", 0.65, "color", glow, "alpha", 0.95, "rate", 120.0)); // 후광
                // 눈앞에 천천히 떨어지는 금빛 깃털 (1인칭)
                if (tt % 12 == 6) {
                    for (int j = 0; j < 2; j++) {
                        Vec3 p = V.add(V.add(c, RuneKit.turn(f, (Kit.rand() - 0.5) * 80), 1.8 + Kit.rand() * 1.2), V.UP, 2.4 + Kit.rand() * 0.6);
                        RuneKit.rfx2(level, "prop", p, Opt.of("dir", V.DOWN, "speed", 0.7, "size", 0.17, "life", 1.4, "spin", Kit.rand() * 360, "spinRate", 70.0,
                                "fade", 0.3), j == 1 ? RuneKit.WHITE : wingCol, RuneKit.WHITE, feather, 0.6);
                    }
                }
            });
        }
        SkillFx.every(hits, gap, i -> {
            if (!RuneKit.aliveSince(sp, alive)) {
                // 죽으면 천상의 하늘도 걷힘 (자바는 다시 태어나면 새 플레이어 — 지금의 플레이어를 찾아서)
                ServerPlayer cur = RuneKit.player(level0, sp.getUUID());
                if (cur != null) Ult.endSky(List.of(cur), RuneKit.ultOf("archangel").strOrNull("fog"));
                return;
            }
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            RuneKit.zonePulse(level, c, i % 2 == 1 ? core : glow, k.num("radius"), 0.5);
            SkillFx.play(level, "beacon.ambient", c, 1.5, 0.6);
            // 앞쪽에 비스듬히 내리쬐는 햇살 두 줄기 (1인칭)
            Vec3 fv = V.flat(sp.getViewVector(1F));
            for (int j = 0; j < 2; j++) {
                Vec3 g0 = V.add(c, RuneKit.turn(fv, (j == 1 ? 1 : -1) * (15 + Kit.rand() * 25)), 3 + Kit.rand() * 2);
                RuneKit.streak(level, V.up(V.add(g0, fv, 1.5), 7), g0, core, 0.3, 0.6, 0.35, 0);
            }
            double scaleS = supportScale(sp, r);
            for (ServerPlayer pl : RuneKit.playersAround(level, c, k.num("allyRadius"), sp)) {
                double max = RpgHealth.maxOf(pl);
                double healed = RpgHealth.healBy(pl, max * k.num("heal") * scaleS);
                if (healed > 0) DamageNumbers.show(pl, healed, DamageNumbers.Style.HEAL, "", 0);
                RpgHealth.addShield(pl, max * k.num("shield") * scaleS, gap * 2 + 20, max * k.num("cap"));
            }
            for (LivingEntity e : Kit.around(sp, c, k.num("radius"))) {
                // 적이 많으면 빛은 10명까지 (파티클 예산 — 피해는 모두)
                if (RuneKit.holyHit(sp, e, P, k.num("mult")) > 0 && RuneKit.fxQuota("archangelHit", 10)) {
                    Vec3 at = Kit.bodyPoint(e);
                    SkillFx.fx(level, "rpg:arrow_shot", V.up(e.position(), 2.2), Opt.of("dir", V.UP, "speed", 0.0, "size", 1.4, "thick", 0.3, "life", 0.35, "color",
                            glow, "alpha", 0.9));
                    SkillFx.burst(level, at, glow, 0.7, 0.22);
                }
            }
        });
        RuneKit.later(total, () -> {
            // 날개가 깃털로 흩어짐 (애드온처럼 죽음과 상관없이 — 다시 태어났으면 지금의 플레이어 자리, 나갔으면 없음)
            ServerPlayer cur = RuneKit.player(level0, sp.getUUID());
            if (cur == null) return;
            ServerLevel level = cur.level();
            Vec3 c = cur.position();
            SkillFx.burst(level, V.up(c, 1.2), glow, 2.0, 0.4);
            for (int j = 0; j < 6; j++) {
                double a = Kit.rand() * Math.PI * 2;
                Vec3 p = new Vec3(c.x + Math.cos(a) * 1.2, c.y + 1.6 + Kit.rand(), c.z + Math.sin(a) * 1.2);
                RuneKit.rfx(level, "prop", p, Opt.of("dir", V.unit(new Vec3(Math.cos(a), 0.4, Math.sin(a))), "speed", 1.5, "size", 0.18, "life", 1.0, "spin",
                        Kit.rand() * 360, "spinRate", 120.0, "cell", (double) feather[0], "color", wingCol, "fade", 0.5));
            }
            SkillFx.play(level, "beacon.deactivate", c, 1.2, 0.8);
        });
        return true;
    }
}
