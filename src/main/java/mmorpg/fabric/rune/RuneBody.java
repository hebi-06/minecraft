package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 신체 강화 룬 (runes.js v2.24.0 "신체 강화" — 적용 스탯 = 네 스탯 중 가장 높은 것 × 1.0): 투지 · 강철 근육 · 재생 · 투신 · 초인 각성.
 * 몇 초 동안 몸이 강해짐 (받는 피해 감소 · 이동 속도 · 밀려나지 않음 · 회복 · 모든 스탯 +%) — 몸에 기운 (룬마다 accent 색).
 * 같은 룬을 다시 쓰면 처음부터, 다른 등급은 함께 (스탯은 가장 큰 것, 감소는 가장 센 것, 회복은 더함). 죽으면 모두 끝 (차원 이동으로는 끝나지 않음).
 * 매 틱 할 일 (끝 · 회복 · 연출) 과 상태 줄은 평타 강화와 함께 RuneStrike 의 buffTick · buffHud 가 부름 (tick · hud).
 */
final class RuneBody {
    private RuneBody() {}

    /** 계열 색 {빛, 심지} (RUNE.color.body) */
    private static final double[][] COLOR = RuneKit.colorOf("body");
    /** 반짝임 그림 칸 (RFX.glint[0]) */
    private static final double GLINT = RuneKit.RFX.get("glint")[0];

    /** 신체 강화 한 등급: 룬 설정 · 등급 · 끝나는 틱 · 위력 · 회복 배율 (쓸 때의 supportScale) · 다음 회복 틱 */
    private static final class BodyBuff {
        final Obj def;
        final String grade;
        final long until;
        final double P;
        final double scale;
        long nextAt;

        BodyBuff(Obj def, String grade, long until, double P, double scale, long nextAt) {
            this.def = def;
            this.grade = grade;
            this.until = until;
            this.P = P;
            this.scale = scale;
            this.nextAt = nextAt;
        }
    }

    /**
     * 신체 강화: 플레이어 → 등급 → 버프 (등급은 건 순서). 자바는 다시 태어나면 (엔드에서 돌아올 때도) 플레이어 엔티티가 바뀌므로
     * 엔티티 대신 uuid 를 두고 매 틱 지금 엔티티를 찾음 (죽으면 AFTER_DEATH 가 지움)
     */
    private static final Map<UUID, Map<String, BodyBuff>> BUFFS = new HashMap<>();

