package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import mmorpg.core.StatTree;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.SkillBarNet;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.stattree.StatTrees;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-18 축복의 길 — 공용 스킬 (스탯 트리의 칸 40 · 90 에서 고른 길이 여는 스킬 — stat_tree.json skills, 컨셉 문서 "공용 스킬 16"). 한 사람이 끼우는 것은
 * 하나 (사용자 "공용스킬 하나만 쓰라고 하자"). 이동기 8 (피해 없음 · 무적 없음) · 작은 공격 8 (그 신의 원소 — 묻힘 · 반응, 원소 색 피해 숫자 · 피해 합 = 그 나무
 * 스탯의 룬 위력 P × mult × 공격력 보너스).
 * <p>그림은 신의 원소 묶음 (위르드식 — 도트 2D + 바닐라 빛 파티클, claude/wyrd-style.md): 포르티아 불 = 분노 묶음 el_rage (+ 불꽃 혀는 용 묶음 el_dragon),
 * 라에타 번개 = 뇌광 el_thunder, 니바리아 얼음 = 서리 el_frost, 그라비스 바위 = 곰 el_bear. 예전 rfx 이름으로 부르는 JobFx 그림 (번개 기둥 · 땅의 번개 고리 · 얼음
 * 결정 · 돌가시 · 돌진 줄기) 은 이 클래스의 빛 배열을 {@link PhantomFx#painter} 가 그 묶음으로 칠함. 화면 흔들림 없음.
 * <p>키: 지금은 /stattree cast (조작 단계에서 여섯 키 가운데 하나에 끼움) · 재사용 대기 패킷 이름 {@link #CD_PREFIX} + id.
 */
public final class CommonSkills {
    private CommonSkills() {}

    /** 끼운 공용 스킬 (Props — 스킬 id, 없거나 닫혔으면 열린 첫 스킬) */
    public static final String PROP = "rpg:common_skill";
    /** 재사용 대기 패킷 이름 앞 (HUD — 조작 단계에서 여섯째 칸) */
    public static final String CD_PREFIX = "common:";

    // 신의 원소 빛 (PhantomFx.painter: 이 배열로 부른 예전 rfx 는 그 원소 묶음 그림으로)
    public static final double[] FIRE = {1.0, 0.42, 0.12};
    public static final double[] FIRE_CORE = {1.0, 0.86, 0.5};
    public static final double[] VOLT = {1.0, 0.84, 0.3};
    public static final double[] VOLT_CORE = {0.82, 0.94, 1.0};

    /**
     * 2026-11-28 벼락받이 사당 (field.FieldShrines — 세계관 2-6 "볏짚 금줄을 두른 큰 기둥에 밤마다 벼락이 떨어진다"): 기둥 꼭대기 top 에서 하늘로 height 칸
     * 번개 (공용 스킬 번개 색 — 라이트닝 스프린트와 같은 그림) · 꼭대기 번개 고리 · 받침 둘레 번개 고리 (ground — 없으면 생략). 모두에게 보임 (그림만 — 불 · 피해 없음).
     */
    public static void shrineBolt(ServerLevel level, Vec3 top, double height, Vec3 ground) {
        JobFx.thunder(level, top, height, VOLT, VOLT_CORE, 0.45, Opt.of("fade", 0.3));
        JobFx.volt(level, V.up(top, 0.2), 1.8, VOLT, VOLT_CORE, 0.6, Opt.of());
        if (ground != null) JobFx.volt(level, V.up(ground, 0.08), 3.4, VOLT, VOLT_CORE, 0.8, Opt.of());
    }
    public static final double[] ICE = {0.5, 0.84, 1.0};
    public static final double[] ICE_CORE = {0.9, 0.98, 1.0};
    public static final double[] ROCK = {0.7, 0.45, 0.2};
    public static final double[] ROCK_CORE = {1.0, 0.78, 0.42};

    /** 스킬 하나 (P = 그 나무 스탯의 룬 위력) — 썼으면 true (재사용 대기 시작) */
    @FunctionalInterface
    interface Impl {
        boolean cast(ServerPlayer sp, StatTree.Skill k, double P);
    }

    private static final Map<String, Impl> IMPL = new LinkedHashMap<>();
    /** 다시 쓸 수 있는 틱 · 대기 전체 틱 */
    private static final Map<UUID, Long> READY = new HashMap<>();
    private static final Map<UUID, Integer> TOTAL = new HashMap<>();
    /** 시험 · 영상: 사람마다 마지막으로 쓴 스킬 · 그 스킬이 넣은 피해 합 (시험이 나란히 돌아도 섞이지 않게 사람마다) */
    private static final Map<UUID, String> LAST_CAST = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<UUID, Double> LAST_DEALT = new java.util.concurrent.ConcurrentHashMap<>();
    /** 2026-11-27 스틸 차지의 강철 몸 (받는 피해 −20%) 이 끝나는 틱 · 마지막으로 쨍 한 틱 (맞을 때마다 그림 — 4틱에 한 번) */
    private static final Map<UUID, long[]> STEEL = new HashMap<>();
    /** 시험: 강철 몸 동안 맞아 쨍 한 수 */
    public static int steelClangs;

    static void init() {
        IMPL.put("blaze_leap", CommonSkills::blazeLeap);
        IMPL.put("steel_charge", CommonSkills::steelCharge);
        IMPL.put("blaze_cross", CommonSkills::blazeCross);
        IMPL.put("ember_nova", CommonSkills::emberNova);
        IMPL.put("spark_blink", CommonSkills::sparkBlink);
        IMPL.put("lightning_sprint", CommonSkills::lightningSprint);
        IMPL.put("volt_dagger", CommonSkills::voltDagger);
        IMPL.put("flicker_shot", CommonSkills::flickerShot);
        IMPL.put("ice_slide", CommonSkills::iceSlide);
        IMPL.put("crystal_shift", CommonSkills::crystalShift);
        IMPL.put("cryo_pulse", CommonSkills::cryoPulse);
        IMPL.put("hailstone", CommonSkills::hailstone);
        IMPL.put("boulder_roll", CommonSkills::boulderRoll);
        IMPL.put("rock_launch", CommonSkills::rockLaunch);
        IMPL.put("rock_spike", CommonSkills::rockSpike);
        IMPL.put("aftershock", CommonSkills::aftershock);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            READY.clear();
            TOTAL.clear();
            LAST_CAST.clear();
            LAST_DEALT.clear();
            STEEL.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            UUID id = h.player.getUUID();
            READY.remove(id);
            TOTAL.remove(id);
            LAST_CAST.remove(id);
            LAST_DEALT.remove(id);
            STEEL.remove(id);
        });
        // 2026-11-27 스틸 차지의 강철 몸 동안 맞으면 쨍 (받는 피해 −20% 가 들어가는 것이 보이게)
        mmorpg.fabric.RpgHealth.LossHook lh = mmorpg.fabric.RpgHealth.lossHook;
        mmorpg.fabric.RpgHealth.lossHook = (sp, src, loss, before, after) -> {
            lh.lost(sp, src, loss, before, after);
            steelClang(sp, src);
        };
    }

    /** 시험 · 영상: 이 사람이 마지막으로 쓴 공용 스킬 (없으면 null) */
    public static String lastCast(ServerPlayer sp) {
        return sp == null ? null : LAST_CAST.get(sp.getUUID());
    }

    /** 시험 · 영상: 이 사람이 마지막으로 쓴 공용 스킬이 지금까지 넣은 피해 합 */
    public static double lastDealt(ServerPlayer sp) {
        return sp == null ? 0 : LAST_DEALT.getOrDefault(sp.getUUID(), 0.0);
    }

    /** 구현이 있는 공용 스킬 id (시험 — stat_tree.json skills 와 같아야 함) */
    public static Set<String> implemented() {
        return java.util.Collections.unmodifiableSet(IMPL.keySet());
    }

    // ------------------------------------------------------------ 끼우기 · 쓰기

    /** 지금 끼운 공용 스킬 (고른 것이 열려 있으면 그것, 아니면 열린 첫 스킬 — 없으면 null) */
    public static String equipped(ServerPlayer sp) {
        List<String> open = StatTrees.openSkills(sp);
        if (open.isEmpty()) return null;
        String id = Props.get(sp, PROP);
        return id != null && open.contains(id) ? id : open.get(0);
    }

    /** 끼우기: 열린 스킬이면 null, 아니면 까닭 */
    public static String equip(ServerPlayer sp, String id) {
        StatTree.Skill k = StatTree.skill(id);
        if (k == null) return "없는 공용 스킬입니다: " + id;
        if (!StatTrees.openSkills(sp).contains(id)) return k.name() + " — 스탯 트리에서 그 길을 고르면 열립니다";
        Props.set(sp, PROP, id);
        Notify.notify(sp, "§b공용 스킬 §f" + k.name() + " §7을 끼웠습니다", 40);
        return null;
    }

    /** 남은 재사용 대기 (틱) */
    public static long cooldownLeft(ServerPlayer sp) {
        Long r = READY.get(sp.getUUID());
        return r == null ? 0 : Math.max(0, r - Tasks.now());
    }

    /** 재사용 대기 지움 (시험 · 영상) */
    public static void resetCooldown(ServerPlayer sp) {
        READY.remove(sp.getUUID());
        TOTAL.remove(sp.getUUID());
    }

    /** 끼운 공용 스킬을 씀 (키 · /stattree cast) — 썼으면 true */
    public static boolean press(ServerPlayer sp) {
        if (sp == null || sp.isRemoved() || !sp.isAlive()) return false;
        String id = equipped(sp);
        if (id == null) {
            Notify.notify(sp, StatTrees.isActive(sp) ? "§7열린 공용 스킬이 없습니다 §8(스탯 트리 40 · 90 칸의 길)" : "§7스탯 트리를 쓸 수 없습니다", 40);
            return false;
        }
        return cast(sp, id, false);
    }

    /**
     * id 를 씀 (force = 열림 · 재사용 대기를 보지 않음 — 시험 · 영상). 원소 맥락 (작은 공격) · 칠할 묶음 · 그림 주인을 정하고 부름 — 안의 later 도 이어 받음
     */
    public static boolean cast(ServerPlayer sp, String id, boolean force) {
        StatTree.Skill k = StatTree.skill(id);
        Impl impl = IMPL.get(id);
        if (k == null || impl == null) return false;
        if (!force) {
            if (!StatTrees.openSkills(sp).contains(id)) return false;
            if (Status.blockedBySilence(sp) || mmorpg.fabric.mount.Riding.blocked(sp, "skill")) return false;
            long left = cooldownLeft(sp);
            if (left > 0) {
                Notify.notify(sp, String.format(java.util.Locale.ROOT, "§7%s §f%.1f초", k.name(), left / 20.0), 20);
                return false;
            }
        }
        double P = RuneCast.power(sp, k.stat());
        Object prevEl = ElementSystem.enter(k.attack() ? k.element() : null, P, false);
        String prevLib = Tasks.lib(libName(k.stat()));
        int prevOwner = RuneKit.enter(sp);
        boolean ok;
        LAST_DEALT.put(sp.getUUID(), 0.0);
        try {
            ok = impl.cast(sp, k, P);
        } finally {
            RuneKit.leave(prevOwner);
            Tasks.lib(prevLib);
            ElementSystem.leave(prevEl);
        }
        if (!ok) return false;
        LAST_CAST.put(sp.getUUID(), id);
        int ticks = Math.max(1, (int) Math.round(k.cd() * 20 * StatTrees.cdMult(sp)));
        READY.put(sp.getUUID(), Tasks.now() + ticks);
        TOTAL.put(sp.getUUID(), ticks);
        SkillBarNet.cooldown(sp, CD_PREFIX + id, ticks, ticks);
        mmorpg.fabric.motion.MotionSystem.skill(sp, CD_PREFIX + id);
        if (!k.attack()) { // 2026-11-18 구르기 대신 공용 이동기 (사용자 "구르기 삭제하고 스탯에서 주는 대쉬로 대체") — 구르기 뒤 덤 (아티팩트 스키드블라드니르 「순풍」)
            try {
                mmorpg.fabric.motion.MotionSystem.onRoll.accept(sp);
            } catch (RuntimeException e) {
                mmorpg.fabric.Mmorpg.LOG.warn("[mmorpg] common move hook", e);
            }
        }
        return true;
    }

    /** 나무 스탯 → 칠할 묶음 */
    static PhantomFx.Lib lib(String stat) {
        return switch (stat) {
            case "dex" -> PhantomFx.THUNDER;
            case "int" -> PhantomFx.FROST;
            case "vit" -> PhantomFx.BEAR;
            default -> PhantomFx.RAGE;
        };
    }

    static String libName(String stat) {
        return lib(stat).p();
    }

    // ------------------------------------------------------------ 공용

    /** 작은 공격 한 번 (P × 몫 share × 공격력 보너스 — 마력은 마법 피해, 맥락의 원소를 묻힘 · 원소 색 숫자) — 넣은 피해 */
    static double hit(ServerPlayer sp, LivingEntity e, StatTree.Skill k, double P, double share) {
        if (e == null || !Kit.canHit(sp, e) || !(share > 0)) return 0;
        double dmg = P * k.mult() * share * StatTrees.atkMult(sp) * RpgCombat.dealtMult.applyAsDouble(sp);
        Kit.Cause cause = "int".equals(k.stat()) ? Kit.Cause.MAGIC : Kit.Cause.ATTACK;
        float before = e.getHealth();
        if (!Kit.applySkillDamage(sp, e, dmg, false, cause, DamageNumbers.Style.SKILL)) return 0;
        ElementSystem.onHit(sp, e);
        float after = e.isRemoved() ? 0 : e.getHealth();
        double dealt = before > after ? before - after : dmg;
        LAST_DEALT.merge(sp.getUUID(), dealt, Double::sum);
        return dealt;
    }

    /** 밀침 (밀 수 있는 것만 — 보스 · 무거운 것 제외) */
    static void push(LivingEntity e, Vec3 dir, double k, double up) {
        if (e != null && JobKit.pushable(e)) RuneKit.knock(e, dir, k, up);
    }

    /** 둘레 반지름 r 의 맞을 수 있는 것 (세로 v) */
    static List<LivingEntity> foes(ServerPlayer sp, Vec3 c, double r, double v) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, c, r, v)) if (Kit.canHit(sp, e)) out.add(e);
        return out;
    }

    /** 땅 (아래로 4칸 안 — 없으면 그 높이) */
    static Vec3 groundOf(ServerLevel level, Vec3 p) {
        Double gy = RuneKit.solidGround(level, V.up(p, 0.6), 4);
        return gy == null ? p : new Vec3(p.x, gy, p.z);
    }

    /** 시험: 사람마다 눈앞 큰 베기 수 (2026-11-26 평타는 그리지 않음 — 스킬만. 나란히 도는 시험이 섞이지 않게 사람마다) */
    private static final java.util.Map<java.util.UUID, Integer> EYE_SLASHES = new java.util.concurrent.ConcurrentHashMap<>();

    public static int eyeSlashesOf(ServerPlayer sp) {
        return sp == null ? 0 : EYE_SLASHES.getOrDefault(sp.getUUID(), 0);
    }

    /** 눈앞 큰 베기 (JobFx.slash 와 같은 짜임 — 묶음을 정함): 1인칭 = 카메라를 보는 칼빛 · 3인칭 · 다른 사람 = 앞을 보는 반달 + 바닐라 겹 */
    static void eyeSlash(ServerPlayer sp, PhantomFx.Lib L, double ahead, double down, double size, double tilt, double life) {
        EYE_SLASHES.merge(sp.getUUID(), 1, Integer::sum);
        Vec3 look = sp.getViewVector(1F);
        Vec3 at = V.add(V.add(sp.getEyePosition(), look, ahead), JobFx.screenUp(look), -down);
        PhantomFx.Moon moon = JobFx.slashMoon(sp, down, size, tilt, 0, 0);
        WyrdKit.slash(sp, L, at, look, tilt, size, moon);
        Vec3 vel = RuneKit.bodyVel(sp);
        PhantomFx.follow(PhantomFx.eyeSlash(sp, L, at, size * 1.8, tilt), vel, 11);
        PhantomFx.follow(PhantomFx.eyeSlash(sp, L, V.add(at, look, 0.15), size * 1.8 * 1.14, tilt + 7, 0.42), vel, 11);
        PhantomFx.moonCard(sp.level(), L, moon, PhantomFx.hide1p(sp), 0.95, life, vel);
        PhantomFx.moonCard(sp.level(), L, JobFx.slashMoon(sp, down, size * 1.14, tilt, 6, 7), PhantomFx.hide1p(sp), 0.45, life * 0.9, vel);
    }

    /** 불꽃 혀 n 개를 c 둘레 반지름 r 에 (용 묶음 flame — 한 번 솟았다 사그라듦) */
    static void flameRing(ServerLevel level, Vec3 c, double r, int n, double h) {
        double turn = Kit.rand() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = turn + i * Math.PI * 2 / n;
            Vec3 p = groundOf(level, new Vec3(c.x + Math.cos(a) * r, c.y + 0.5, c.z + Math.sin(a) * r));
            int at = i % 3;
            RuneKit.later(at, () -> PhantomFx.flame(level, PhantomFx.DRAGONL, p, h * (0.8 + Kit.rand() * 0.4), 2));
        }
    }

    static void sound(ServerLevel level, String name, Vec3 at, double pitch, double volume) {
        SkillFx.play(level, name, at, pitch, volume);
    }

    // ------------------------------------------------------------ 포르티아 (힘 · 불)

    /** 블레이즈 리프: 보는 쪽으로 7칸 도약 (꼭대기 2.4칸 · 12틱) · 착지 둘레 2.5칸 밀침 · 낙하 피해 없음 */
    static boolean blazeLeap(ServerPlayer sp, StatTree.Skill k, double P) {
        ServerLevel level = sp.level();
        PhantomFx.Lib L = PhantomFx.RAGE;
        Predicate<ServerPlayer> hide = PhantomFx.hide1p(sp);
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double dist = RuneKit.dashDistance(sp, f, 7, 1.2, 2.4);
        Vec3 tip = V.add(start, f, dist);
        Double gy = RuneKit.solidGround(level, new Vec3(tip.x, Math.max(start.y, tip.y) + 2.0, tip.z), 9);
        Vec3 land = new Vec3(tip.x, gy != null ? gy : start.y, tip.z);
        int air = 12;
        double peak = JobMove.hopPeak(sp, start, land, 2.4);
        // 박차고 오름: 발밑 작은 폭발 · 충격파 · 불꽃 혀 · 솟는 불티
        PhantomFx.boomSmall(level, L, V.up(start, 0.35), 1.3);
        PhantomFx.shock(level, L, start, 3.2, 1, hide);
        flameRing(level, start, 0.9, 4, 1.3);
        WyrdKit.rise(level, L, start, 0.5, 10, 1.4, hide);
        sound(level, "entity.blaze.shoot", start, 0.8, 0.8);
        sound(level, "item.firecharge.use", start, 0.7, 0.7);
        String life = RuneKit.lifeToken(sp);
        for (int t = 2; t < air; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 p = sp.position();
                PhantomFx.embers(level, L, V.up(p, 0.7), 4, 0.6, 0.35, 12);
                WyrdKit.trail(level, L, V.up(p, 0.9), f, 0.6, hide);
            });
        }
        RuneKit.leap(sp, land, peak, air, FIRE, FIRE_CORE, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 g = sp.position();
            // 내려앉음: 불덩이 · 넓은 충격파 · 그을린 땅 · 불꽃 혀 고리 · 밀침
            PhantomFx.boomSmall(level, L, V.up(g, 0.5), 2.0);
            PhantomFx.shock(level, L, g, 6.0, 1, hide);
            PhantomFx.crackS(level, L, g, 3.8);
            PhantomFx.ring(level, L, g, 2.5, 2, 5, hide, 0.9);
            flameRing(level, g, 1.7, 6, 1.6);
            for (LivingEntity e : foes(sp, g, 2.5, 2.0)) {
                push(e, V.sub(e.position(), g), 1.0, 0.4);
                PhantomFx.impact(level, L, Kit.bodyPoint(e), 0.7);
            }
            sound(level, "entity.generic.explode", g, 1.3, 0.55);
            sound(level, "entity.blaze.shoot", g, 0.6, 0.6);
        }, false);
        return true;
    }

    /** 스틸 차지: 6틱에 5칸 돌진 · 부딪힌 적 밀침 · 끝나고 1.5초 받는 피해 -20% */
    static boolean steelCharge(ServerPlayer sp, StatTree.Skill k, double P) {
        ServerLevel level = sp.level();
        PhantomFx.Lib L = PhantomFx.RAGE;
        Predicate<ServerPlayer> hide = PhantomFx.hide1p(sp);
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        int T = 6;
        double can = RuneKit.dashDistance(sp, f, 5, 0.3, 1.5);
        JobMove.run(sp, JobMove.to(0, T, JobMove.ahead(sp, 5, 0), JobMove.OUT));
        // 돌진 줄기 (칠한 dash) · 박차는 자리 섬광
        JobFx.dash(level, V.up(start, 1.0), V.up(V.add(start, f, Math.max(1, can)), 1.0), FIRE, FIRE_CORE, 0.35, JobFx.hide1p(sp));
        PhantomFx.impact(level, L, V.up(start, 0.4), 0.8);
        sound(level, "entity.player.attack.knockback", start, 0.7, 0.9);
        sound(level, "item.armor.equip_netherite", start, 0.8, 0.8);
        String life = RuneKit.lifeToken(sp);
        Set<Integer> knocked = new HashSet<>();
        for (int t = 1; t <= T; t++) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 p = sp.position();
                WyrdKit.trail(level, L, V.up(p, 1.0), f, 0.8, hide);
                PhantomFx.embers(level, L, V.up(p, 0.8), 3, 0.5, 0.3, 10);
                for (LivingEntity e : foes(sp, V.up(p, 0.9), 1.7, 1.6)) {
                    if (!knocked.add(e.getId())) continue;
                    Vec3 side = V.rightOf(f);
                    double s = V.dot(V.sub(e.position(), p), side) >= 0 ? 1 : -1;
                    push(e, V.add(f, side, 0.6 * s), 1.2, 0.35);
                    PhantomFx.impact(level, L, Kit.bodyPoint(e), 0.9);
                    sound(level, "entity.iron_golem.attack", e.position(), 1.2, 0.6);
                }
            });
        }
        RuneKit.later(T + 1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Guards.set(sp, STEEL_TICKS, 0.20, null, null);
            Vec3 g = sp.position();
            // 강철 몸: 발밑 고리 · 솟는 빛 고리 · 반짝임
            PhantomFx.ring(level, L, g, 1.1, 2, 24, hide, 0.85);
            WyrdKit.ringRise(level, L, g, 0.9, 14, 1.8, hide);
            PhantomFx.glint(level, L, V.up(g, 1.2), 0.6, 0.3, null, hide);
            sound(level, "block.anvil.land", g, 1.6, 0.35);
            // 2026-11-27 피해 감소 표시 (11-25 영상: 표시 없음): 둘레 강철 육각 판 껍질 · 주인은 알림 줄 · 맞으면 쨍
            steelShell(sp, STEEL_TICKS);
            STEEL.put(sp.getUUID(), new long[] {Tasks.now() + STEEL_TICKS, -100});
            Notify.notify(sp, "§6§l강철 몸 §f받는 피해 −20% §7" + String.format(java.util.Locale.ROOT, "%.1f초", STEEL_TICKS / 20.0), STEEL_TICKS);
        });
        return true;
    }

    /** 스틸 차지 강철 몸 (받는 피해 −20%) 틱 */
    static final int STEEL_TICKS = 30;
    /** 강철 판 테 빛 (힘 — 불빛 금) */
    static final double[] STEEL_RIM = {1.0, 0.62, 0.22};

    /**
     * 2026-11-27 스틸 차지의 받는 피해 −20% 표시 (사용자 "공용스킬로 넘어가자" — 11-25 영상: 피해 감소 표시 없음): 가슴 높이 둘레 (0.85칸) 에 강철 육각 판 여섯 (0.8칸 —
     * 두 높이로 엇갈려 벌집 껍질) 이 몸에서 펼쳐져 (3틱) 천천히 돌며 (틱마다 4°) 몸을 따라다니다 (ticks) 번쩍 커진 뒤 몸속으로 작아짐. 주인의 1인칭에는 안 보임 (화면을
     * 가리지 않게 — 주인은 알림 줄 · 맞을 때의 쨍)
     */
    static void steelShell(ServerPlayer sp, int ticks) {
        ServerLevel level = sp.level();
        mmorpg.fabric.vfx.Vfx.Fx fx = mmorpg.fabric.vfx.Vfx.at(level, sp.position(), ticks + 12, PhantomFx.hide1p(sp));
        double s = mmorpg.fabric.rune.RuneProps.scaleFor(0.8, mmorpg.fabric.rune.RuneProps.HEX_PANEL_H);
        int n = 6;
        double R = 0.85;
        mmorpg.fabric.vfx.Vfx.Prop[] ps = new mmorpg.fabric.vfx.Vfx.Prop[n];
        for (int i = 0; i < n; i++) {
            Vec3 d = V.unit(RuneKit.turn(Kit.facingOf(sp), i * 60.0));
            ps[i] = mmorpg.fabric.rune.RuneProps.prop(fx, mmorpg.fabric.rune.RuneProps.HEX_PANEL, V.up(V.add(sp.position(), d, R * 0.3), 1.05),
                    mmorpg.fabric.rune.RuneProps.MID, mmorpg.fabric.rune.RuneProps.facing(d, V.UP), s * 0.2, STEEL_RIM, RuneKit.WHITE);
        }
        lastShellPanels = n;
        for (int t = 1; t <= ticks; t++) {
            final int tt = t;
            fx.on(t, () -> {
                if (sp.isRemoved()) return;
                double grow = Math.min(1, tt / 3.0);
                Vec3 base = sp.position();
                for (int i = 0; i < n; i++) {
                    Vec3 d = V.unit(RuneKit.turn(Kit.facingOf(sp), i * 60.0 + tt * 4.0));
                    double y = (i % 2 == 0 ? 0.88 : 1.22) + 0.04 * Math.sin((tt + i * 3) * 0.45);
                    ps[i].to(V.up(V.add(base, d, R * (0.3 + 0.7 * grow)), y), mmorpg.fabric.rune.RuneProps.facing(d, V.UP), s * (0.2 + 0.8 * grow), 1);
                }
            });
        }
        fx.on(ticks + 1, () -> {
            for (mmorpg.fabric.vfx.Vfx.Prop p : ps) p.size(s * 1.25, 1);
        });
        fx.on(ticks + 2, () -> {
            Vec3 c = V.up(sp.position(), 1.05);
            for (mmorpg.fabric.vfx.Vfx.Prop p : ps) p.to(c, null, s * 0.1, 5);
        });
        fx.on(ticks + 7, () -> {
            for (mmorpg.fabric.vfx.Vfx.Prop p : ps) p.remove();
        });
    }

    /** 시험: 마지막 강철 껍질의 판 수 */
    public static int lastShellPanels;

    /** 강철 몸 동안 맞음 (RpgHealth.lossHook): 때린 쪽 몸 겉에 쨍 — 육각 빛 번쩍 · 불티 · 모루 소리 (4틱에 한 번) */
    private static void steelClang(ServerPlayer sp, net.minecraft.world.damagesource.DamageSource src) {
        long[] st = STEEL.get(sp.getUUID());
        long now = Tasks.now();
        if (st == null) return;
        if (st[0] <= now) {
            STEEL.remove(sp.getUUID());
            return;
        }
        if (now - st[1] < 4) return;
        st[1] = now;
        steelClangs++;
        ServerLevel level = sp.level();
        Vec3 c = V.up(sp.position(), 1.05);
        Vec3 from = src != null && src.getEntity() != null ? src.getEntity().position() : src != null && src.getSourcePosition() != null ? src.getSourcePosition() : null;
        Vec3 d = from != null ? V.flat(V.sub(from, sp.position())) : Kit.facingOf(sp);
        if (V.len(d) < 1e-4) d = Kit.facingOf(sp);
        d = V.unit(d);
        Vec3 at = V.add(c, d, 0.75);
        PhantomFx.glint(level, PhantomFx.RAGE, at, 0.5, 0.25, null, null);
        SkillFx.sparks(level, at, STEEL_RIM, 6, 3.0);
        sound(level, "block.anvil.place", at, 1.7, 0.35);
        sound(level, "item.shield.block", at, 1.2, 0.6);
    }

    /** 블레이즈 크로스: 앞 3.5칸 (120°) 을 불꽃으로 두 번 벰 (1틱 · 5틱 — 몫 반씩), 끝에 X 자 불꽃 */
    static boolean blazeCross(ServerPlayer sp, StatTree.Skill k, double P) {
        ServerLevel level = sp.level();
        PhantomFx.Lib L = PhantomFx.RAGE;
        String life = RuneKit.lifeToken(sp);
        double[] tilts = {-38, 38};
        int[] at = {1, 5};
        for (int i = 0; i < 2; i++) {
            double tilt = tilts[i];
            RuneKit.later(at[i], () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                eyeSlash(sp, L, 1.5, 0.25, 0.95, tilt, 0.35);
                sound(level, "entity.player.attack.sweep", sp.position(), 0.75, 0.9);
                sound(level, "item.firecharge.use", sp.position(), 1.3, 0.45);
                for (LivingEntity e : RuneKit.coneTargets(sp, 3.5, 120, 8)) {
                    if (hit(sp, e, k, P, 0.5) > 0) PhantomFx.hit(level, L, e, 0.9);
                }
            });
        }
        RuneKit.later(7, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 f = Kit.facingOf(sp);
            Vec3 c = V.add(V.up(sp.position(), 1.1), f, 2.0);
            PhantomFx.xslash(level, L, c, 3.4, Kit.rand() * 20 - 10);
            PhantomFx.flash(level, L, c, 2.2, PhantomFx.hide1p(sp));
            PhantomFx.embers(level, L, c, 12, 1.4, 0.4, 16);
            flameRing(level, V.add(sp.position(), f, 2.2), 1.1, 4, 1.4);
            sound(level, "entity.blaze.shoot", c, 1.1, 0.6);
        });
        return true;
    }

    /** 엠버 노바: 6틱 모은 뒤 둘레 3칸 불꽃 폭발 (조금 밀침) */
    static boolean emberNova(ServerPlayer sp, StatTree.Skill k, double P) {
        ServerLevel level = sp.level();
        PhantomFx.Lib L = PhantomFx.RAGE;
        Predicate<ServerPlayer> hide = PhantomFx.hide1p(sp);
        String life = RuneKit.lifeToken(sp);
        Vec3 g0 = sp.position();
        PhantomFx.ring(level, L, g0, 3.0, 4, 4, hide, 0.7); // 예고 고리
        sound(level, "entity.blaze.shoot", g0, 0.6, 0.6);
        for (int t = 0; t < 6; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 p = sp.position();
                WyrdKit.chargeTick(level, L, V.up(p, 1.0), tt / 6.0, 1.4, hide);
                WyrdKit.pull(level, L, V.up(p, 0.9), 3.0, 8, hide);
            });
        }
        RuneKit.later(6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 g = sp.position();
            PhantomFx.boom(level, L, V.up(g, 0.1), 2.6, hide);
            flameRing(level, g, 2.3, 8, 1.8);
            WyrdKit.ringRise(level, L, g, 3.0, 26, 2.2, hide);
            for (LivingEntity e : foes(sp, V.up(g, 0.9), 3.0, 2.5)) {
                if (hit(sp, e, k, P, 1.0) > 0) {
                    PhantomFx.hit(level, L, e, 1.0);
                    push(e, V.sub(e.position(), g), 0.5, 0.25);
                }
            }
            sound(level, "entity.generic.explode", g, 1.1, 0.7);
            sound(level, "item.firecharge.use", g, 0.6, 0.8);
        });
        return true;
    }

    // ------------------------------------------------------------ 라에타 (민첩 · 번개)

    /** 스파크 블링크: 보는 쪽 6칸 순간 이동 (벽 앞에서 멈춤) — 떠난 자리 · 닿은 자리 번개 고리 · 그 사이 일섬 */
    static boolean sparkBlink(ServerPlayer sp, StatTree.Skill k, double P) {
        ServerLevel level = sp.level();
        PhantomFx.Lib L = PhantomFx.THUNDER;
        Predicate<ServerPlayer> hide = PhantomFx.hide1p(sp);
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double dist = RuneKit.dashDistance(sp, f, 6, 0.3, 1.5);
        if (dist < 0.8) {
            Notify.notify(sp, "§7앞이 막혀 있습니다", 20);
            return false;
        }
        Vec3 tip = V.add(start, f, dist);
        Vec3 end = new Vec3(tip.x, RuneKit.groundAt(level, tip, start.y), tip.z);
        PhantomFx.flash(level, L, V.up(start, 1.0), 2.0, hide);
        JobFx.volt(level, V.up(start, 0.05), 1.1, VOLT, VOLT_CORE, 0.45, Opt.of());
        WyrdKit.sparks(level, L, V.up(start, 1.0), 10, 1.4, null);
        if (!RuneKit.moveTo(sp, end, null)) return false;
        Kit.noFall(sp, 20);
        PhantomFx.cut(level, L, V.up(start, 1.0), V.up(end, 1.0), 4, false, hide);
        WyrdKit.beam(level, L, V.up(start, 1.0), V.up(end, 1.0), hide);
        PhantomFx.flash(level, L, V.up(end, 1.0), 1.8, hide);
        JobFx.volt(level, V.up(end, 0.05), 1.4, VOLT, VOLT_CORE, 0.5, JobFx.hide1p(sp));
        RuneKit.later(2, () -> WyrdKit.sparks(level, L, V.up(end, 0.6), 8, 1.1, hide));
        sound(level, "entity.enderman.teleport", end, 1.6, 0.6);
        sound(level, "item.trident.thunder", end, 1.9, 0.25);
        return true;
    }

    /**
     * 라이트닝 스프린트: 4초 동안 이동 속도 +40% (신속 II) — 발밑 번개 고리 · 달리는 동안 불티. 2026-11-27 버프 표시 (11-25 영상: 고리 · 흰 반짝임뿐): 쓸 때 하늘에서
     * 번개가 몸에 내리꽂힘, 4초 동안 몸을 감싸는 번개 기운 (4틱마다 이어 그림 — 몸과 함께 나아감) · 달릴 때 몸 뒤로 번개 잔상 줄 · 주인 1인칭은 화면 가장자리 빠른 줄,
     * 끝날 때 번개가 흩어짐
     */
    static boolean lightningSprint(ServerPlayer sp, StatTree.Skill k, double P) {
        ServerLevel level = sp.level();
        PhantomFx.Lib L = PhantomFx.THUNDER;
        Predicate<ServerPlayer> hide = PhantomFx.hide1p(sp);
        RuneKit.effect(sp, "speed", SPRINT_TICKS, 1);
        Vec3 g = sp.position();
        JobFx.thunder(level, g, 7.0, VOLT, VOLT_CORE, 0.32, JobFx.hide1p(sp));
        JobFx.volt(level, V.up(g, 0.05), 1.3, VOLT, VOLT_CORE, 0.5, JobFx.hide1p(sp));
        PhantomFx.flash(level, L, V.up(g, 1.0), 1.6, hide);
        WyrdKit.ringRise(level, L, g, 0.8, 16, 2.0, hide);
        WyrdKit.sparks(level, L, V.up(g, 1.0), 10, 1.4, hide);
        sound(level, "item.trident.thunder", g, 2.0, 0.3);
        sound(level, "block.respawn_anchor.charge", g, 1.8, 0.5);
        sound(level, "entity.lightning_bolt.impact", g, 1.6, 0.35);
        Notify.notify(sp, "§e§l" + k.name() + " §f이동 속도 +40% §7" + (SPRINT_TICKS / 20) + "초", 40);
        String life = RuneKit.lifeToken(sp);
        for (int t = 2; t < SPRINT_TICKS; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 p = sp.position();
                Vec3 vel = RuneKit.bodyVel(sp);
                boolean moving = Math.hypot(vel.x, vel.z) > 2.0;
                if (tt % 3 == 0) WyrdKit.sparks(level, L, V.up(p, 0.25), 3, 0.7, hide);
                if (tt % 12 == 0) JobFx.volt(level, V.up(p, 0.05), 0.6, VOLT, VOLT_CORE, 0.3, JobFx.hide1p(sp));
                // 몸을 감싸는 번개 기운 (짧게 이어 그림 — 방향을 바꿔도 몸을 따라감)
                if (tt % 4 == 2) {
                    JobFx.aura(sp, VOLT, VOLT_CORE, 2.0, 0.3, 0.8);
                    sprintAuras++;
                }
                // 달리는 동안 몸 뒤로 번개 잔상 줄 · 1인칭 빠른 줄
                if (moving && tt % 2 == 0) WyrdKit.trail(level, L, V.up(V.add(p, V.flat(vel), -0.09), 0.95), V.flat(vel), 0.55, hide);
                if (tt % 10 == 2) JobFx.speed(sp, VOLT, VOLT_CORE, 0.55, moving ? 0.55 : 0.3, vel);
            });
        }
        RuneKit.later(SPRINT_TICKS, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 p = sp.position();
            WyrdKit.sparks(level, L, V.up(p, 1.0), 8, 1.2, hide);
            JobFx.volt(level, V.up(p, 0.05), 0.9, VOLT, VOLT_CORE, 0.3, JobFx.hide1p(sp));
            sound(level, "block.beacon.deactivate", p, 1.6, 0.4);
        });
        return true;
    }

    /** 라이트닝 스프린트 틱 (신속 II) */
    static final int SPRINT_TICKS = 80;
    /** 시험: 번개 기운을 그린 수 */
    public static int sprintAuras;

    /** 볼트 대거: 손에서 번개 단검이 앞 14칸을 날아 첫 적에 꽂힘 (칸/초 40) — 꽂힌 자리 하늘에서 번개 · 땅의 번개 고리 */
    static boolean voltDagger(ServerPlayer sp, StatTree.Skill k, double P) {
        ServerLevel level = sp.level();
        PhantomFx.Lib L = PhantomFx.THUNDER;
        Vec3 from = RuneKit.handOf(sp).hand();
        Vec3 look = sp.getViewVector(1F);
        LivingEntity target = null;
        double best = 1e9;
        for (Kit.LineHit h : RuneKit.lineTargets(sp, sp.getEyePosition(), look, 14, 0.9)) {
            if (!Kit.canHit(sp, h.e())) continue;
            double d = h.e().distanceToSqr(sp);
            if (d < best) {
                best = d;
                target = h.e();
            }
        }
        double reach = target != null ? Math.sqrt(best) : RuneKit.rayLength(level, sp.getEyePosition(), look, 14);
        Vec3 to = target != null ? Kit.bodyPoint(target) : V.add(sp.getEyePosition(), look, Math.max(1, reach));
        Vec3 u = V.unit(V.sub(to, from));
        double len = V.len(V.sub(to, from));
        int ticks = Math.max(1, (int) Math.ceil(len / 2.0));
        // 날아가는 번개 창 (칠한 spear — 가는 쪽으로 누움) · 꼬리
        PhantomFx.paintedWith(level, PhantomFx.PAINT.get("jspear2"), L, 0, from, Opt.of("dir", u, "speed", 40.0, "size", 0.9, "life", ticks / 20.0, "frames", 4.0,
                "fps", 12.0, "loop", true, "alpha", 1.0));
        WyrdKit.trail(level, L, from, to, ticks, 0.7, null);
        sound(level, "item.trident.throw", from, 1.6, 0.7);
        LivingEntity t0 = target;
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(ticks, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            PhantomFx.flash(level, L, to, 1.8, null);
            if (t0 != null && !t0.isRemoved() && t0.isAlive()) {
                Vec3 feet = t0.position();
                JobFx.thunder(level, feet, 5.0, VOLT, VOLT_CORE, 0.35, Opt.of());
                JobFx.volt(level, V.up(feet, 0.05), 1.2, VOLT, VOLT_CORE, 0.45, Opt.of());
                if (hit(sp, t0, k, P, 1.0) > 0) PhantomFx.hit(level, L, t0, 1.0);
                WyrdKit.sparks(level, L, Kit.bodyPoint(t0), 10, 1.3, null);
                sound(level, "entity.lightning_bolt.impact", feet, 1.5, 0.45);
            } else {
                WyrdKit.sparks(level, L, to, 6, 1.0, null);
            }
        });
        return true;
    }

    /** 플리커 샷: 뒤로 3칸 물러나며 (4틱) 2틱에 앞 10칸 번개 줄기 · 너비 1.5칸 (줄 위 모두) — 줄기를 따라 하늘에서 번개 셋 */
    static boolean flickerShot(ServerPlayer sp, StatTree.Skill k, double P) {
        ServerLevel level = sp.level();
        PhantomFx.Lib L = PhantomFx.THUNDER;
        Predicate<ServerPlayer> hide = PhantomFx.hide1p(sp);
        Vec3 f = Kit.facingOf(sp);
        Vec3 start = sp.position();
        JobMove.run(sp, JobMove.to(0, 4, JobMove.ahead(sp, -3, 0), JobMove.OUT));
        PhantomFx.flash(level, L, V.up(start, 1.0), 1.4, hide);
        WyrdKit.sparks(level, L, V.up(start, 0.6), 6, 1.0, hide);
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(2, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 a = V.add(V.up(sp.position(), 1.1), f, 0.6);
            double len = Math.max(1.5, RuneKit.rayLength(level, a, f, 10));
            Vec3 b = V.add(a, f, len);
            PhantomFx.cut(level, L, a, b, 6, true, null);
            WyrdKit.beam(level, L, a, b, null);
            sound(level, "item.trident.thunder", a, 1.4, 0.45);
            sound(level, "entity.firework_rocket.blast", a, 0.8, 0.7);
            for (Kit.LineHit h : RuneKit.lineTargets(sp, a, f, len, 1.5)) {
                LivingEntity e = h.e();
                if (hit(sp, e, k, P, 1.0) > 0) {
                    PhantomFx.hit(level, L, e, 0.9);
                    WyrdKit.sparks(level, L, Kit.bodyPoint(e), 6, 1.0, null);
                }
            }
            for (int i = 0; i < 3; i++) {
                Vec3 g = groundOf(level, V.add(V.up(a, -1.1), f, len * (0.3 + 0.3 * i)));
                RuneKit.later(1 + i * 2, () -> {
                    JobFx.thunder(level, g, 4.5, VOLT, VOLT_CORE, 0.3, Opt.of());
                    JobFx.volt(level, V.up(g, 0.05), 0.9, VOLT, VOLT_CORE, 0.35, Opt.of());
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 니바리아 (마력 · 얼음)

    /** 아이스 슬라이드: 12틱 (0.6초) 에 8칸 미끄러짐 · 지나간 자리 얼음 길 (서리 자국 — 3초) */
    static boolean iceSlide(ServerPlayer sp, StatTree.Skill k, double P) {
        ServerLevel level = sp.level();
        PhantomFx.Lib L = PhantomFx.FROST;
        Predicate<ServerPlayer> hide = PhantomFx.hide1p(sp);
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        int T = 12;
        JobMove.run(sp, JobMove.to(0, T, JobMove.ahead(sp, 8, 0), JobMove.OUT));
        PhantomFx.flash(level, L, V.up(start, 0.8), 1.6, hide);
        PhantomFx.shards(level, L, V.up(start, 0.4), 6, 1.0, 0.32, 14);
        sound(level, "block.glass.break", start, 1.6, 0.4);
        sound(level, "block.powder_snow.break", start, 0.9, 0.8);
        String life = RuneKit.lifeToken(sp);
        for (int t = 1; t <= T; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 p = sp.position();
                WyrdKit.trail(level, L, V.up(p, 0.5), f, 0.7, hide);
                if (tt % 2 == 0) {
                    Vec3 g = groundOf(level, p);
                    PhantomFx.crackS(level, L, g, 1.8); // 얼음 길 (서리 금 — 40틱 머묾)
                    RuneKit.later(30, () -> PhantomFx.crackS(level, L, g, 1.6)); // 3초까지 다시 (사그라들며)
                    WyrdKit.snow(level, V.up(g, 0.2), 3, 0.5, null);
                }
            });
        }
        RuneKit.later(T + 1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 g = sp.position();
            PhantomFx.shards(level, L, V.up(g, 0.5), 8, 1.2, 0.35, 16);
            PhantomFx.ring(level, L, g, 1.2, 2, 6, hide, 0.8);
            sound(level, "block.amethyst_block.break", g, 1.4, 0.6);
        });
        return true;
    }

    /** 크리스털 시프트: 뒤로 6칸 순간 이동 · 떠난 자리 얼음 결정이 솟았다 깨져 둘레 2칸 구속 I 1.5초 */
    static boolean crystalShift(ServerPlayer sp, StatTree.Skill k, double P) {
        ServerLevel level = sp.level();
        PhantomFx.Lib L = PhantomFx.FROST;
        Predicate<ServerPlayer> hide = PhantomFx.hide1p(sp);
        Vec3 start = sp.position();
        Vec3 back = V.scale(Kit.facingOf(sp), -1);
        double dist = RuneKit.dashDistance(sp, back, 6, 0.3, 1.5);
        Vec3 tip = V.add(start, back, dist);
        Vec3 end = new Vec3(tip.x, RuneKit.groundAt(level, tip, start.y), tip.z);
        // 떠난 자리: 솟는 얼음 결정 (칠한 spike) → 6틱에 깨짐
        JobFx.spikeLife(level, start, 1.0, 0.12, 0.2, Opt.of(), ICE, ICE_CORE);
        JobFx.halo(level, V.up(start, 0.05), 2.0, ICE, ICE_CORE, 0.5, Opt.of());
        if (dist >= 0.8) {
            RuneKit.moveTo(sp, end, null);
            Kit.noFall(sp, 20);
            PhantomFx.flash(level, L, V.up(end, 1.0), 1.6, hide);
            PhantomFx.ring(level, L, end, 1.0, 2, 6, hide, 0.8);
            WyrdKit.beam(level, L, V.up(start, 1.0), V.up(end, 1.0), hide);
        }
        sound(level, "block.amethyst_block.chime", start, 1.2, 1.0);
        RuneKit.later(6, () -> {
            PhantomFx.shards(level, L, V.up(start, 0.8), 14, 2.0, 0.42, 18);
            PhantomFx.flash(level, L, V.up(start, 0.9), 2.2, null);
            PhantomFx.shock(level, L, start, 4.2, 1);
            WyrdKit.snow(level, V.up(start, 0.6), 10, 1.4, null);
            for (LivingEntity e : foes(sp, V.up(start, 0.8), 2.0, 2.0)) {
                RuneKit.effect(e, "slowness", 30, 0);
                JobFx.frost(e, ICE, ICE_CORE, 30);
            }
            sound(level, "block.glass.break", start, 1.0, 0.8);
        });
        return true;
    }

    /** 크라이오 펄스: 4틱 모은 뒤 둘레 3.5칸 냉기 파동 · 구속 I 1초 */
    static boolean cryoPulse(ServerPlayer sp, StatTree.Skill k, double P) {
        ServerLevel level = sp.level();
        PhantomFx.Lib L = PhantomFx.FROST;
        Predicate<ServerPlayer> hide = PhantomFx.hide1p(sp);
        String life = RuneKit.lifeToken(sp);
        Vec3 g0 = sp.position();
        JobFx.halo(level, V.up(g0, 0.05), 3.5, ICE, ICE_CORE, 0.45, JobFx.hide1p(sp));
        sound(level, "block.powder_snow.break", g0, 0.6, 0.8);
        for (int t = 0; t < 4; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                WyrdKit.chargeTick(level, L, V.up(sp.position(), 1.0), tt / 4.0, 1.3, hide);
            });
        }
        RuneKit.later(4, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 g = sp.position();
            PhantomFx.shock(level, L, g, 7.2, 1, hide);
            PhantomFx.ring(level, L, g, 3.5, 3, 4, hide, 0.9);
            PhantomFx.shards(level, L, V.up(g, 0.8), 16, 3.0, 0.4, 18);
            WyrdKit.snow(level, V.up(g, 0.8), 14, 3.0, null);
            WyrdKit.ringRise(level, L, g, 3.3, 24, 1.6, hide);
            for (LivingEntity e : foes(sp, V.up(g, 0.9), 3.5, 2.5)) {
                if (hit(sp, e, k, P, 1.0) > 0) {
                    RuneKit.effect(e, "slowness", 20, 0);
                    JobFx.frost(e, ICE, ICE_CORE, 20);
                    PhantomFx.hit(level, L, e, 0.8);
                }
            }
            sound(level, "block.glass.break", g, 0.7, 0.9);
            sound(level, "entity.player.hurt_freeze", g, 0.8, 0.6);
        });
        return true;
    }

    /** 하일스톤: 보는 자리 (16칸 안) 에 예고 고리 → 하늘에서 얼음 창이 떨어져 10틱에 둘레 2칸 */
    static boolean hailstone(ServerPlayer sp, StatTree.Skill k, double P) {
        ServerLevel level = sp.level();
        PhantomFx.Lib L = PhantomFx.FROST;
        Vec3 aim = RuneKit.groundAim(sp, 16);
        PhantomFx.ring(level, L, aim, 2.0, 3, 8, null, 0.8);
        JobFx.halo(level, V.up(aim, 0.05), 2.0, ICE, ICE_CORE, 0.5, Opt.of());
        Vec3 sky = V.up(aim, 9);
        // 2026-11-25 큰 우박 = 3D 얼음 결정 (만년빙하 얼음 가시 모델 · 끝이 아래 · 2.6칸): 하늘에서 9틱에 떨어져 끝이 땅에 닿음 → 깨지며 가라앉음.
        // 예전 칠한 얼음 창 (icelance 1.6칸) 은 영상 (CI 45586370783) 에서 가는 바늘처럼 보였음 — 꼬리 빛으로만 남김
        double hh = 2.6;
        double hs = mmorpg.fabric.rune.RuneProps.scaleFor(hh, mmorpg.fabric.rune.RuneProps.ICE_SPIKE_H);
        var hailFx = mmorpg.fabric.rune.RuneProps.fx(level, sky, 18);
        var hail = mmorpg.fabric.rune.RuneProps.prop(hailFx, mmorpg.fabric.rune.RuneProps.ICE_SPIKE, V.up(aim, 9 + hh), new double[] {8, 0, 8},
                mmorpg.fabric.rune.RuneProps.along(V.DOWN, new Vec3(1, 0, 0)), hs);
        hailFx.on(1, () -> hail.to(V.up(aim, hh - 0.1), null, hs, 9));
        hailFx.on(11, () -> hail.to(V.up(aim, hh * 0.2), null, hs * 0.45, 4));
        hailFx.on(15, hail::remove);
        PhantomFx.paintedWith(level, PhantomFx.PAINT.get("jicelance"), L, 0, sky, Opt.of("dir", V.DOWN, "speed", 18.0, "size", 0.9, "life", 0.5, "frames", 4.0, "fps",
                12.0, "loop", true, "alpha", 0.6));
        WyrdKit.trail(level, L, sky, V.up(aim, 0.5), 10, 1.2, null);
        sound(level, "block.powder_snow.fall", aim, 0.6, 1.0);
        RuneKit.later(10, () -> {
            PhantomFx.boom(level, L, V.up(aim, 0.2), 1.8, null);
            PhantomFx.shards(level, L, V.up(aim, 0.6), 14, 2.2, 0.45, 20);
            for (int i = 0; i < 3; i++) {
                double a = Kit.rand() * Math.PI * 2;
                Vec3 g = groundOf(level, new Vec3(aim.x + Math.cos(a) * 1.0, aim.y + 0.5, aim.z + Math.sin(a) * 1.0));
                JobFx.spikeLife(level, g, 0.7 + Kit.rand() * 0.3, 0.1, 0.5, Opt.of(), ICE, ICE_CORE);
            }
            WyrdKit.snow(level, V.up(aim, 0.8), 12, 2.0, null);
            for (LivingEntity e : foes(sp, V.up(aim, 0.8), 2.0, 2.5)) {
                if (hit(sp, e, k, P, 1.0) > 0) PhantomFx.hit(level, L, e, 1.0);
            }
            sound(level, "block.glass.break", aim, 0.6, 1.0);
            sound(level, "entity.generic.explode", aim, 1.6, 0.4);
        });
        return true;
    }

    // ------------------------------------------------------------ 그라비스 (체력 · 바위)

    /** 구르는 쪽: 움직이는 쪽 (몸의 수평 속도 — 칸/초 1.5 넘게) · 아니면 보는 쪽 */
    static Vec3 rollDir(ServerPlayer sp) {
        Vec3 v = RuneKit.bodyVel(sp);
        return Math.hypot(v.x, v.z) > 1.5 ? V.flat(v) : Kit.facingOf(sp);
    }

    /** 보울더 롤: 누르는 (움직이는) 쪽으로 8틱에 5칸 구르기 · 부딪힌 적 밀침 · 구르는 동안 받는 피해 -30% */
    static boolean boulderRoll(ServerPlayer sp, StatTree.Skill k, double P) {
        ServerLevel level = sp.level();
        PhantomFx.Lib L = PhantomFx.BEAR;
        Predicate<ServerPlayer> hide = PhantomFx.hide1p(sp);
        Vec3 start = sp.position();
        Vec3 d = rollDir(sp);
        int T = 8;
        double can = RuneKit.dashDistance(sp, d, 5, 0.3, 1.5);
        Vec3 end = V.add(start, d, can);
        JobMove.run(sp, JobMove.to(0, T, JobMove.at(end), JobMove.OUT));
        Guards.set(sp, T + 2, 0.30, null, null);
        boulderBall(sp, d, T); // 2026-11-27 몸을 감싸고 구르는 3D 바위 (11-25 영상: 구르는 바위 그림 없음)
        PhantomFx.dust(level, L, start, 1.6);
        sound(level, "block.stone.break", start, 0.7, 0.9);
        sound(level, "entity.ravager.step", start, 0.8, 0.8);
        String life = RuneKit.lifeToken(sp);
        Set<Integer> knocked = new HashSet<>();
        for (int t = 1; t <= T; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 p = sp.position();
                if (tt % 2 == 0) {
                    PhantomFx.crackS(level, L, groundOf(level, p), 1.6);
                    PhantomFx.rocks(level, V.up(p, 0.3), 2, 2.0, 0.25);
                }
                WyrdKit.trail(level, L, V.up(p, 0.5), d, 0.6, hide);
                for (LivingEntity e : foes(sp, V.up(p, 0.8), 1.6, 1.5)) {
                    if (!knocked.add(e.getId())) continue;
                    push(e, V.add(d, V.sub(e.position(), p), 0.5), 1.1, 0.45);
                    PhantomFx.impact(level, L, Kit.bodyPoint(e), 0.9);
                    sound(level, "entity.iron_golem.attack", e.position(), 0.8, 0.7);
                }
            });
        }
        RuneKit.later(T + 1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            PhantomFx.dust(level, L, sp.position(), 1.8);
            PhantomFx.shock(level, L, sp.position(), 2.6, 1, hide);
        });
        return true;
    }

    /** 보울더 롤 바위 지름 (칸 — 몸 1.8칸을 다 덮음) */
    static final double BOULDER_D = 2.3;
    /** 시험: 마지막 구르는 바위가 돈 각 (라디안 — 구른 거리 ÷ 반지름) */
    public static double lastBoulderTurn;

    /**
     * 2026-11-27 보울더 롤의 3D 구르는 바위 (사용자 "공용스킬로 넘어가자" — 11-25 영상: 구르는 바위 그림 없음 · 불티 · 땅 갈라짐뿐): 몸을 감싸는 바위 (지름 2.3칸 — 금 사이
     * 호박빛) 가 몸에서 부풀어 (2틱) 구르는 쪽 축 (위 × 구르는 쪽) 으로 구른 거리만큼 돌며 (구른 거리 ÷ 반지름) 몸을 따라감 (틱마다) → 끝에서 부풀었다 금이 가며
     * 부서짐 (작은 바위 넷이 바깥으로 튀어 떨어짐). 주인의 1인칭에는 안 보임 (화면을 바위 속이 가리지 않게)
     */
    static void boulderBall(ServerPlayer sp, Vec3 d, int T) {
        ServerLevel level = sp.level();
        mmorpg.fabric.vfx.Vfx.Fx fx = mmorpg.fabric.vfx.Vfx.at(level, sp.position(), T + 26, PhantomFx.hide1p(sp));
        double s = mmorpg.fabric.rune.RuneProps.scaleFor(BOULDER_D, mmorpg.fabric.rune.RuneProps.BOULDER_D);
        double r = BOULDER_D / 2;
        long seed = (long) (Kit.rand() * 1e9);
        org.joml.Quaternionf q0 = mmorpg.fabric.rune.RuneProps.tumble(seed, 0, 0);
        Vec3 axis = V.unit(V.cross(V.UP, d));
        Vec3 start = sp.position();
        mmorpg.fabric.vfx.Vfx.Prop p = mmorpg.fabric.rune.RuneProps.prop(fx, mmorpg.fabric.rune.RuneProps.BOULDER, V.up(start, r - 0.1),
                mmorpg.fabric.rune.RuneProps.MID, q0, s * 0.45, ROCK_CORE);
        lastBoulderTurn = 0;
        for (int t = 1; t <= T; t++) {
            final int tt = t;
            fx.on(t, () -> {
                Vec3 pos = sp.position();
                double ang = Math.max(0, V.dot(V.sub(pos, start), d)) / r;
                lastBoulderTurn = ang;
                org.joml.Quaternionf q = new org.joml.Quaternionf().rotateAxis((float) ang, (float) axis.x, (float) axis.y, (float) axis.z).mul(q0);
                p.to(V.up(pos, r - 0.1), q, s * (tt <= 2 ? 0.45 + 0.55 * tt / 2.0 : 1.0), 1);
            });
        }
        // 부서짐: 부풀었다 (1틱) 금 사이 빛이 번쩍 → 작아지며 땅으로 (5틱), 작은 바위 넷이 바깥으로 튐
        fx.on(T + 1, () -> p.size(s * 1.1, 1));
        fx.on(T + 2, () -> {
            Vec3 c = sp.position();
            p.to(V.up(c, 0.3), null, s * 0.25, 5);
            PhantomFx.rocks(level, V.up(c, 0.9), 8, 3.2, 0.35);
            for (int i = 0; i < 4; i++) {
                final Vec3 out = V.unit(RuneKit.turn(d, -135 + i * 90 + (Kit.rand() - 0.5) * 30));
                final long sd = seed + 7 + i;
                final double cs = s * (0.28 + Kit.rand() * 0.08);
                final Vec3 from = V.up(V.add(c, out, 0.5), 0.9);
                final Vec3 land = new Vec3(c.x + out.x * (2.2 + Kit.rand()), groundOf(level, c).y + 0.15, c.z + out.z * (2.2 + Kit.rand()));
                mmorpg.fabric.vfx.Vfx.Prop q = mmorpg.fabric.rune.RuneProps.prop(fx, mmorpg.fabric.rune.RuneProps.BOULDER, from,
                        mmorpg.fabric.rune.RuneProps.MID, mmorpg.fabric.rune.RuneProps.tumble(sd, 0, 0), cs, ROCK_CORE);
                for (int u = 1; u <= 8; u++) {
                    final double w = u / 8.0;
                    final int uu = u;
                    fx.on(T + 2 + u, () -> q.to(V.add(V.lerp(from, land, w), V.UP, 1.3 * 4 * w * (1 - w)), mmorpg.fabric.rune.RuneProps.tumble(sd, uu, 40), -1, 1));
                }
                fx.on(T + 14, () -> q.to(V.up(land, -0.4), null, cs * 0.4, 6));
                fx.on(T + 21, q::remove);
            }
        });
        fx.on(T + 8, p::remove);
    }

    /** 록 런치: 발밑 바위 기둥이 솟아 위로 7칸 (8틱) → 떨어져 내려앉음 · 낙하 피해 없음 · 착지 둘레 2.5칸 밀침 */
    static boolean rockLaunch(ServerPlayer sp, StatTree.Skill k, double P) {
        ServerLevel level = sp.level();
        PhantomFx.Lib L = PhantomFx.BEAR;
        Predicate<ServerPlayer> hide = PhantomFx.hide1p(sp);
        Vec3 start = sp.position();
        double ground = start.y;
        // 2026-11-25 바위 기둥 = 3D 바위 가시 (금 사이 호박빛): 끝이 발밑을 따라 7칸 솟음 (8틱 — 몸과 같은 빠르기) → 내려앉는 동안 함께 가라앉음.
        // 예전 칠한 기둥 (pillar — 주황 빛기둥) 은 영상 (CI 45586370783) 에서 바위가 아니라 불기둥처럼 보였음
        double ph = 7.6;
        double ps = mmorpg.fabric.rune.RuneProps.scaleFor(ph, mmorpg.fabric.rune.RuneProps.ROCK_SPIKE_H);
        var colFx = mmorpg.fabric.rune.RuneProps.fx(level, start, 36);
        var col = mmorpg.fabric.rune.RuneProps.prop(colFx, mmorpg.fabric.rune.RuneProps.ROCK_SPIKE, V.up(start, -ph), new double[] {8, 0, 8},
                mmorpg.fabric.rune.RuneProps.along(V.UP, new Vec3(Math.cos(Kit.rand() * 6.3), 0, Math.sin(Kit.rand() * 6.3))), ps, ROCK_CORE);
        colFx.on(1, () -> col.to(V.up(start, 7.0 - ph + 0.05), null, ps, 8));
        colFx.on(14, () -> col.to(V.up(start, -ph * 0.95), null, ps * 0.85, 12));
        colFx.on(27, col::remove);
        for (int i = 0; i < 3; i++) {
            double a = Kit.rand() * Math.PI * 2;
            mmorpg.fabric.rune.RuneProps.rockSpike(level, groundOf(level, new Vec3(start.x + Math.cos(a) * 1.3, start.y + 0.5, start.z + Math.sin(a) * 1.3)),
                    1.0 + Kit.rand() * 0.5, 10, Math.toDegrees(a), ROCK_CORE);
        }
        JobFx.spikeLife(level, start, 1.4, 0.1, 0.4, JobFx.hide1p(sp), ROCK, ROCK_CORE);
        PhantomFx.crackS(level, L, start, 2.6);
        PhantomFx.rocks(level, V.up(start, 0.4), 6, 3.0, 0.3);
        PhantomFx.shock(level, L, start, 3.0, 1, hide);
        sound(level, "block.basalt.break", start, 0.6, 1.0);
        sound(level, "block.deepslate.break", start, 0.7, 0.9);
        Kit.noFall(sp, 60);
        JobMove.run(sp, JobMove.to(0, 8, JobMove.up(sp, 7), JobMove.OUT), JobMove.hold(8, 12), JobMove.to(12, 22, JobMove.down(sp, ground), JobMove.IN));
        String life = RuneKit.lifeToken(sp);
        for (int t = 1; t <= 8; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                PhantomFx.rocks(level, V.up(sp.position(), -0.2), 1, 1.5, 0.22);
            });
        }
        RuneKit.later(23, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 g = sp.position();
            PhantomFx.smash(level, L, g, 2.5, hide);
            PhantomFx.boomSmall(level, L, V.up(g, 0.4), 1.4);
            for (LivingEntity e : foes(sp, V.up(g, 0.8), 2.5, 2.0)) {
                push(e, V.sub(e.position(), g), 1.0, 0.45);
                PhantomFx.impact(level, L, Kit.bodyPoint(e), 0.8);
            }
            sound(level, "block.anvil.land", g, 0.6, 0.45);
            sound(level, "entity.generic.explode", g, 1.6, 0.4);
        });
        return true;
    }

    /** 록 스파이크: 앞 5칸에 바위 가시 셋이 차례로 솟음 (2 · 4 · 6틱 — 몫 셋으로) · 맞은 것을 띄움 */
    static boolean rockSpike(ServerPlayer sp, StatTree.Skill k, double P) {
        ServerLevel level = sp.level();
        PhantomFx.Lib L = PhantomFx.BEAR;
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double reach = Math.max(2, RuneKit.dashDistance(sp, f, 5, 0.3));
        Set<Integer> hitOnce = new HashSet<>();
        PhantomFx.crackS(level, L, groundOf(level, V.add(start, f, 1.0)), 1.8);
        sound(level, "block.stone.break", start, 0.6, 1.0);
        for (int i = 0; i < 3; i++) {
            double dist = reach * (i + 1) / 3.0;
            Vec3 g = groundOf(level, V.add(start, f, dist));
            RuneKit.later(2 + i * 2, () -> {
                JobFx.spikeLife(level, g, 1.0 + 0.15 * dist / reach, 0.1, 0.45, Opt.of(), ROCK, ROCK_CORE);
                PhantomFx.crackS(level, L, g, 2.4);
                PhantomFx.dust(level, L, g, 1.6);
                PhantomFx.rocks(level, V.up(g, 0.4), 4, 2.6, 0.28);
                for (LivingEntity e : foes(sp, V.up(g, 0.8), 1.6, 2.0)) {
                    if (!hitOnce.add(e.getId())) continue;
                    if (hit(sp, e, k, P, 1.0) > 0) {
                        PhantomFx.hit(level, L, e, 0.9);
                        if (JobKit.pushable(e)) Kit.launch(e, f.x * 0.15, 0.62, f.z * 0.15);
                    }
                }
                sound(level, "block.pointed_dripstone.land", g, 0.7, 0.9);
                sound(level, "block.basalt.break", g, 0.8, 0.8);
            });
        }
        return true;
    }

    /** 애프터쇼크: 둘레 3칸 충격 두 번 (3틱 · 15틱 — 0.6초 간격, 몫 반씩) */
    static boolean aftershock(ServerPlayer sp, StatTree.Skill k, double P) {
        ServerLevel level = sp.level();
        PhantomFx.Lib L = PhantomFx.BEAR;
        Predicate<ServerPlayer> hide = PhantomFx.hide1p(sp);
        String life = RuneKit.lifeToken(sp);
        Vec3 g0 = sp.position();
        PhantomFx.ring(level, L, g0, 3.0, 3, 16, hide, 0.7);
        int[] at = {3, 15};
        for (int i = 0; i < 2; i++) {
            int n = i;
            RuneKit.later(at[i], () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 g = sp.position();
                PhantomFx.smash(level, L, g, 3.0, hide);
                if (n == 1) {
                    PhantomFx.boomSmall(level, L, V.up(g, 0.3), 1.6);
                    PhantomFx.paintedWith(level, PhantomFx.PAINT.get("jquake"), L, 0, V.up(g, 0.05), Opt.of("dir", V.UP, "size", 3.2, "life", 0.6, "frames", 6.0,
                            "fps", 10.0, "alpha", 0.9, "view", 1.0, "owner", (double) sp.getId()));
                }
                PhantomFx.rocks(level, V.up(g, 0.3), 6, 3.2, 0.3);
                for (LivingEntity e : foes(sp, V.up(g, 0.8), 3.0, 2.0)) {
                    if (hit(sp, e, k, P, 0.5) > 0) {
                        PhantomFx.hit(level, L, e, 0.8);
                        push(e, V.sub(e.position(), g), 0.35, 0.3);
                    }
                }
                sound(level, "entity.generic.explode", g, n == 0 ? 1.4 : 0.9, 0.5);
                sound(level, "block.deepslate.break", g, 0.6, 1.0);
            });
        }
        return true;
    }
}
