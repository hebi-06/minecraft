package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 사신 룬 (0.12.6 — 자바 전용, 낫을 든 동안만 씀 · 민첩 1.0 + 마력 0.5): 피의 초승달 컨셉 — 핏빛 초승달 · 출혈 · 흡혈.
 * 피의 초승달 (날아가는 초승달 · 관통 · 출혈) · 핏빛 수확 (두 번 크게 휘두름 · 흡혈) · 피의 회오리 (초승달을 두르고 돎 · 출혈) ·
 * 피의 낙인 (초승달 낙인 → 폭발 · 출혈 · 흡혈) · 적월 강림 (핏빛 달이 떠올라 칼날을 퍼붓고 떨어짐 — 핏빛 하늘).
 * 출혈은 맞은 뒤 dotGap 틱마다 dots 번 위력 × dot (몹 무적 시간을 피해 잡아 둠 — 룬의 마지막 타격 뒤부터), 흡혈은 한 번에 적마다 최대 체력 × heal (healMax 명까지).
 */
final class RuneReaper {
    private RuneReaper() {}

    /** 짙은 피 (아우라 · 바닥) */
    private static final double[] BLOOD = {0.5, 0.0, 0.05};
    /** 핏빛 알갱이 (빨간 먼지) */
    private static final DustParticleOptions DROP = DustParticleOptions.REDSTONE;

    static void register() {
        RuneCast.register("bloodCrescent", RuneReaper::bloodCrescent);
        RuneCast.register("bloodHarvest", RuneReaper::bloodHarvest);
        RuneCast.register("bloodWhirl", RuneReaper::bloodWhirl);
        RuneCast.register("bloodBrand", RuneReaper::bloodBrand);
        RuneCast.register("crimsonMoon", RuneReaper::crimsonMoon);
    }

    // ------------------------------------------------------------ 공용: 출혈 · 흡혈 · 핏방울

    /** 핏방울 (빨간 먼지 + 튀는 핏빛 알갱이) */
    private static void drops(ServerLevel level, Vec3 at, int n, double spread) {
        SkillFx.particle(level, DROP, at, n, spread, 0.02);
    }

    /**
     * 출혈: from 틱 뒤부터 dotGap 틱마다 dots 번 위력 × dot. 몹 무적 시간을 피해 틱을 잡아 둠 (Kit.reserveHit · hitAt — 다른 타격과 겹치면 미룸).
     * 쓴 사람이 죽거나 다른 삶이면 멈춤
     */
    static void bleed(ServerPlayer sp, Runes.Rune r, LivingEntity e, double P, Obj k, int from, String life) {
        double dot = k.numOr("dot", 0);
        int dots = (int) k.numOr("dots", 0);
        if (!(dot > 0) || dots <= 0) return;
        int gap = (int) Math.max(10, k.numOr("dotGap", 20));
        ServerLevel level = sp.level();
        long now = Tasks.now();
        for (int j = 0; j < dots; j++) {
            long at = Kit.reserveHit(sp, e, now + from + (long) j * gap);
            Kit.hitAt(sp, e, at, () -> RuneKit.sameLife(sp, life) && !e.isRemoved(), () -> {
                if (RuneKit.runeHit(sp, e, P, dot, r.stat()) <= 0) return;
                Vec3 b = Kit.bodyPoint(e);
                drops(level, V.up(b, 0.2), 6, 0.25);
                SkillFx.fx(level, "rpg:arrow_spark", V.up(b, 0.4), Opt.of("dir", V.DOWN, "speed", 1.4, "size", 0.12, "grow", 0.6, "life", 0.45, "color",
                        RuneKit.colorOf("reaper")[0], "alpha", 0.95));
            });
        }
    }

