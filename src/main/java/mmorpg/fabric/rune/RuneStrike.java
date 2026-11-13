package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Combat;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Runes;
import mmorpg.core.SwingLooks;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.SwingArea;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.motion.MotionSystem;
import mmorpg.fabric.motion.SwingLook;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 평타 강화 룬 (runes.js v2.24.0 "평타 강화" — 적용 스탯 = 네 스탯 중 가장 높은 것 × 1.0): 날 세우기 · 연타 · 가르기 · 피의 칼날 · 검신 강림.
 * ticks 틱 동안 (hits 가 있으면 그 번까지) 평타 (근접 · 활 · 완드) 에 위력 × bonus 를 더함 (평타와 한 번에 — 몹 무적 시간에 막히지 않음, RpgCombat 훅)
 * + 룬마다 덧붙임 (잔상 · 부채꼴 · 흡혈 · 검기). 같은 등급은 처음부터, 다른 등급은 함께 (더한 피해를 더함). 죽음 · 차원 이동이면 끝 (삶 표시).
 * 신체 강화 (RuneBody) 와 함께 쓰는 매 틱 할 일 (buffTick) · 상태 줄 (buffHud) 도 여기서 한 번만 등록.
 * 0.41.0 (사용자 "평타 강화 스킬은 평타 색이 바뀌고 범위가 더 넓어지는걸로" — 고른 것: 룬마다 다르게 · 휘두를 때마다 1번): 걸린 동안 평타 모습 (SwingLook —
 * 궤적 색 · 참격 범위 × 룬마다 배율, rules_more SWING_LOOK), '평타 N번' 은 근접 평타를 휘두를 때마다 한 번 (같은 휘두르기의 조준 · 범위 적은 모두 더한 피해를 받고
 * 덧붙임은 한 번 — 마지막 번의 휘두르기는 범위 적까지 더한 피해), 가르기 부채꼴은 같은 휘두르기에 이미 맞은 적을 다시 베지 않음.
 */
public final class RuneStrike {
    private RuneStrike() {}

    /** 더한 피해의 숫자 앞 표시 (mark) · 훅 구분 (family) */
    private static final Obj RUNE_STRIKE = Rules.get().obj("RUNE_STRIKE");
    /** 반짝임 그림 칸 (RFX.glint[0]) */
    private static final double GLINT = RuneKit.RFX.get("glint")[0];

    /** 평타 강화 한 등급: 룬 설정 · 등급 · 끝나는 틱 · 남은 번 (hits 가 없으면 무한) · 위력 · 삶 표시 */
    private static final class StrikeBuff {
        final Obj def;
        final String grade;
        final long until;
        double left;
        final double P;
        final String token;
        /** 0.41.0 마지막으로 센 휘두르기의 틱 (SwingArea.swingTick — 없으면 -1): 같은 휘두르기의 다른 적은 다시 세지 않음 */
        long swing = -1;

        StrikeBuff(Obj def, String grade, long until, double left, double P, String token) {
            this.def = def;
            this.grade = grade;
            this.until = until;
            this.left = left;
            this.P = P;
            this.token = token;
        }
    }

    /** 평타 강화: 플레이어 → 등급 → 버프 (등급은 건 순서). 다시 태어나면 엔티티가 바뀌므로 uuid 로 (죽으면 삶 표시가 달라 끝남) */
    private static final Map<UUID, Map<String, StrikeBuff>> BUFFS = new HashMap<>();
    /** buffTick 이 돈 횟수 (매 틱 — 신체 강화 연출은 짝수 틱, 평타 강화 연출은 홀수 틱: 한 틱에 몰리지 않게) */
    private static long buffTicks;