    static void register() {
        RuneCast.register("fightingSpirit", RuneBody::fightingSpirit);
        RuneCast.register("steelBody", RuneBody::steelBody);
        RuneCast.register("regenBody", RuneBody::regenBody);
        RuneCast.register("warGod", RuneBody::warGod);
        RuneCast.register("awakening", RuneBody::awakening);
        // 죽음: 신체 강화도 끝 (모든 스탯 · 밀려나지 않음 — 차원 이동 · 탈것으로는 끝나지 않음)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer sp) clearBody(sp);
        });
        RuneSystem.onForget(id -> BUFFS.remove(id));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BUFFS.clear());
    }

    /** 룬마다의 기운 색 (k.accent) */
    private static double[] accent(Obj k) {
        return Kit.rgb(k.get("accent"));
    }

    /** 지금 걸린 신체 강화 (끝난 것 빼고, 높은 등급 먼저) */
    private static List<BodyBuff> buffsOf(ServerPlayer sp) {
        Map<String, BodyBuff> b = BUFFS.get(sp.getUUID());
        if (b == null) return List.of();
        long now = Tasks.now();
        List<BodyBuff> out = new ArrayList<>();
        for (BodyBuff st : b.values()) if (st.until > now) out.add(st);
        out.sort(Comparator.comparingInt((BodyBuff s) -> RuneKit.GRADE_ORDER.indexOf(s.grade)).reversed());
        return out;
    }

    /** 모든 스탯 +: 걸린 신체 강화 중 가장 큰 것 (RpgStats — 바뀌면 최대 체력도 다시) */
    private static void refreshStatBuff(ServerPlayer sp) {
        double pct = 0;
        for (BodyBuff st : buffsOf(sp)) pct = Math.max(pct, st.def.numOr("statUp", 0));
        RpgStats.setBuff(sp, pct);
    }

    /** 신체 강화 걸기 (같은 등급은 처음부터): 받는 피해 감소 · 이동 속도 · 밀려나지 않음 · 모든 스탯은 지금, 회복은 buffTick 이 pulse 틱마다 */
    private static void startBody(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        long now = Tasks.now();
        int ticks = (int) k.num("ticks");
        Map<String, BodyBuff> b = BUFFS.computeIfAbsent(sp.getUUID(), id -> new LinkedHashMap<>());
        b.put(r.grade(), new BodyBuff(k, r.grade(), now + ticks, P, Runes.supportScale(RpgStats.effective(sp), r.family()),
                now + (long) k.numOr("pulse", 20)));
        if (k.truthy("reduce")) Guards.set(sp, ticks, k.num("reduce"), null, null);
        Double speed = k.numOrNull("speed");
        if (speed != null) RuneKit.effect(sp, "speed", ticks, speed.intValue());
        if (k.truthy("steadfast")) RpgCombat.setSteadfast(sp, ticks);
        refreshStatBuff(sp);
    }

    /** 신체 강화를 쓸 때: 몸에서 기운이 터짐 (발밑 룬 · 충격 고리 · 머리 위 빛기둥 · 몸의 기운) + 1인칭 눈앞 고리 · 글자 */
    private static void bodyCast(ServerPlayer sp, Runes.Rune r, Obj k, double big) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double[] col = accent(k);
        double[] core = COLOR[1];
        SkillFx.rune(level, c, Opt.of("size", 1.6 + big * 0.5, "life", 0.9, "color", col, "alpha", 0.9, "rate", 140.0, "grow", 0.3));
        SkillFx.shock(level, c, col, 1.1 + big * 0.5, 0.45);
        SkillFx.burst(level, V.up(c, 1), col, 1.5 + big * 0.5, 0.35);
        SkillFx.fx(level, "rpg:arrow_shot", V.up(c, 4.4 + big), Opt.of("dir", V.UP, "speed", 0.0, "size", 2.0 + big * 0.6, "thick", 0.7 + big * 0.2, "life", 0.55,
                "color", col, "alpha", 0.8));
        SkillFx.fx(level, "rpg:arrow_shot", V.up(c, 4.4 + big), Opt.of("dir", V.UP, "speed", 0.0, "size", 1.9 + big * 0.6, "thick", 0.22, "life", 0.5,
                "color", core, "alpha", 1.0));
        RuneKit.bodyAura(sp, col, Opt.of("n", 6, "size", 1.2 + big * 0.15, "alpha", 0.85, "life", 0.36, "force", true)); // 쓸 때의 큰 기운 (조금 길게)
        RuneKit.selfCue(sp, col, core, true);
        DamageNumbers.showFace(sp, "§6§l" + k.str("name") + "!", 2.2, -0.9, -0.35);
    }

    /** 신체 강화 동안 (짝수 틱 2틱마다 — 연출 횟수 n): 몸의 기운 (가장 높은 등급의 색, 짧게 · 몸을 따라옴) + 룬마다 덧붙임 + 1인칭 불티 (12틱마다) */
    private static void bodyFx(ServerPlayer sp, BodyBuff st, long n) {
        ServerLevel level = sp.level();
        Obj k = st.def;
        double[] col = accent(k);
        int gi = RuneKit.GRADE_ORDER.indexOf(st.grade);
        int[] auras = {2, 3, 3, 4, 5};
        RuneKit.bodyAura(sp, col, Opt.of("n", gi >= 0 && gi < auras.length ? auras[gi] : 3, "size", 0.95 + gi * 0.07, "alpha", 0.6 + gi * 0.05));
        if (n % 6 == 0) RuneKit.auraCue(sp, col, gi >= 3 ? 3 : 2);
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        String fn = k.str("fn");
        if (fn.equals("steelBody") && n % 6 == 2) {
            // 강철 비늘: 몸 옆에서 번쩍이는 쇳빛
            Vec3 d = RuneKit.turn(f, (n % 12 == 2 ? 1 : -1) * (80 + Kit.rand() * 30));
            RuneKit.rfx(level, "prop", V.up(V.add(c, d, 0.45), 0.7 + Kit.rand() * 0.8), Opt.of("size", 0.22, "life", 0.3, "cell", GLINT, "spin", Kit.rand() * 90,
                    "color", RuneKit.WHITE, "alpha", 0.9, "vel", RuneKit.bodyVel(sp)));
        } else if (fn.equals("regenBody") && n % 4 == 0) {
            double a = Kit.rand() * Math.PI * 2;
            SkillFx.fx(level, "rpg:arrow_spark", new Vec3(c.x + Math.cos(a) * 0.7, c.y + 0.3, c.z + Math.sin(a) * 0.7), Opt.of("dir", V.UP, "speed", 1.4,
                    "size", 0.14, "grow", 1.3, "life", 0.8, "color", col, "alpha", 1.0));
        } else if (fn.equals("awakening") && n % 4 == 2) {
            // 몸 둘레에 튀는 금빛 번개 (양옆)
            Vec3 d = RuneKit.turn(f, (n % 8 == 2 ? 1 : -1) * (95 + Kit.rand() * 40));
            Vec3 a = V.up(V.add(c, d, 0.55), 0.4 + Kit.rand() * 1.2);
            SkillFx.bolt(level, a, V.add(a, new Vec3((Kit.rand() - 0.5) * 0.8, 0.5 + Kit.rand() * 0.5, (Kit.rand() - 0.5) * 0.8)), col,
                    Opt.of("jag", 0.25, "thick", 0.06, "life", 0.18));
        }
    }

    /** 재생 · 초인 각성의 회복 한 번 (pulse 틱마다) */
    private static void bodyPulse(ServerPlayer sp, BodyBuff st) {
        Obj k = st.def;
        double[] col = accent(k);
        double healed = RpgHealth.healBy(sp, RpgHealth.maxOf(sp) * k.num("heal") * st.scale);
        if (healed > 0) DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        RuneKit.zonePulse(level, c, col, 1.3, 0.4);
        SkillFx.vanilla(level, "minecraft:heart_particle", V.up(c, 2.1));
        for (int i = 0; i < 3; i++) {
            double a = Kit.rand() * Math.PI * 2;
            SkillFx.fx(level, "rpg:arrow_spark", new Vec3(c.x + Math.cos(a) * 0.8, c.y + 0.2, c.z + Math.sin(a) * 0.8), Opt.of("dir", V.UP, "speed", 2.0,
                    "size", 0.18, "grow", 1.4, "life", 0.7, "color", i > 0 ? col : RuneKit.WHITE, "alpha", 1.0));
        }
        SkillFx.play(level, "random.orb", c, 1.4, 0.35);
    }

    /** 신체 강화가 모두 끝남: 기운이 흩어짐 */
    private static void bodyEnd(ServerPlayer sp) {
        ServerLevel level = sp.level();
        SkillFx.burst(level, V.up(sp.position(), 1), COLOR[0], 1.0, 0.3);
        SkillFx.play(level, "beacon.deactivate", sp.position(), 1.6, 0.4);
        if (Notify.actionbarFree(sp)) Notify.notify(sp, "§7신체 강화가 끝났습니다", 16);
    }

    /** 죽음: 신체 강화를 모두 없앰 (모든 스탯 · 밀려나지 않음도) */
    private static void clearBody(ServerPlayer sp) {
        BUFFS.remove(sp.getUUID());
        RpgCombat.clearSteadfast(sp.getUUID());
        RpgStats.setBuff(sp, 0);
    }

    /** 투지: 5초 동안 이동 속도 II · 받는 피해 -15% (주황 기운) */
    static boolean fightingSpirit(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        startBody(sp, r, P, k);
        bodyCast(sp, r, k, 0);
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        double[] col = accent(k);
        for (int i = 0; i < 4; i++) { // 뒤로 흐르는 바람
            RuneKit.streak(level, V.up(V.add(sp.position(), f, -0.6 - i * 0.5), 0.4 + i * 0.3), V.up(V.add(sp.position(), f, -1.4 - i * 0.6), 0.4 + i * 0.3),
                    col, 0.1, 0.3, 0.8, 0);
        }
        SkillFx.play(level, "mob.ravager.roar", sp.position(), 1.5, 0.45);
        SkillFx.play(level, "item.trident.riptide_1", sp.position(), 1.3, 0.6);
        return true;
    }

    /** 강철 근육: 7초 동안 받는 피해 -25% · 보스 · 몬스터 기술에 밀려나지 않음 (쇳빛 기운 · 강철 비늘) */
    static boolean steelBody(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        startBody(sp, r, P, k);
        bodyCast(sp, r, k, 0.4);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        for (int i = 0; i < 6; i++) {
            Vec3 d = RuneKit.turn(f, (i % 2 == 1 ? 1 : -1) * (70 + i * 14));
            RuneKit.rfx(level, "prop", V.up(V.add(c, d, 0.5), 0.5 + (i % 3) * 0.5), Opt.of("size", 0.3, "life", 0.4, "cell", GLINT, "spin", i * 30.0,
                    "color", RuneKit.WHITE, "grow", 0.4, "fade", 0.5));
        }
        SkillFx.burst(level, V.up(c, 1), RuneKit.STEEL, 1.8, 0.35);
        SkillFx.play(level, "armor.equip_iron", c, 0.8, 1);
        SkillFx.play(level, "random.anvil_use", c, 0.9, 0.6);
        return true;
    }

    /** 재생: 9초 동안 1초마다 최대 체력 × heal 회복 · 이동 속도 I (초록 기운) */
    static boolean regenBody(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        startBody(sp, r, P, k);
        bodyCast(sp, r, k, 0.4);
        ServerLevel level = sp.level();
        SkillFx.vanilla(level, "minecraft:heart_particle", V.up(sp.position(), 2.1));
        SkillFx.play(level, "beacon.power", sp.position(), 1.6, 0.6);
        SkillFx.play(level, "random.levelup", sp.position(), 1.8, 0.4);
        return true;
    }

    /** 투신: 12초 동안 모든 스탯 +15% · 받는 피해 -20% (핏빛 기운 · 땅이 울림) */
    static boolean warGod(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        startBody(sp, r, P, k);
        bodyCast(sp, r, k, 0.8);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double[] col = accent(k);
        SkillFx.fx(level, "rpg:ground_crack", V.up(c, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", 2.6, "life", 1.4, "color",
                new double[] {0.4, 0.06, 0.04}, "alpha", 0.9));
        for (int i = 0; i < 3; i++) {
            final int ii = i;
            RuneKit.later(i * 2, () -> SkillFx.shock(level, c, ii % 2 == 1 ? col : COLOR[0], 1.6 + ii * 0.9, 0.5));
        }
        SkillFx.play(level, "mob.ravager.roar", c, 0.9, 0.8);
        SkillFx.play(level, "mob.enderdragon.growl", c, 1.6, 0.35);
        SkillFx.shake(sp, 0.15, 0.25);
        return true;
    }

    /**
     * 초인 각성: 15초 동안 모든 스탯 +25% · 받는 피해 -30% · 1초마다 회복. 쓸 때 하늘에서 금빛 빛기둥이 내려와 몸에 닿고 (머리 위까지)
     * 주위 radius 칸에 충격파 (위력 × mult, 밀쳐냄 · 땅이 갈라지고 바위가 튐)
     */
    static boolean awakening(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        startBody(sp, r, P, k);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        String life = RuneKit.lifeToken(sp);
        double[] col = accent(k);
        double[] white = RuneKit.WHITE;
        double radius = k.num("radius");
        // 하늘에서 내려오는 빛기둥 (머리 위 2.6칸까지 — 1인칭 카메라가 기둥 안에 들어가지 않게)
        SkillFx.fx(level, "rpg:arrow_shot", V.up(c, 22), Opt.of("dir", V.DOWN, "speed", 17 / 0.25, "size", 3.0, "thick", 1.4, "life", 0.25, "color", col,
                "alpha", 0.95));
        SkillFx.fx(level, "rpg:arrow_shot", V.up(c, 22), Opt.of("dir", V.DOWN, "speed", 17 / 0.25, "size", 2.8, "thick", 0.5, "life", 0.25, "color", white,
                "alpha", 1.0));
        SkillFx.play(level, "beacon.activate", c, 0.8, 1);
        // 2차 강화 — 각성: 호박빛 하늘 · 충격파 때 번쩍임 → 성광 문양 · 하늘의 섬광 (1틱 뒤) → 앞쪽 빛기둥 넷 (3틱 뒤 — 충격파 틱의 파티클 예산)
        RuneKit.ultSkyFor(sp, level, c, "awakening", 76, null);
        RuneKit.later(5, () -> {
            if (!RuneKit.sameLife(sp, life)) return; // 죽음 · 차원 이동이면 충격파 없음
            Vec3 cc = sp.position();
            bodyCast(sp, r, k, 1.4);
            RuneKit.ultFlashFor(sp, level, cc, "awakening", "flash", Opt.empty());
            RuneKit.later(1, () -> {
                RuneKit.domainSigil(level, cc, 5.4, "holy", col, white, Opt.of("life", 1.4, "spinRate", 50.0, "grow", 0.3, "alpha", 0.85));
                if (!sp.isRemoved()) RuneKit.skyFlare(level, RuneKit.skyPoint(sp, cc, 12, 20, 6, 22), 3.6, col, white, Opt.of("life", 0.7));
            });
            RuneKit.later(3, () -> {
                if (RuneKit.sameLife(sp, life)) {
                    RuneKit.godRays(sp, sp.level(), sp.position(), 6, 4, col, white, Opt.of("arc", new double[] {-60, 60}, "h", 16.0, "life", 0.9));
                }
            });
            // 머리 위 3칸 (위를 봐도 화면을 덮지 않게)
            RuneKit.rfx(level, "prop", V.up(cc, 4.6), Opt.of("size", 0.9, "life", 0.5, "cell", GLINT, "color", white, "grow", 0.4, "fade", 0.6));
            for (int i = 0; i < 4; i++) {
                final int ii = i;
                RuneKit.later(i * 2, () -> RuneKit.zonePulse(level, cc, ii % 2 == 1 ? white : col, radius * (0.55 + 0.15 * ii), 0.5));
            }
            SkillFx.fx(level, "rpg:ground_crack", V.up(cc, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 1.3, "life", 1.6, "color",
                    new double[] {0.36, 0.2, 0.04}, "alpha", 0.9));
            // 먼지 · 튀는 바위: 몸에서 떨어진 둘레에서 (몸 가운데 먼지는 1인칭 카메라가 그 안에 들어가 화면 아래를 덮음)
            for (int i = 0; i < 5; i++) {
                Vec3 d = RuneKit.turn(new Vec3(1, 0, 0), i * 72 + Kit.rand() * 20);
                SkillFx.fx(level, "rpg:dust", V.add(V.up(cc, 0.6), d, radius * 0.45), Opt.of("size", 1.3, "life", 0.8, "color", new double[] {0.66, 0.54, 0.36},
                        "alpha", 0.6));
            }
            for (int i = 0; i < 6; i++) {
                double a = (i / 6.0) * Math.PI * 2 + Kit.rand() * 0.4;
                Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
                SkillFx.fx(level, "rpg:boulder", V.add(V.up(cc, 0.4), d, 1.3), Opt.of("dir", V.unit(new Vec3(d.x, 1.1, d.z)), "speed", 6 + Kit.rand() * 2,
                        "size", 0.26, "life", 0.7, "spin", Kit.rand() * 360, "color", RuneKit.ROCK, "alpha", 1.0));
            }
            for (int i = 0; i < 8; i++) {
                Vec3 dd = RuneKit.turn(new Vec3(1, 0, 0), i * 45 + Kit.rand() * 10);
                SkillFx.fx(level, "rpg:light_cut", V.add(V.up(cc, 0.08), dd, radius * 0.45), Opt.of("dir", dd, "size", radius * 0.4, "thick", 0.34, "life", 1.0,
                        "delay", 0.6, "color", col, "alpha", 0.95));
            }
            SkillFx.play(level, "item.trident.thunder", cc, 0.8, 1);
            SkillFx.play(level, "random.explode", cc, 0.9, 0.6);
            SkillFx.play(level, "mob.enderdragon.growl", cc, 1.2, 0.5);
            RuneKit.shakeAround(level, cc, 12, 0.35, 0.35);
            int shown = 0; // 적이 많으면 빛은 8마리까지 (모바일 파티클 예산 — 이 틱에 충격파 연출이 많음)
            for (LivingEntity e : Kit.around(sp, cc, radius)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                    RuneKit.knock(e, V.sub(e.position(), cc), 0.9, 0.4);
                    if (shown++ < 8) SkillFx.burst(level, Kit.bodyPoint(e), col, 0.8, 0.25);
                }
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 매 틱 · 상태 줄 (RuneStrike 의 buffTick · buffHud 가 부름)

    /**
     * 신체 강화 (buffTick 의 앞부분 — 매 틱, n = 돈 횟수 · m = 연출 횟수): 끝난 것 지우기 · 회복 · 이동 속도 지키기 · 연출 (짝수 틱).
     * 플레이어는 uuid 로 지금 엔티티를 찾음 — 나갔으면 지움 (스탯 버프 · 밀려나지 않음은 나갈 때 RpgStats · RpgCombat 이 지움)
     */
    static void tick(MinecraftServer server, long now, long n, long m) {
        for (UUID id : new ArrayList<>(BUFFS.keySet())) {
            Map<String, BodyBuff> b = BUFFS.get(id);
            if (b == null) continue;
            ServerPlayer pl = server.getPlayerList().getPlayer(id);
            if (pl == null) {
                BUFFS.remove(id);
                continue;
            }
            // 목록에 있지만 사라진 엔티티 (엔드 크레딧 — 다시 태어나면 새 엔티티로 이어짐): 이번 틱은 건너뜀. 죽음은 AFTER_DEATH 가 이미 지움
            if (pl.isRemoved()) continue;
            boolean changed = false;
            for (Iterator<BodyBuff> it = b.values().iterator(); it.hasNext(); ) {
                BodyBuff st = it.next();
                // 회복 먼저 (마지막 회복이 끝나는 틱과 겹쳐도 빠지지 않게: 9초 = 9번)
                if (st.def.truthy("heal") && now >= st.nextAt && st.nextAt <= st.until && !pl.isDeadOrDying()) {
                    st.nextAt += (long) st.def.numOr("pulse", 20);
                    bodyPulse(pl, st);
                }
                if (now >= st.until) {
                    it.remove();
                    changed = true;
                }
            }
            if (changed) refreshStatBuff(pl);
            if (b.isEmpty()) {
                BUFFS.remove(id);
                bodyEnd(pl);
                continue;
            }
            if (n % 2 == 0) keepSpeed(pl, now);
            if (n % 2 == 0 && !pl.isDeadOrDying()) {
                List<BodyBuff> list = buffsOf(pl);
                if (!list.isEmpty()) bodyFx(pl, list.get(0), m);
            }
        }
    }

    /**
     * 신체 강화의 이동 속도를 지킴 (2틱마다): 걸린 등급 중 가장 센 이동 속도가 없거나 약해졌으면 그 등급이 끝날 때까지 다시 걺.
     * (베드락은 투지 II 가 재생 I 을 덮어쓴 뒤 끝나면 I 을 되살리지 않음 — 다른 데서 건 더 센 효과는 건드리지 않음)
     */
    private static void keepSpeed(ServerPlayer sp, long now) {
        double want = -1;
        long until = now;
        for (BodyBuff st : buffsOf(sp)) {
            Double a = st.def.numOrNull("speed");
            if (a == null) continue;
            if (a > want || (a == want && st.until > until)) {
                want = a;
                until = st.until;
            }
        }
        if (want < 0 || until - now < 2) return;
        MobEffectInstance cur = sp.getEffect(MobEffects.SPEED);
        if (cur != null && cur.getAmplifier() >= want) return;
        RuneKit.effect(sp, "speed", (int) (until - now), (int) want);
    }

    /** 상태 줄의 신체 강화 (buffHud 의 앞부분): "§6투지 §73.5초" */
    static void hud(ServerPlayer sp, long now, List<String> out) {
        for (BodyBuff st : buffsOf(sp)) out.add("§6" + st.def.str("name") + " §7" + Js.toFixed(Math.max(0, (st.until - now) / 20.0), 1) + "초");
    }
}