    /** 흡혈: 적 n 명 (healMax 까지) × 최대 체력 × heal × 회복 배율, 피가 적들에게서 나에게로 흘러듦 */
    private static void lifesteal(ServerPlayer sp, Runes.Rune r, Obj k, List<LivingEntity> from) {
        if (from.isEmpty() || !k.truthy("heal")) return;
        ServerLevel level = sp.level();
        double[] glow = RuneKit.colorOf("reaper")[0];
        int n = Math.min(from.size(), (int) k.numOr("healMax", 1));
        Vec3 to = V.up(sp.position(), 1.0);
        for (int i = 0; i < Math.min(n, 6); i++) {
            Vec3 at = Kit.bodyPoint(from.get(i));
            for (int j = 0; j < 3; j++) {
                Vec3 p = V.add(at, new Vec3((Kit.rand() - 0.5) * 0.6, (Kit.rand() - 0.3) * 0.6, (Kit.rand() - 0.5) * 0.6));
                Vec3 d = V.sub(to, p);
                SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.unit(d), "speed", V.len(d) / 0.4, "size", 0.16, "grow", 0.5, "life", 0.4, "color", glow,
                        "alpha", 1.0));
            }
        }
        double healed = RpgHealth.healBy(sp, RpgHealth.maxOf(sp) * k.num("heal") * n * Runes.supportScale(RpgStats.effective(sp), r.family()));
        if (healed > 0) {
            DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
            RuneKit.later(8, () -> {
                if (!sp.isRemoved()) RuneKit.selfCue(sp, glow, BLOOD, false);
            });
            SkillFx.play(level, "mob.warden.heartbeat", sp.position(), 1.3, 0.8);
        }
    }

    /** 맞은 자리: 핏빛 베인 자국 · 작은 초승달 · 핏방울 */
    private static void cut(ServerPlayer sp, ServerLevel level, LivingEntity e, double[] glow, double[] core, double size) {
        Vec3 at = Kit.bodyPoint(e);
        Vec3 v = V.unit(V.sub(at, sp.getEyePosition()));
        SkillFx.cutMark(level, at, v, 60 + Kit.rand() * 60, 0.1, glow, core, size);
        Vec3 wv = V.add(at, v, -0.3);
        RuneKit.rfx2(level, "wave", wv, Opt.of("size", 0.7 * size * RuneKit.nearK(sp, wv, 3, 0.4), "life", 0.26, "spin", -70 + Kit.rand() * 40, "grow", 0.3,
                "fade", 0.5, "alpha", 0.95), glow, core);
        drops(level, at, 8, 0.3);
    }

    // ------------------------------------------------------------ 일반: 피의 초승달

    /**
     * 피의 초승달: 낫을 가로로 크게 휘둘러 핏빛 초승달 (rpg:rfx_wave) 을 앞으로 날림 — range 칸 (벽 앞까지) 을 speed 칸/초로 날아가며 폭 width 칸의 적을 꿰뚫고
     * (위력 × mult), 맞은 적은 출혈. 지나간 자리에 핏방울
     */
    static boolean bloodCrescent(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.colorOf("reaper");
        double[] glow = cs[0];
        double[] core = cs[1];
        String life = RuneKit.lifeToken(sp);
        Vec3 eye = sp.getEyePosition();
        Vec3 view = sp.getViewVector(1F);
        Vec3 f = V.flat(view);
        Vec3 d = V.unit(new Vec3(f.x, Math.max(-0.35, Math.min(0.35, view.y)), f.z));
        Vec3 from = V.add(V.up(sp.position(), 1.1), f, 0.7);
        double len = Math.max(2, RuneKit.rayLength(level, from, d, k.num("range")));
        double speed = k.num("speed");
        int ticks = Math.max(1, (int) Math.round(len / speed * 20));
        // 낫을 가로로 휘두름 (왼쪽 → 오른쪽) + 핏빛 기운
        SkillFx.arc(level, V.up(V.add(eye, f, 1.3), -0.25), f, Opt.of("shape", "heavy", "flip", true, "size", 2.2, "life", 0.22, "speed", 2.0, "spin", 12.0, "color",
                glow, "aura", BLOOD));
        // 날아가는 초승달 (손 앞 1.2칸부터 — 1인칭 화면을 덮지 않게) + 뒤를 잇는 얇은 초승달
        RuneKit.rfx2(level, "wave", V.add(from, d, 1.2), Opt.of("dir", d, "speed", speed, "size", 1.9, "life", Math.max(0.05, (len - 1.2) / speed), "fade", 0.2,
                "spin", 0.0, "alpha", 1.0), glow, core);
        RuneKit.later(2, () -> RuneKit.rfx2(level, "wave", V.add(from, d, 1.0), Opt.of("dir", d, "speed", speed, "size", 1.5, "life", Math.max(0.05,
                (len - 1.0) / speed), "fade", 0.4, "alpha", 0.55), BLOOD, glow));
        for (int t = 2; t <= ticks; t += 2) {
            final double s = Math.min(len, speed * t / 20.0);
            RuneKit.later(t, () -> {
                Vec3 p = V.add(from, d, s);
                drops(level, V.up(p, -0.2), 3, 0.25);
            });
        }
        SkillFx.play(level, "entity.player.attack.sweep", sp.position(), 0.6, 1.0);
        SkillFx.play(level, "item.trident.throw", sp.position(), 0.6, 0.8);
        SkillFx.play(level, "mob.phantom.flap", sp.position(), 1.4, 0.6);
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, d, len, k.num("width"))) {
            LivingEntity e = h.e();
            int when = Math.max(0, (int) Math.round(h.t() / speed * 20));
            RuneKit.later(when, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) <= 0) return;
                cut(sp, level, e, glow, core, 1.0);
                SkillFx.play(level, "mob.phantom.bite", e.position(), 1.2, 0.6);
                bleed(sp, r, e, P, k, (int) Math.max(10, k.numOr("dotGap", 20)), life);
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 희귀: 핏빛 수확

    /**
     * 핏빛 수확: 낫을 앞으로 크게 두 번 휘두름 (hits 번, gap 틱 간격 — 오른쪽 → 왼쪽, 왼쪽 → 오른쪽) — 휘두를 때마다 거대한 핏빛 초승달 (rpg:rfx_wave) ·
     * 바닥의 베인 자국, 앞 radius 칸 · cone 도의 적 (위력 × mult), 벤 적의 피가 나에게로 (흡혈)
     */
    static boolean bloodHarvest(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        double[][] cs = RuneKit.colorOf("reaper");
        double[] glow = cs[0];
        double[] core = cs[1];
        String life = RuneKit.lifeToken(sp);
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        double radius = k.num("radius");
        double cone = k.num("cone");
        SkillFx.every(hits, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            Vec3 f = V.flat(sp.getViewVector(1F));
            boolean back = i % 2 == 1;
            // 거대한 낫질 (두 겹) + 앞의 핏빛 초승달이 휩쓸며 커짐
            SkillFx.arc(level, V.up(V.add(c, f, 1.7), 1.15), f, Opt.of("shape", "heavy", "flip", back, "size", 3.8, "life", 0.3, "speed", 1.5, "spin",
                    back ? 70.0 : -70.0, "color", glow, "aura", BLOOD));
            RuneKit.later(1, () -> SkillFx.arc(level, V.up(V.add(c, f, 2.2), 0.9), f, Opt.of("shape", "arc", "flip", back, "size", 3.0, "life", 0.24, "speed",
                    2.0, "spin", back ? 90.0 : -90.0, "color", BLOOD, "core", false, "alpha", 0.75)));
            Vec3 wv = V.up(V.add(c, f, 2.6), 1.0);
            RuneKit.rfx2(level, "wave", wv, Opt.of("dir", f, "speed", 5.0, "size", 2.8 * RuneKit.nearK(sp, wv, 3, 0.6), "life", 0.3, "grow", 0.35, "fade", 0.5,
                    "spin", back ? 25.0 : -25.0, "alpha", 0.95), glow, core);
            for (int j = -2; j <= 2; j++) {
                Vec3 dj = RuneKit.turn(f, j * cone / 6);
                SkillFx.fx(level, "rpg:light_cut", V.add(V.up(c, 0.08), dj, radius * 0.55), Opt.of("dir", dj, "size", radius * 0.45, "thick", 0.3, "life", 0.8,
                        "delay", 0.5, "color", glow, "alpha", 0.9));
            }
            RuneKit.zonePulse(level, V.add(c, f, 1.5), BLOOD, radius * 0.6, 0.35);
            SkillFx.play(level, "entity.player.attack.sweep", c, back ? 0.8 : 0.65, 1.0);
            SkillFx.play(level, "mace.smash_air", c, 0.7, 0.6);
            List<LivingEntity> got = new ArrayList<>();
            for (LivingEntity e : RuneKit.nearestTo(RuneKit.coneAround(sp, c, f, radius, cone), c)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) <= 0) continue;
                got.add(e);
                if (got.size() <= 6) cut(sp, level, e, glow, core, 1.1);
                else SkillFx.burst(level, Kit.bodyPoint(e), glow, 0.7, 0.25);
            }
            if (!got.isEmpty()) {
                SkillFx.shake(sp, 0.12, 0.12);
                lifesteal(sp, r, k, got);
            }
            SkillFx.sparks(level, V.up(V.add(c, f, 2.0), 1.0), core, 6, 4);
        });
        return true;
    }

    // ------------------------------------------------------------ 고귀: 피의 회오리

    /**
     * 피의 회오리: 핏빛 초승달 여섯을 몸에 두르고 hits 번 (gap 틱 간격) 돌며 둘레 radius 칸을 벰 (위력 × mult) — 몸 둘레의 초승달 참격 · 바닥 핏빛 소용돌이 ·
     * 바닥의 초승달 고리. 처음 벤 적마다 출혈 (회오리가 끝난 뒤부터)
     */
    static boolean bloodWhirl(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        double[][] cs = RuneKit.colorOf("reaper");
        double[] glow = cs[0];
        double[] core = cs[1];
        String life = RuneKit.lifeToken(sp);
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        double radius = k.num("radius");
        Map<UUID, LivingEntity> bled = new LinkedHashMap<>();
        RuneKit.selfCue(sp, glow, BLOOD, true);
        SkillFx.play(sp.level(), "mob.breeze.whirl", sp.position(), 0.8, 1.0);
        SkillFx.every(hits, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            // 몸 둘레의 초승달 참격 여섯 (도는 쪽으로) — 앞 (1인칭 화면) 의 것은 흐리게
            for (int j = 0; j < 6; j++) {
                double a = j / 6.0 * Math.PI * 2 + i * 0.6;
                Vec3 dir = new Vec3(Math.cos(a + Math.PI / 2), 0, Math.sin(a + Math.PI / 2));
                Vec3 at = new Vec3(c.x + Math.cos(a) * radius * 0.62, c.y + 1.0, c.z + Math.sin(a) * radius * 0.62);
                boolean ahead = V.dot(V.flat(V.sub(at, c)), Kit.facingOf(sp)) > 0.6;
                int jj = j;
                RuneKit.later(jj / 2, () -> SkillFx.arc(level, at, dir, Opt.of("shape", "heavy", "flip", i % 2 == 1, "size", 2.0, "life", 0.26, "speed", 2.4,
                        "spin", i % 2 == 0 ? 30.0 : -30.0, "color", glow, "aura", jj % 2 == 0 ? BLOOD : null, "alpha", ahead ? 0.5 : 1.0)));
            }
            // 도는 핏빛 초승달 (몸 둘레 — 3틱 동안)
            for (int s = 0; s < 3; s++) {
                final int ss = s;
                RuneKit.later(s, () -> {
                    if (sp.isRemoved()) return;
                    Vec3 cc = sp.position();
                    for (int j = 0; j < 3; j++) {
                        double a = i * 1.3 + ss * 0.7 + j * Math.PI * 2 / 3;
                        Vec3 pos = new Vec3(cc.x + Math.cos(a) * radius * 0.7, cc.y + 1.1, cc.z + Math.sin(a) * radius * 0.7);
                        boolean ahead = V.dot(V.flat(V.sub(pos, cc)), Kit.facingOf(sp)) > 0.6;
                        RuneKit.rfx2(sp.level(), "wave", pos, Opt.of("dir", new Vec3(-Math.sin(a), 0, Math.cos(a)), "size", 1.1, "life", 0.14, "spin", (double) (ss * 40),
                                "alpha", ahead ? 0.45 : 0.95), glow, core);
                    }
                });
            }
            SkillFx.fx(level, "rpg:whirlpool", V.up(c, 0.1), Opt.of("dir", V.ZERO, "size", radius * 0.95, "life", 0.45, "spin", i * 90.0, "speed", -560.0,
                    "color", BLOOD, "alpha", 0.6));
            RuneKit.crescent(level, V.up(c, 0.08), V.UP, radius * 1.05, glow, core, i * 110.0, 0.4);
            drops(level, V.up(c, 0.6), 10, radius * 0.4);
            SkillFx.play(level, "entity.player.attack.sweep", c, 0.75 + i * 0.1, 0.9);
            for (LivingEntity e : Kit.around(sp, c, radius, 2.5)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) <= 0) continue;
                Vec3 b = Kit.bodyPoint(e);
                SkillFx.fx(level, "rpg:slash_x", b, Opt.of("dir", V.flat(V.sub(e.position(), c)), "size", 0.9, "life", 0.22, "color", glow, "alpha", 1.0));
                drops(level, b, 5, 0.25);
                // 처음 벤 적: 회오리가 끝난 뒤부터 출혈
                if (bled.putIfAbsent(e.getUUID(), e) == null) bleed(sp, r, e, P, k, (hits - 1 - i) * gap + (int) Math.max(10, k.numOr("dotGap", 20)), life);
            }
            if (i == hits - 1) {
                SkillFx.burst(level, V.up(c, 1), glow, 2.2, 0.35);
                SkillFx.play(level, "mob.phantom.bite", c, 0.7, 0.8);
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 영웅: 피의 낙인

    /**
     * 피의 낙인: 앞 range 칸 · cone 도의 적 targets 명에게 핏빛 초승달을 던져 낙인 (머리 위 초승달 · 발밑 룬 · delay 동안 느려짐) → delay 틱 뒤 낙인이 터짐
     * (거대한 핏빛 X · 초승달 — 위력 × mult), 터진 적은 출혈, 피가 나에게로 (흡혈). 대상이 없으면 쓰지 않음 (재사용 대기 없음)
     */
    static boolean bloodBrand(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        List<LivingEntity> ts = RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), (int) k.num("targets"));
        if (ts.isEmpty()) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7앞쪽 " + k.text("range") + "칸 안에 대상이 없습니다");
            return false;
        }
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.colorOf("reaper");
        double[] glow = cs[0];
        double[] core = cs[1];
        String life = RuneKit.lifeToken(sp);
        int delay = (int) k.num("delay");
        Vec3 eye = sp.getEyePosition();
        Vec3 f = V.flat(sp.getViewVector(1F));
        SkillFx.arc(level, V.up(V.add(eye, f, 1.3), -0.2), f, Opt.of("shape", "heavy", "flip", false, "size", 2.4, "life", 0.24, "speed", 2.5, "spin", -40.0,
                "color", glow, "aura", BLOOD));
        SkillFx.play(level, "mob.evocation_illager.prepare_attack", sp.position(), 0.7, 0.8);
        SkillFx.play(level, "item.trident.throw", sp.position(), 0.5, 0.8);
        Vec3 hand = V.add(V.up(sp.position(), 1.2), f, 0.8);
        for (LivingEntity e : ts) {
            Vec3 b = Kit.bodyPoint(e);
            Vec3 d = V.sub(b, hand);
            double dist = Math.max(0.5, V.len(d));
            int fly = Math.max(2, Math.min(8, (int) Math.round(dist / 18 * 20)));
            Vec3 u = V.unit(d);
            // 날아가는 초승달 (손 앞 1.2칸부터)
            if (dist > 1.4) RuneKit.rfx2(level, "wave", V.add(hand, u, 1.2), Opt.of("dir", u, "speed", (dist - 1.2) / (fly / 20.0), "size", 0.9, "life", fly / 20.0,
                    "fade", 0.2), glow, core);
            RuneKit.later(fly, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                int left = Math.max(2, delay - fly);
                // 낙인: 머리 위 초승달 (터질 때까지) · 발밑 핏빛 룬 · 느려짐
                RuneKit.crescent(level, RuneKit.overHead(e, 0.45), RuneKit.towardEye(sp, Kit.bodyPoint(e)), 0.6, glow, core, -30, left / 20.0 + 0.05);
                SkillFx.rune(level, e.position(), Opt.of("size", 1.3, "life", left / 20.0 + 0.1, "color", glow, "alpha", 0.85, "rate", 150.0, "progress", 1.0,
                        "grow", 0.2));
                RuneKit.effect(e, "slowness", left + 6, 1);
                drops(level, Kit.bodyPoint(e), 6, 0.3);
                SkillFx.play(level, "mob.phantom.bite", e.position(), 1.5, 0.5);
            });
        }
        // 낙인이 터짐
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            List<LivingEntity> got = new ArrayList<>();
            for (LivingEntity e : ts) {
                if (e.isRemoved()) continue;
                Vec3 b = Kit.bodyPoint(e);
                Vec3 v = V.unit(V.sub(b, sp.getEyePosition()));
                SkillFx.fx(level, "rpg:slash_x", V.add(b, v, -0.4), Opt.of("dir", v, "size", 2.2, "life", 0.35, "color", glow, "alpha", 1.0));
                Vec3 wv = V.add(b, v, -0.5);
                RuneKit.rfx2(level, "wave", wv, Opt.of("size", 1.6 * RuneKit.nearK(sp, wv, 3, 0.5), "life", 0.32, "spin", -45.0, "grow", 0.5, "fade", 0.6),
                        glow, core);
                SkillFx.burst(level, b, glow, 1.6, 0.35);
                SkillFx.shock(level, e.position(), BLOOD, 2.0, 0.4);
                drops(level, b, 14, 0.45);
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                    got.add(e);
                    bleed(sp, r, e, P, k, (int) Math.max(10, k.numOr("dotGap", 20)), life);
                }
            }
            SkillFx.play(level, "item.trident.thunder", sp.position(), 1.6, 0.4);
            SkillFx.play(level, "mob.wither.shoot", sp.position(), 0.6, 0.6);
            if (!got.isEmpty()) {
                SkillFx.shake(sp, 0.2, 0.2);
                lifesteal(sp, r, k, got);
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 초월: 적월 강림

    /** 달이 떨어지는 틱 수 */
    private static final int FALL = 6;

    /**
     * 적월 강림: 조준한 곳 (range 칸) 위 하늘에 거대한 핏빛 초승달 (rpg:rfx_moon) 이 떠오름 (delay 틱 — 핏빛 하늘 · 검붉은 번쩍임 · 땅의 칼날 문양) →
     * gap 틱마다 hits 번 달에서 핏빛 칼날 (초승달) 이 radius 칸 안의 적마다 쏟아짐 (위력 × mult) → 달이 떨어져 땅을 거대한 초승달로 가름 (위력 × crush) ·
     * 피가 나에게로 (흡혈). 3돌부터 추락 뒤 출혈 (dot × dots). 대상이 없어도 씀
     */
    static boolean crimsonMoon(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level0 = sp.level();
        double[][] cs = RuneKit.colorOf("reaper");
        double[] glow = cs[0];
        double[] core = cs[1];
        String life = RuneKit.lifeToken(sp);
        int delay = (int) k.num("delay");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        double radius = k.num("radius");
        Vec3 g = RuneKit.groundAim(sp, k.num("range"));
        int rainEnd = delay + (hits - 1) * gap;
        int fallAt = rainEnd + 14;
        int total = fallAt + FALL;
        // 핏빛 하늘 (끝까지) · 검붉은 번쩍임 · 땅의 칼날 문양 · 발밑 룬
        RuneKit.ultSkyFor(sp, level0, g, "crimsonMoon", total + 30, null);
        RuneKit.ultFlashFor(sp, level0, g, "crimsonMoon", "flash", Opt.empty());
        RuneKit.domainSigil(level0, g, radius, "blade", glow, BLOOD, Opt.of("life", total / 20.0 + 0.3, "spinRate", 30.0, "grow", 0.25, "alpha", 0.85,
                "fade", 0.2));
        SkillFx.rune(level0, g, Opt.of("size", radius * 0.55, "life", total / 20.0, "color", glow, "alpha", 0.8, "rate", -60.0, "progress", 1.0));
        RuneKit.selfCue(sp, glow, BLOOD, true);
        SkillFx.play(level0, "mob.wither.spawn", sp.position(), 1.4, 0.5);
        SkillFx.play(level0, "mob.enderdragon.growl", g, 0.6, 0.8);
        SkillFx.shake(sp, 0.15, 0.4);
        Vec3 moonAt = RuneKit.skyPoint(sp, g, 6, 24, 9, 20);
        // 달이 떠오름 (아래에서 솟으며 커짐 — 2틱마다 한 장)
        for (int t = 0; t < delay; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                double q = tt / (double) delay;
                RuneBoost.rfx2a(level0, "moon", V.up(moonAt, -4 * (1 - q)), Opt.of("size", (2.2 + 3.0 * q) * 1.15, "life", 0.16, "spin", -20.0, "alpha",
                        0.6 + 0.4 * q), glow, core);
            });
        }
        // 떠 있는 달 (비가 끝날 때까지) + 달무리
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneBoost.rfx2a(level0, "moon", moonAt, Opt.of("size", 6.0, "life", (fallAt - delay) / 20.0, "spin", -20.0, "spinRate", 4.0, "alpha", 1.0, "fade",
                    0.1), glow, core);
            RuneKit.skyFlare(level0, moonAt, 4.2, BLOOD, glow, Opt.of("life", (fallAt - delay) / 20.0));
            SkillFx.play(level0, "mob.warden.heartbeat", g, 0.7, 1.0);
        });
        // 핏빛 칼날이 쏟아짐: 적마다 달에서 초승달이 4틱 만에 날아와 꽂힘 (+ 둘레 빈 땅에도 둘)
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            RuneKit.later(delay + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                if (level != level0) return;
                List<LivingEntity> ts = Kit.around(sp, g, radius, 4);
                List<Vec3> spots = new ArrayList<>();
                for (int j = 0; j < Math.min(8, ts.size()); j++) spots.add(Kit.bodyPoint(ts.get(j)));
                for (int j = 0; j < 2; j++) {
                    double a = Kit.rand() * Math.PI * 2;
                    double rr = Math.sqrt(Kit.rand()) * radius;
                    spots.add(new Vec3(g.x + Math.cos(a) * rr, g.y + 0.3, g.z + Math.sin(a) * rr));
                }
                for (Vec3 p : spots) {
                    Vec3 d = V.sub(p, moonAt);
                    Vec3 u = V.unit(d);
                    RuneKit.rfx2(level, "wave", moonAt, Opt.of("dir", u, "speed", V.len(d) / 0.2, "size", 1.3, "life", 0.2, "spin", ii * 37.0), glow, core);
                }
                SkillFx.play(level, "mob.phantom.flap", moonAt, 0.6, 0.6);
                RuneKit.later(4, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    for (Vec3 p : spots) {
                        SkillFx.fx(level, "rpg:light_cut", new Vec3(p.x, g.y + 0.08, p.z), Opt.of("dir", RuneKit.turn(new Vec3(1, 0, 0), Kit.rand() * 180), "size",
                                1.4, "thick", 0.3, "life", 0.7, "delay", 0.4, "color", glow, "alpha", 0.9));
                        drops(level, p, 6, 0.3);
                    }
                    int n = 0;
                    for (LivingEntity e : ts) {
                        if (e.isRemoved()) continue;
                        if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0 && n++ < 8) {
                            SkillFx.fx(level, "rpg:slash_x", Kit.bodyPoint(e), Opt.of("dir", V.DOWN, "size", 1.1, "life", 0.22, "color", glow, "alpha", 1.0));
                        }
                    }
                    SkillFx.play(level, "entity.player.attack.sweep", g, 0.6 + ii * 0.05, 0.8);
                });
            });
        }
        // 달이 떨어짐 → 땅을 거대한 초승달로 가름 (추락) · 흡혈 · (3돌부터) 출혈
        RuneKit.later(fallAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 d = V.sub(V.up(g, 1.0), moonAt);
            RuneBoost.rfx2a(level0, "moon", moonAt, Opt.of("dir", V.unit(d), "speed", V.len(d) / (FALL / 20.0), "size", 6.0, "life", FALL / 20.0, "grow",
                    0.3, "spin", -20.0), glow, core);
            SkillFx.play(level0, "mob.enderdragon.flap", moonAt, 0.5, 1.0);
            SkillFx.play(level0, "mob.wither.shoot", g, 0.5, 1.0);
        });
        RuneKit.later(total, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            if (level != level0) return;
            RuneKit.ultFlashFor(sp, level, g, "crimsonMoon", "flash", Opt.empty());
            RuneBoost.impact(level, g, glow, core, radius * 1.1); // 0.48.2 2D 강화: 달이 떨어진 자리의 터짐
            RuneKit.crescent(level, V.up(g, 0.1), V.UP, radius * 1.25, glow, core, Kit.rand() * 360, 0.7);
            RuneKit.novaRing(level, g, 1.5, radius * 0.9, glow, BLOOD, Opt.of("life", 0.6));
            RuneKit.glowCrack(level, g, radius, glow, BLOOD, Opt.of("life", 1.4));
            SkillFx.fx(level, "rpg:ground_crack", V.up(g, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 1.1, "life", 1.5, "color", BLOOD,
                    "alpha", 0.95));
            SkillFx.burst(level, V.up(g, 1.2), glow, 3.4, 0.45);
            SkillFx.shock(level, g, glow, radius * 0.9, 0.5);
            drops(level, V.up(g, 0.8), 40, radius * 0.4);
            RuneKit.shakeAround(level, g, 14, 0.45, 0.4);
            SkillFx.play(level, "random.explode", g, 0.6, 1.0);
            SkillFx.play(level, "mace.heavy_smash_ground", g, 0.7, 1.0);
            SkillFx.play(level, "mob.ravager.roar", g, 0.6, 0.6);
            List<LivingEntity> got = new ArrayList<>();
            for (LivingEntity e : Kit.around(sp, g, radius, 4)) {
                if (RuneKit.runeHit(sp, e, P, k.num("crush"), r.stat()) <= 0) continue;
                got.add(e);
                RuneKit.knock(e, V.sub(e.position(), g), 0.35, 0.3);
                if (got.size() <= 8) SkillFx.fx(level, "rpg:slash_x", Kit.bodyPoint(e), Opt.of("dir", V.flat(V.sub(e.position(), g)), "size", 1.6, "life", 0.3,
                        "color", glow, "alpha", 1.0));
                bleed(sp, r, e, P, k, (int) Math.max(10, k.numOr("dotGap", 20)), life);
            }
            lifesteal(sp, r, k, got);
        });
        return true;
    }
}