    static void register() {
        RuneCast.register("edgeHone", RuneStrike::edgeHone);
        RuneCast.register("flurry", RuneStrike::flurry);
        RuneCast.register("cleave", RuneStrike::cleave);
        RuneCast.register("bloodBlade", RuneStrike::bloodBlade);
        RuneCast.register("swordGod", RuneStrike::swordGod);
        // 평타 · 활 · 완드 피해에 더함 · 들어가면 한 번 쓰고 덧붙임 · 치명타 + · 활 · 완드 재사용 배율
        RpgCombat.addWeaponBonusHook(RuneStrike::strikeBonus);
        RpgCombat.addWeaponHitHook(RuneStrike::strikeLanded);
        RpgCombat.addCritBonusHook(RuneStrike::strikeCrit);
        RpgCombat.addRangedCooldownHook(RuneStrike::strikeRangedScale);
        // 신체 강화 · 평타 강화: 끝 · 회복 · 연출 (매 틱), 상태 줄 덧붙임 — 두 계열 함께 한 번만
        Notify.addHudSuffix(RuneStrike::buffHud);
        ServerTickEvents.END_SERVER_TICK.register(RuneStrike::buffTick);
        RuneSystem.onForget(id -> BUFFS.remove(id));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BUFFS.clear());
        // 0.41.0 평타 강화 모습: 걸린 평타 강화마다 그 룬 (fn) 의 모습 — 활 · 완드 평타 색에도
        SwingLook.addSource((sp, out) -> {
            for (StrikeBuff st : buffsOf(sp)) {
                SwingLooks.Look l = SwingLooks.of(st.def.strOrNull("fn"));
                if (l != null) out.add(new SwingLook.Active(l, st.until, true));
            }
        });
    }

    /** 룬마다의 빛 색 (k.accent) */
    private static double[] accent(Obj k) {
        return Kit.rgb(k.get("accent"));
    }

    /**
     * 평타 강화가 아직 있는지: 시간 · 남은 번 · 삶 표시 (죽음 · 차원 이동 · 탈것이면 끝). 0.41.0 마지막 번을 쓴 휘두르기는 끝날 때까지 (범위 적이 1틱 뒤에 맞음)
     */
    private static boolean strikeOn(ServerPlayer sp, StrikeBuff st) {
        if (st.until <= Tasks.now() || !st.token.equals(RuneKit.lifeToken(sp))) return false;
        return st.left > 0 || (st.swing >= 0 && st.swing == SwingArea.swingTick(sp));
    }

    /** 지금 걸린 평타 강화 (높은 등급 먼저) */
    private static List<StrikeBuff> buffsOf(ServerPlayer sp) {
        Map<String, StrikeBuff> b = BUFFS.get(sp.getUUID());
        if (b == null) return List.of();
        List<StrikeBuff> out = new ArrayList<>();
        for (StrikeBuff st : b.values()) if (strikeOn(sp, st)) out.add(st);
        out.sort(Comparator.comparingInt((StrikeBuff s) -> RuneKit.GRADE_ORDER.indexOf(s.grade)).reversed());
        return out;
    }

    /** 평타 강화 걸기 (같은 등급은 처음부터, 다른 등급은 함께 — 더한 피해를 더함) */
    private static void startStrike(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        Map<String, StrikeBuff> b = BUFFS.computeIfAbsent(sp.getUUID(), id -> new LinkedHashMap<>());
        b.put(r.grade(), new StrikeBuff(k, r.grade(), Tasks.now() + (long) k.num("ticks"), k.numOr("hits", Double.POSITIVE_INFINITY), P, RuneKit.lifeToken(sp)));
    }

    /** 평타 강화를 쓸 때: 무기를 든 손에 빛 (고리 · 반짝임) + 글자 */
    private static void strikeCast(ServerPlayer sp, Obj k) {
        ServerLevel level = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        double[] col = accent(k);
        SkillFx.ring(level, h.hand(), h.f(), col, 0.5, 0.3, 0);
        RuneKit.rfx(level, "prop", h.hand(), Opt.of("size", 0.3, "life", 0.35, "cell", GLINT, "spin", 20.0, "color", RuneKit.WHITE, "grow", 0.6, "fade", 0.6));
        SkillFx.sparks(level, h.hand(), col, 6, 3);
    }

    /** 날 세우기: 다음 평타 3번 (6초 안) 에 위력 × bonus — 칼날을 숫돌에 가는 불티 */
    static boolean edgeHone(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        startStrike(sp, r, P, k);
        strikeCast(sp, k);
        ServerLevel level = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        Vec3 hand = h.hand();
        Vec3 side = V.rightOf(h.f());
        // 손에서 칼날을 따라 그어지는 빛 + 튀는 불티 (1인칭 화면 오른쪽 아래)
        SkillFx.fx(level, "rpg:light_cut", V.up(V.add(hand, side, -0.15), 0.25), Opt.of("dir", V.unit(V.add(V.UP, side, -0.6)), "size", 0.45, "thick", 0.08,
                "life", 0.35, "delay", 0.1, "color", RuneKit.WHITE, "alpha", 1.0));
        for (int i = 0; i < 6; i++) {
            SkillFx.fx(level, "rpg:arrow_spark", hand, Opt.of("dir", V.unit(new Vec3(side.x + (Kit.rand() - 0.5), 0.6 + Kit.rand() * 0.6, side.z + (Kit.rand() - 0.5))),
                    "speed", 3.0, "size", 0.06, "life", 0.3, "color", new double[] {1.0, 0.85, 0.5}, "alpha", 1.0));
        }
        SkillFx.play(level, "random.anvil_use", sp.position(), 2.0, 0.3);
        SkillFx.play(level, "item.spear.attack_miss", sp.position(), 1.6, 0.6);
        return true;
    }

    /** 연타: 7초 동안 활 · 완드 재사용 × rangedCd, 근접 평타마다 잔상 (위력 × echo, 옆의 적 하나에게도) */
    static boolean flurry(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        startStrike(sp, r, P, k);
        strikeCast(sp, k);
        ServerLevel level = sp.level();
        Vec3 head = sp.getEyePosition();
        Vec3 f = V.flat(sp.getViewVector(1F));
        double[] col = accent(k);
        RuneKit.arc(level, V.up(V.add(head, f, 1.6), -0.3), f, Opt.of("shape", "thin", "flip", false, "size", 1.6, "life", 0.18, "speed", 4.0, "spin", -20.0,
                "color", col, "core", true));
        RuneKit.later(2, () -> {
            if (!sp.isRemoved()) RuneKit.arc(level, V.up(V.add(sp.getEyePosition(), f, 1.7), -0.35), f, Opt.of("shape", "thin", "flip", true, "size", 1.6,
                    "life", 0.18, "speed", 4.0, "spin", 20.0, "color", col, "core", true));
        });
        SkillFx.play(level, "mob.breeze.whirl", sp.position(), 1.6, 0.6);
        return true;
    }

    /** 가르기: 평타 9번 (9초 안) — 위력 × bonus, 근접은 앞 부채꼴 cleave 칸 · cone 도, 활 · 완드는 맞은 적 뒤 splash 칸의 다른 적도 벰 */
    static boolean cleave(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        startStrike(sp, r, P, k);
        strikeCast(sp, k);
        ServerLevel level = sp.level();
        Vec3 f = V.flat(sp.getViewVector(1F));
        RuneKit.waveCard(level, V.up(V.add(sp.position(), f, 2.4), 1.0), f, V.UP, Opt.of("size", 1.8, "life", 0.28, "grow", 0.4, "fade", 0.6), accent(k),
                RuneKit.WHITE);
        SkillFx.play(level, "item.spear.attack_miss", sp.position(), 0.9, 0.8);
        SkillFx.play(level, "random.anvil_use", sp.position(), 1.4, 0.3);
        return true;
    }

    /** 피의 칼날: 평타 12번 (12초 안) — 위력 × bonus, 맞힐 때마다 최대 체력 × heal 회복 (핏빛 손) */
    static boolean bloodBlade(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        startStrike(sp, r, P, k);
        strikeCast(sp, k);
        ServerLevel level = sp.level();
        Vec3 hand = RuneKit.handOf(sp).hand();
        double[] col = accent(k);
        for (int i = 0; i < 8; i++) {
            double a = (i / 8.0) * Math.PI * 2;
            Vec3 from = V.add(hand, new Vec3(Math.cos(a) * 1.2, Math.sin(a) * 0.6, Math.sin(a) * 1.2));
            Vec3 d = V.sub(hand, from);
            SkillFx.fx(level, "rpg:arrow_spark", from, Opt.of("dir", V.unit(d), "speed", V.len(d) / 0.35, "size", 0.1, "grow", 0.5, "life", 0.35, "color", col,
                    "alpha", 1.0));
        }
        SkillFx.play(level, "mob.vex.charge", sp.position(), 0.7, 0.8);
        SkillFx.play(level, "mob.zombie.unfect", sp.position(), 1.4, 0.4);
        bloodSword(sp, col);
        return true;
    }

    /**
     * 2026-11-13 피의 칼날의 3D 검 (룬 전용 검 2판 · 핏빛 · 2.1칸 — 사용자 "이재 다른 룬들도 모션하고 이펙트 추가하자"): 눈앞 가슴 높이에 가로로 나타나
     * (넓은 면이 나를 봄) 바람개비처럼 한 바퀴 돌며 핏방울을 빨아들인 뒤 (1 ~ 9) 무기를 든 손으로 날아들어 작아지며 스밈 (11 ~ 14)
     */
    private static void bloodSword(ServerPlayer sp, double[] col) {
        ServerLevel level = sp.level();
        mmorpg.fabric.vfx.Vfx.Fx fx = RuneProps.fx(level, sp.position(), 22);
        Vec3 f = V.flat(sp.getViewVector(1F));
        Vec3 rt = V.rightOf(f);
        Vec3 mid = V.up(V.add(sp.position(), f, 1.8), 1.3);
        double len = 2.1;
        Vec3 back = V.scale(f, -1);
        RuneSwords.Blade b = RuneSwords.blade(fx, V.add(mid, rt, -len * 0.42), rt, back, len, 0.2, col, RuneKit.BLOOD_CORE);
        fx.on(1, () -> b.grow(1, 3));
        for (int i = 1; i <= 8; i++) {
            final Vec3 d = RuneSwords.rot(rt, f, -45.0 * i);
            fx.on(1 + i, () -> b.pose(V.add(mid, d, -len * 0.42), d, back, 1));
            if (i % 2 == 0) {
                final int ii = i;
                fx.on(1 + i, () -> {
                    for (int k = 0; k < 3; k++) {
                        double a = (ii * 40 + k * 120) * Math.PI / 180;
                        Vec3 from = V.add(V.add(mid, rt, Math.cos(a) * 1.6), V.UP, Math.sin(a) * 1.1);
                        Vec3 dv = V.sub(mid, from);
                        SkillFx.fx(level, "rpg:arrow_spark", from, Opt.of("dir", V.unit(dv), "speed", V.len(dv) / 0.2, "size", 0.12, "grow", 0.5, "life", 0.2,
                                "color", col, "alpha", 1.0));
                    }
                });
            }
        }
        fx.on(11, () -> {
            RuneKit.Hand h = RuneKit.handOf(sp);
            Vec3 d = V.unit(V.add(V.UP, h.f(), 0.35));
            b.pose(h.hand(), d, V.rightOf(h.f()), 3);
            b.grow(0.22, 3);
        });
        fx.on(14, () -> {
            RuneKit.Hand h = RuneKit.handOf(sp);
            SkillFx.ring(level, h.hand(), h.f(), col, 0.7, 0.3, 0);
            SkillFx.burst(level, h.hand(), col, 0.6, 0.25);
            SkillFx.sparks(level, h.hand(), RuneKit.BLOOD_CORE, 6, 3);
            b.remove();
        });
    }

    /**
     * 검신 강림: 평타 16번 (15초 안) — 위력 × bonus, 평타마다 거대한 금빛 검기 (wave 칸 · 폭 width, 맞은 적 너머의 적), 평타 치명타 + crit.
     * 쓸 때 앞쪽 하늘에서 거대한 금빛 검이 떨어져 꽂히고, 그 동안 양 어깨 위에 금빛 검 둘이 떠 있음
     */
    static boolean swordGod(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        startStrike(sp, r, P, k);
        strikeCast(sp, k);
        ServerLevel level = sp.level();
        Vec3 f = V.flat(sp.getViewVector(1F));
        Vec3 g = V.add(sp.position(), f, 4);
        double gy = RuneKit.groundAt(level, g, sp.getY());
        Vec3 at = new Vec3(g.x, gy, g.z);
        double[] col = accent(k);
        double[] white = RuneKit.WHITE;
        double len = 8; // 0.48.2 2D 강화: 7 → 8
        double rest = len / 2 - 1.2;
        // 2026-11-13 거대한 금빛 검 = 3D 룬 검 (2D 빛의 검 대신): 하늘 13칸에서 칼끝을 아래로 떨어져 (빨라짐 · 5틱) 칼끝이 땅속 1.2칸 → 1.45초 꽂혀 있다가 가라앉음
        godFall(level, at, len, 13, 5, 29, col, white);
        SkillFx.fx(level, "rpg:arrow_shot", V.up(at, 12), Opt.of("dir", V.UP, "speed", 0.0, "size", 12.0, "thick", 0.12, "life", 0.3, "color", col, "alpha", 0.7));
        GodSwords.start(sp, (int) k.num("ticks"), col);
        RuneKit.later(6, () -> {
            RuneKit.rfx(level, "prop", V.up(at, rest + len / 2 - 0.3), Opt.of("size", 1.6, "life", 0.45, "cell", GLINT, "color", white, "grow", 0.6, "fade", 0.6));
            for (int i = 0; i < 3; i++) {
                final int ii = i;
                RuneKit.later(i * 2, () -> SkillFx.shock(level, at, ii % 2 == 1 ? white : col, 1.4 + ii, 0.5));
            }
            for (int i = 0; i < 8; i++) {
                Vec3 d = RuneKit.turn(new Vec3(1, 0, 0), i * 45 + Kit.rand() * 12);
                SkillFx.fx(level, "rpg:light_cut", V.add(V.up(at, 0.08), d, 1.4), Opt.of("dir", d, "size", 1.2, "thick", 0.3, "life", 0.9, "delay", 0.5,
                        "color", col, "alpha", 0.95));
            }
            SkillFx.sparks(level, V.up(at, 0.6), col, 12, 6);
            RuneBoost.impact(level, at, col, white, 3.6); // 0.48.2 2D 강화: 꽂히는 순간의 터짐
            SkillFx.play(level, "item.trident.thunder", at, 1.0, 0.8);
            SkillFx.play(level, "mace.heavy_smash_ground", at, 0.8, 0.8);
            RuneKit.shakeAround(level, at, 10, 0.25, 0.25);
        });
        SkillFx.play(level, "beacon.activate", sp.position(), 1.2, 0.8);
        // 2차 강화 — 강림: 흰 금빛 하늘 · 검이 내려오는 앞쪽 하늘이 갈라짐 → (꽂힐 때) 번쩍임 · 섬광 → 칼날 문양 · 충격 고리 (1틱 뒤)
        RuneKit.ultSkyFor(sp, level, at, "swordGod", 70, null);
        Vec3 rift = RuneKit.skyPoint(sp, at, 7, 21, 6, 22);
        RuneKit.skyPlate(sp, level, "crack", rift, 5.2, col, white, Opt.of("life", 0.75, "grow", 0.4, "fade", 0.5));
        RuneKit.later(6, () -> {
            RuneKit.ultFlashFor(sp, level, at, "swordGod", "flash", Opt.empty());
            RuneKit.skyFlare(level, rift, 3.6, col, white, Opt.of("life", 0.5));
        });
        RuneKit.later(7, () -> {
            RuneKit.domainSigil(level, at, 4.2, "blade", col, white, Opt.of("life", 1.3, "spinRate", 70.0, "grow", 0.3, "alpha", 0.85));
            RuneKit.novaRing(level, at, 1.4, 3.6, col, white, Opt.of("life", 0.45));
        });
        return true;
    }

    /** 하늘에서 떨어져 꽂히는 3D 검: 칼끝이 땅 at 위 sky 칸 → 땅속 1.2칸 (fall 틱 — 빨라짐), stuck 틱 꽂혀 있다가 가라앉으며 작아져 사라짐 (10틱) */
    static void godFall(ServerLevel level, Vec3 at, double len, double sky, int fall, int stuck, double[] col, double[] core) {
        mmorpg.fabric.vfx.Vfx.Fx fx = RuneProps.fx(level, at, fall + stuck + 14);
        double h2t = len * RuneSwords.HILT_TO_TIP / RuneProps.SWORD_LEN;
        RuneSwords.Blade b = RuneSwords.blade(fx, V.up(at, sky + h2t), V.DOWN, new Vec3(1, 0, 0), len, 0.6, col, core);
        fx.on(1, () -> b.grow(1, 2));
        for (int i = 1; i <= fall; i++) {
            double u = i / (double) fall;
            final double tipY = sky - (sky + 1.2) * u * u;
            fx.on(i, () -> b.pose(V.up(at, tipY + h2t), V.DOWN, new Vec3(1, 0, 0), 1));
        }
        fx.on(fall + stuck, () -> {
            b.grow(0.7, 10);
            b.prop.move(V.up(b.hilt(), -2.2), 10);
        });
        fx.on(fall + stuck + 10, b::remove);
    }

    /**
     * 2026-11-13 검신 강림의 어깨 위 3D 금빛 검 둘 (룬 전용 검 2판 · 1.8칸 — 2D 빛의 검 대신): 칼끝을 아래로 양 어깨 위에 떠서 틱마다 몸을 따라옴
     * (움직인 만큼 앞당김), 평타마다 번갈아 하나가 앞으로 찔러 날아갔다 돌아옴 (lunge). 검신 강림이 끝나면 (시간 · 횟수 · 죽음) 위로 솟으며 사라짐
     */
    static final class GodSwords {
        private static final Map<UUID, GodSwords> ON = new HashMap<>();
        final mmorpg.fabric.vfx.Vfx.Fx fx;
        final RuneSwords.Blade[] b = new RuneSwords.Blade[2];
        final int[] busy = {0, 0};
        int next;
        boolean done;
        static final double LEN = 1.8;

        private GodSwords(mmorpg.fabric.vfx.Vfx.Fx fx) {
            this.fx = fx;
        }

        /** 어깨 위 자리 (검 가운데) · 칼끝 방향 */
        private static Vec3[] shoulder(ServerPlayer sp, Vec3 c, int s, int t) {
            Vec3 fc = Kit.facingOf(sp);
            Vec3 sd = V.rightOf(fc);
            Vec3 p = V.up(V.add(V.add(c, sd, s * 1.05), fc, -0.35), 2.25 + 0.12 * Math.sin(t * 0.2 + s));
            Vec3 d = V.unit(new Vec3(sd.x * s * 0.15, -1, sd.z * s * 0.15));
            return new Vec3[] {p, d, fc};
        }

        static void start(ServerPlayer sp, int ticks, double[] col) {
            GodSwords old = ON.remove(sp.getUUID());
            if (old != null) old.retire();
            mmorpg.fabric.vfx.Vfx.Fx fx = RuneProps.fx(sp.level(), sp.position(), ticks + 20);
            GodSwords g = new GodSwords(fx);
            ON.put(sp.getUUID(), g);
            for (int i = 0; i < 2; i++) {
                int s = i == 0 ? -1 : 1;
                Vec3[] o = shoulder(sp, sp.position(), s, 0);
                g.b[i] = RuneSwords.blade(fx, V.add(o[0], o[1], -LEN * 0.42), o[1], o[2], LEN, 0.15, col, RuneKit.WHITE);
            }
            fx.on(1, () -> {
                for (RuneSwords.Blade x : g.b) x.grow(1, 4);
            });
            Vec3[] last = {sp.position()};
            fx.each(1, ticks, 1, t -> {
                if (g.done) return;
                if (sp.isRemoved() || ON.get(sp.getUUID()) != g || !active(sp)) {
                    g.retire();
                    return;
                }
                Vec3 c = sp.position();
                Vec3 lead = V.sub(c, last[0]);
                last[0] = c;
                if (V.len(lead) > 2) lead = Vec3.ZERO;
                Vec3 cc = V.add(c, lead);
                for (int i = 0; i < 2; i++) {
                    if (g.busy[i] > t) continue;
                    Vec3[] o = shoulder(sp, cc, i == 0 ? -1 : 1, t);
                    g.b[i].pose(V.add(o[0], o[1], -LEN * 0.42), o[1], o[2], 1);
                }
            });
            fx.on(ticks, g::retire);
        }

        private static boolean active(ServerPlayer sp) {
            for (StrikeBuff st : buffsOf(sp)) if ("swordGod".equals(st.def.strOrNull("fn"))) return true;
            return false;
        }

        /** 평타: 번갈아 하나가 앞으로 찔러 날아갔다 (3틱 — 4칸) 돌아옴 (4틱) */
        static void lunge(ServerPlayer sp, Vec3 f) {
            GodSwords g = ON.get(sp.getUUID());
            if (g == null || g.done) return;
            int i = g.next;
            g.next = 1 - i;
            RuneSwords.Blade x = g.b[i];
            int t0 = g.fx.age();
            g.busy[i] = t0 + 9;
            int s = i == 0 ? -1 : 1;
            Vec3 from = V.add(V.up(sp.position(), 1.5), V.rightOf(f), s * 0.55);
            x.pose(V.add(from, f, -0.4), f, V.UP, 1);
            g.fx.on(t0 + 1, () -> x.pose(V.add(from, f, 3.6), f, V.UP, 3));
            g.fx.on(t0 + 5, () -> {
                Vec3[] o = shoulder(sp, sp.position(), s, g.fx.age());
                x.pose(V.add(o[0], o[1], -LEN * 0.42), o[1], o[2], 4);
            });
        }

        void retire() {
            if (done) return;
            done = true;
            ON.values().remove(this);
            int age = fx.age();
            for (RuneSwords.Blade x : b) {
                if (x == null) continue;
                x.grow(0.1, 6);
                x.prop.move(V.up(x.hilt(), 2.4), 6);
            }
            fx.on(age + 7, () -> {
                for (RuneSwords.Blade x : b) if (x != null) x.remove();
                fx.end();
            });
        }
    }

    // ------------------------------------------------------------ 평타 훅 (RpgCombat)

    /** 평타에 더할 피해 (피해를 넣기 직전 — 세계를 바꾸지 않음): 걸린 평타 강화마다 위력 × bonus (연타는 근접만 위력 × echo) */
    private static RpgCombat.Bonus strikeBonus(ServerPlayer sp, LivingEntity victim, Weapon w) {
        Map<String, StrikeBuff> b = BUFFS.get(sp.getUUID());
        if (b == null || b.isEmpty() || !Kit.canHit(sp, victim)) return null;
        double add0 = 0;
        List<String> grades = new ArrayList<>();
        for (StrikeBuff st : b.values()) {
            if (!strikeOn(sp, st)) continue;
            Obj k = st.def;
            double per = k.numOrNull("echo") != null ? (Combat.isMeleeWeapon(w) ? st.P * k.num("echo") : 0) : st.P * k.numOr("bonus", 0);
            if (!(per > 0)) continue;
            add0 += per;
            grades.add(st.grade);
        }
        return add0 > 0 ? new RpgCombat.Bonus(add0, RUNE_STRIKE.str("mark"), RUNE_STRIKE.str("family"), DamageNumbers.Style.RUNE, grades) : null;
    }

    /** 평타 치명타 + (검신 강림). 맞을 엔티티가 스킬 대상이 아니면 (다른 플레이어 · 길들인 동물 등) 없음 — 더한 피해와 같음 */
    private static double strikeCrit(ServerPlayer sp, Weapon w, LivingEntity victim) {
        if (victim != null && !Kit.canHit(sp, victim)) return 0;
        double c = 0;
        for (StrikeBuff st : buffsOf(sp)) c += st.def.numOr("crit", 0);
        return c;
    }

    /** 활 · 완드 재사용 배율 (연타) */
    private static double strikeRangedScale(ServerPlayer sp, Weapon w) {
        double k = 1;
        for (StrikeBuff st : buffsOf(sp)) if (st.def.truthy("rangedCd")) k = Math.min(k, st.def.num("rangedCd"));
        return k;
    }

    /** 평타가 들어감 (after): 더한 평타 강화마다 한 번 쓰고 그 룬의 덧붙임 (잔상 · 부채꼴 · 회복 · 검기). 연타는 활 · 완드에도 잔상 연출 */
    private static void strikeLanded(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        Map<String, StrikeBuff> b = BUFFS.get(sp.getUUID());
        if (b == null || b.isEmpty()) return;
        Set<String> used = new HashSet<>();
        if (bonus != null) {
            for (RpgCombat.Bonus part : bonus.parts()) {
                if (!RUNE_STRIKE.str("family").equals(part.key())) continue;
                if (part.grades() != null) used.addAll(part.grades());
                break;
            }
        }
        // 0.41.0 근접 평타는 휘두를 때마다 한 번 (조준한 적 · 범위 적 — 같은 휘두르기), 활 · 완드는 맞힐 때마다
        long sw = Combat.isMeleeWeapon(w) ? SwingArea.swingTick(sp) : -1;
        for (String g : new ArrayList<>(b.keySet())) {
            StrikeBuff st = b.get(g);
            if (st == null || !strikeOn(sp, st)) continue;
            if (used.contains(g)) {
                if (sw >= 0 && st.swing == sw) continue; // 같은 휘두르기 — 이미 셈 · 덧붙임
                st.swing = sw;
                if (Double.isFinite(st.left)) st.left -= 1;
                int own = RuneKit.enter(sp); // 0.48.2 참격 판의 1인칭 나눔
                String tg = Tasks.tag("strike:" + g); // 0.50.0 계열의 맞은 자리 (RuneBoost.mark)
                try {
                    strikeEffect(sp, victim, w, st);
                } finally {
                    RuneKit.leave(own);
                    Tasks.tag(tg);
                }
                if (st.left <= 0) {
                    strikeEnd(sp, st, true);
                    if (sw < 0) b.remove(g); // 휘두르기면 이번 휘두르기가 끝날 때까지 남김 (범위 적도 더한 피해 — buffTick 이 지움)
                }
            } else if (st.def.truthy("rangedCd") && !Combat.isMeleeWeapon(w) && victim != null && !victim.isRemoved()) {
                // 연타 (활 · 완드): 맞은 자리에 잔상 고리
                Vec3 p = Kit.bodyPoint(victim);
                SkillFx.ring(sp.level(), p, V.unit(V.sub(p, sp.getEyePosition())), accent(st.def), 0.6, 0.22, 3);
            }
        }
    }

    /** 평타 강화가 끝남 (used = 평타를 다 씀, 아니면 시간이 다 됨) */
    private static void strikeEnd(ServerPlayer sp, StrikeBuff st, boolean used) {
        SkillFx.burst(sp.level(), RuneKit.handOf(sp).hand(), accent(st.def), 0.4, 0.25);
        if (Notify.actionbarFree(sp)) Notify.notify(sp, "§7" + st.def.str("name") + (used ? " 평타를 모두 썼습니다" : " 시간이 끝났습니다"), 16);
    }

    /**
     * 0.48.2 이번 평타의 휘두르는 면 (참격 판의 면 — 사용자 "평타 때리면 그뒤에 참격 이펙트 나오는데 그거 방향대로 나가면 좋겠는데"): 세로 베기 · 내려찍기 · 찌르기 =
     * 옆 (판을 세움), X 베기 = 비스듬히 (틱마다 번갈아), 그 밖 (가로 베기 · 회전 · 휩쓸기 · 밀치기) = 위 (판을 눕힘)
     */
    private static Vec3 swingFace(ServerPlayer sp, Vec3 f) {
        MotionSystem.Swing s = MotionSystem.last(sp);
        String m = s == null ? "" : s.motion();
        Vec3 side = V.rightOf(f);
        return switch (m) {
            case "slash_v", "chop", "smite", "thrust" -> side;
            case "dagger_x" -> V.unit(V.add(V.UP, side, (s.tick() & 1) == 0 ? 1.0 : -1.0));
            default -> V.UP;
        };
    }

    /** 평타 강화 룬마다 맞힌 뒤 덧붙임 */
    private static void strikeEffect(ServerPlayer sp, LivingEntity victim, Weapon w, StrikeBuff st) {
        Obj k = st.def;
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 eye = sp.getEyePosition();
        boolean valid = victim != null && !victim.isRemoved();
        Vec3 at = valid ? Kit.bodyPoint(victim) : V.add(eye, sp.getViewVector(1F), 2);
        Vec3 v = V.unit(V.sub(at, eye));
        boolean melee = Combat.isMeleeWeapon(w);
        Vec3 f = V.len(V.sub(at, eye)) > 0.3 ? V.flat(V.sub(at, sp.position())) : Kit.facingOf(sp);
        double[] col = accent(k);
        double[] white = RuneKit.WHITE;
        switch (k.str("fn")) {
            case "edgeHone" -> {
                RuneKit.rfx(level, "prop", V.add(at, v, -0.4), Opt.of("size", 0.55, "life", 0.25, "cell", GLINT, "spin", 45.0, "color", white, "grow", 0.4,
                        "fade", 0.6));
                SkillFx.fx(level, "rpg:slash_x", V.add(at, v, -0.3), Opt.of("dir", v, "size", 0.8, "life", 0.2, "color", col, "alpha", 1.0));
                SkillFx.sparks(level, at, white, 5, 4);
                SkillFx.play(level, "random.anvil_land", at, 2.0, 0.25);
            }
            case "flurry" -> {
                // 잔상: 2틱 뒤 같은 자리를 한 번 더 베는 빛 + 옆의 적 하나에게 위력 × echo
                RuneKit.later(2, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    SkillFx.arc(level, V.add(at, v, -0.5), v, Opt.of("shape", "thin", "flip", Kit.rand() < 0.5, "size", 1.1, "life", 0.16, "speed", 2.0,
                            "spin", (Kit.rand() - 0.5) * 80, "color", col, "core", true));
                    LivingEntity other = null;
                    if (victim != null && !victim.isRemoved()) {
                        List<LivingEntity> near = new ArrayList<>();
                        for (LivingEntity e : Kit.around(sp, victim.position(), k.num("echoRange"), 2.5)) {
                            if (!e.getUUID().equals(victim.getUUID())) near.add(e);
                        }
                        List<LivingEntity> sorted = RuneKit.nearestTo(near, victim.position());
                        if (!sorted.isEmpty()) other = sorted.get(0);
                    }
                    if (other == null) return;
                    Vec3 to = Kit.bodyPoint(other);
                    RuneKit.streak(level, at, to, col, 0.12, 0.25, 0.9, 0);
                    if (RuneKit.runeHit(sp, other, st.P, k.num("echo"), "str") > 0) {
                        SkillFx.fx(level, "rpg:slash_x", to, Opt.of("dir", V.unit(V.sub(to, at)), "size", 0.8, "life", 0.2, "color", col, "alpha", 1.0));
                        SkillFx.sparks(level, to, white, 4, 4);
                    }
                });
                SkillFx.play(level, "item.spear.attack_miss", at, 1.8, 0.4);
            }
            case "cleave" -> {
                // 부채꼴: 근접 = 내 앞, 활 · 완드 = 맞은 적 뒤 (꿰뚫어 퍼짐). 0.41.0 근접은 같은 휘두르기에 이미 맞은 적 (조준 · 범위) 은 빼고
                Vec3 c = melee ? sp.position() : valid ? victim.position() : V.add(sp.position(), f, 3);
                double rad = melee ? k.num("cleave") : k.num("splash");
                Set<UUID> skip = melee ? new HashSet<>(SwingArea.swingVictims(sp)) : new HashSet<>();
                if (victim != null) skip.add(victim.getUUID());
                // 0.48.2 참격 판: 휘두른 면 그대로 앞으로 나가는 초승달 (불룩한 날이 앞 — 근접은 이번 평타의 면, 활 · 완드는 눕힘)
                RuneKit.waveCard(level, V.up(V.add(c, f, melee ? 0.9 : 0.3), 1.0), f, melee ? swingFace(sp, f) : V.UP, Opt.of("speed", rad / 0.3, "size", rad * 0.55,
                        "life", 0.3, "grow", 0.35, "fade", 0.6), col, white);
                SkillFx.fx(level, "rpg:light_cut", V.up(V.add(c, f, rad * 0.7), 0.08), Opt.of("dir", V.rightOf(f), "size", rad * 0.55, "thick", 0.22, "life", 0.6,
                        "delay", 0.35, "color", col, "alpha", 0.9));
                RuneKit.later(1, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    int shown = 0; // 적이 많으면 칼자국은 12마리까지 (모바일 파티클 예산)
                    for (LivingEntity e : RuneKit.coneAround(sp, c, f, rad, k.num("cone"))) {
                        if (skip.contains(e.getUUID())) continue;
                        if (RuneKit.runeHit(sp, e, st.P, k.num("bonus"), "str") > 0 && shown++ < 12) {
                            Vec3 p = Kit.bodyPoint(e);
                            SkillFx.cutMark(level, p, V.unit(V.sub(p, eye)), -10, 0.08, col, white, 0.9);
                        }
                    }
                });
                SkillFx.play(level, "item.spear.attack_miss", c, 1.0, 0.6);
            }
            case "bloodBlade" -> {
                // 핏빛 X 베기 + 밝은 속 (짙은 빨강만이면 풀밭 위에서 어둡게 보임) + 튀는 핏방울
                SkillFx.fx(level, "rpg:slash_x", V.add(at, v, -0.3), Opt.of("dir", v, "size", 0.95, "life", 0.24, "color", col, "alpha", 1.0));
                SkillFx.fx(level, "rpg:slash_x", V.add(at, v, -0.36), Opt.of("dir", v, "size", 0.62, "life", 0.2, "color", RuneKit.BLOOD_CORE, "alpha", 1.0));
                SkillFx.sparks(level, at, col, 5, 4);
                double healed = RpgHealth.healBy(sp, RpgHealth.maxOf(sp) * k.num("heal") * Runes.supportScale(RpgStats.effective(sp), RUNE_STRIKE.str("family")));
                if (healed > 0) {
                    DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
                    Vec3 to = V.up(sp.position(), 1.0);
                    for (int i = 0; i < 3; i++) {
                        Vec3 from = V.add(at, new Vec3((Kit.rand() - 0.5) * 0.6, (Kit.rand() - 0.3) * 0.6, (Kit.rand() - 0.5) * 0.6));
                        Vec3 d = V.sub(to, from);
                        SkillFx.fx(level, "rpg:arrow_spark", from, Opt.of("dir", V.unit(d), "speed", V.len(d) / 0.35, "size", 0.14, "grow", 0.5, "life", 0.35,
                                "color", col, "alpha", 1.0));
                    }
                }
            }
            case "swordGod" -> {
                GodSwords.lunge(sp, f); // 2026-11-13 어깨 위 3D 검이 번갈아 앞으로 찌름
                // 거대한 금빛 검기: 근접 = 내 앞에서, 활 · 완드 = 맞은 적에서 그 너머로 (맞은 적은 평타에 이미 들어감).
                // 8단계 6돌 (cross): X자로 두 줄 (양옆 16° — 줄마다 위력 × cross)
                Vec3 from = melee ? V.up(V.add(sp.position(), f, 0.6), 1.1) : at;
                Vec3 dir0 = melee ? f : V.unit(new Vec3(v.x, Math.max(-0.4, Math.min(0.4, v.y)), v.z));
                double speed = k.num("speed");
                double width = k.num("width");
                double cross = k.numOr("cross", 0);
                double waveBonus = k.num("bonus") * (cross > 0 ? cross : 1);
                Vec3 face = melee ? swingFace(sp, f) : V.UP; // 0.48.2 참격 판: 이번 평타의 휘두른 면 (가로 = 눕힘 · 세로 = 세움)
                for (double ang : cross > 0 ? new double[] {-16, 16} : new double[] {0}) {
                    Vec3 dir = ang == 0 ? dir0 : V.turnY(dir0, ang);
                    double len = Math.max(1, RuneKit.rayLength(level, from, dir, k.num("wave")));
                    double s0 = Math.min(1.2, len * 0.5);
                    RuneKit.waveCard(level, V.add(from, dir, s0), dir, face, Opt.of("speed", speed, "size", width, "life", Math.max(0.05, (len - s0) / speed), "fade",
                            0.2), col, white);
                    SkillFx.fx(level, "rpg:light_cut", V.add(from, dir, len / 2), Opt.of("dir", dir, "size", len / 2, "thick", 0.16, "life", 0.35, "delay", 0.15,
                            "color", col, "alpha", 0.8));
                    List<Kit.LineHit> targets = new ArrayList<>();
                    for (Kit.LineHit hit : RuneKit.lineTargets(sp, from, dir, len, width)) {
                        if (victim == null || !hit.e().getUUID().equals(victim.getUUID())) targets.add(hit);
                    }
                    // 적이 많으면 불티를 줄이고 X 는 10마리까지 (모바일 파티클 예산 — 같은 틱에 여럿이 맞음)
                    int sparkN = targets.size() > 6 ? 0 : targets.size() > 3 ? 2 : 5;
                    for (int j = 0; j < targets.size(); j++) {
                        LivingEntity e = targets.get(j).e();
                        final int jj = j;
                        Tasks.later((int) Math.max(1, Math.round((targets.get(j).t() / speed) * 20)), () -> {
                            if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                            if (RuneKit.runeHit(sp, e, st.P, waveBonus, "str") > 0 && jj < 10) {
                                Vec3 p = Kit.bodyPoint(e);
                                SkillFx.fx(level, "rpg:slash_x", p, Opt.of("dir", dir, "size", 1.2, "life", 0.24, "color", col, "alpha", 1.0));
                                if (sparkN > 0) SkillFx.sparks(level, p, white, sparkN, 5);
                            }
                        });
                    }
                }
                SkillFx.play(level, "item.trident.throw", from, 1.4, 0.5);
            }
            default -> {
            }
        }
    }

    /**
     * 평타 강화 동안 (홀수 틱 2틱마다 — 연출 횟수 n): 무기를 든 손에 그 룬의 빛 (4틱마다, 1인칭 화면 오른쪽 아래 · 다른 사람에게도 보임)
     */
    private static void strikeFx(ServerPlayer sp, List<StrikeBuff> list, long n) {
        if (list.isEmpty()) return;
        ServerLevel level = sp.level();
        StrikeBuff top = list.get(0);
        Vec3 vel = RuneKit.bodyVel(sp);
        if (n % 2 == 1) {
            RuneKit.Hand h = RuneKit.handOf(sp);
            Vec3 side = V.rightOf(h.f());
            for (int i = 0; i < Math.min(2, list.size()); i++) {
                double a = n * 0.45 + i * Math.PI;
                SkillFx.fx(level, "rpg:arrow_spark", V.add(V.up(h.hand(), Math.sin(a) * 0.16), side, Math.cos(a) * 0.2), Opt.of("dir", V.UP, "speed", 0.7,
                        "size", 0.09, "life", 0.35, "color", accent(list.get(i).def), "alpha", 1.0));
            }
            if (n % 4 == 1) {
                RuneKit.rfx(level, "prop", h.hand(), Opt.of("size", 0.09 + 0.02 * RuneKit.GRADE_ORDER.indexOf(top.grade), "life", 0.24, "cell", GLINT,
                        "spin", n * 12.0, "color", accent(top.def), "alpha", 0.95, "vel", vel));
            }
        }
        // 검신 강림의 어깨 위 검 둘은 2026-11-13 3D (GodSwords — 틱마다 몸을 따라옴)
    }

    // ------------------------------------------------------------ 신체 강화 · 평타 강화: 매 틱 · 상태 줄 (두 계열 함께 — 여기서 한 번만 등록)

    /**
     * 신체 강화 · 평타 강화 (매 틱): 끝난 것 지우기 · 회복 · 연출. 연출은 2틱마다 (신체 강화 = 짝수 틱, 평타 강화 = 홀수 틱 —
     * 둘 다 걸려도 한 틱에 몰리지 않게, 무거운 룬의 터지는 틱과 겹쳐도 60개 안). m = 연출 횟수 (2틱마다 1씩).
     * 플레이어는 uuid 로 지금 엔티티를 찾음 (나갔으면 지움)
     */
    private static void buffTick(MinecraftServer server) {
        long now = Tasks.now();
        long n = buffTicks++;
        long m = n >> 1;
        RuneBody.tick(server, now, n, m);
        for (UUID id : new ArrayList<>(BUFFS.keySet())) {
            Map<String, StrikeBuff> b = BUFFS.get(id);
            if (b == null) continue;
            ServerPlayer pl = server.getPlayerList().getPlayer(id);
            if (pl == null || pl.isRemoved()) {
                BUFFS.remove(id);
                continue;
            }
            for (Iterator<StrikeBuff> it = b.values().iterator(); it.hasNext(); ) {
                StrikeBuff st = it.next();
                if (strikeOn(pl, st)) continue;
                it.remove();
                if (st.token.equals(RuneKit.lifeToken(pl)) && st.left > 0) strikeEnd(pl, st, false); // 시간이 다 됨 (죽음 · 차원 이동 · 탈것이면 조용히)
            }
            if (b.isEmpty()) {
                BUFFS.remove(id);
                continue;
            }
            if (n % 2 == 1) strikeFx(pl, buffsOf(pl), m);
        }
    }

    /** 화면 하단 상태 줄의 신체 강화 · 평타 강화 (룬 · 초월 무기 줄 뒤에 붙음) */
    private static String buffHud(ServerPlayer sp) {
        long now = Tasks.now();
        List<String> out = new ArrayList<>();
        RuneBody.hud(sp, now, out);
        for (StrikeBuff st : buffsOf(sp)) {
            if (!(st.left > 0)) continue; // 0.41.0 마지막 번을 쓴 휘두르기가 끝나기를 기다리는 1틱 — 이미 "평타를 모두 썼습니다"
            out.add("§e" + st.def.str("name") + (Double.isFinite(st.left) ? " §f×" + Js.str(st.left) : "") + " §7"
                    + Js.toFixed(Math.max(0, (st.until - now) / 20.0), 1) + "초");
        }
        return String.join(" §8| ", out);
    }

    /** 시험 · 사진첩: 이 사람의 평타 강화를 모두 지움 (조용히) */
    public static void testClear(ServerPlayer sp) {
        BUFFS.remove(sp.getUUID());
    }

    /** 시험: 이 사람의 그 등급 평타 강화의 남은 번 (걸려 있지 않으면 -1 · 번이 없으면 무한) */
    public static double testLeft(ServerPlayer sp, String grade) {
        Map<String, StrikeBuff> b = BUFFS.get(sp.getUUID());
        StrikeBuff st = b == null ? null : b.get(grade);
        return st == null || st.until <= Tasks.now() ? -1 : st.left;
    }
}
