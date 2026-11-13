package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Combat;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.IceFx;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-07 재설계안 3판 — 전사 반전 프로스트 레이더 (글래디에이터 대신 · 사용자가 고른 컨셉 "서리 도끼를 던져 박고 되불러 얼린 적을 깨부숨") 특성 「빙결」 · 요툰 그립
 * (claude/job-advance-redo.md "2026-11-07 도감 표시 고침", 서버 — jobs.json advance.frostraider):
 * <ul>
 * <li>서리: 도끼 평타 (무기 훅) · 직업 스킬 (JobKit.hit 뒤) 이 맞힌 적에 서리 +1 (스킬은 {@link #addFrost} 로 더) — 최대 max · frostFor 틱 (맞힐 때마다 다시).
 * 하나당 이동 속도 −slowPer (구속 효과 단계 — 서리 2 · 4 에서 한 단계씩)</li>
 * <li>얼어붙음: 서리가 max 가 되면 freezeFor 틱 묶임 (RuneKit.stun · 얼음 결정 JobFx.frost) — 보스는 대신 bossFor 틱 받는 피해 + bossTaken (RpgCombat.takenMult)</li>
 * <li>깨짐: 이 사람이 얼린 적을 직업 스킬로 치면 그 타 × (1 + shatter) (JobKit.hit 배율) → 둘레 shardRadius 칸의 다른 적에 얼음 조각 (위력 × shard) · 서리 0 · 풀림.
 * 샤터 · 핌불윈터는 제 결정을 제 숫자로 깨므로 그 타 앞에 {@link #thaw} (덤 없음)</li>
 * <li>요툰 그립 (태세): 도끼 평타가 맞으면 평타 × echo 를 더함 (무기 덤) · 서리 +1 더 · 휘두를 때마다 등 뒤 서리 거인의 어깨에서 거대한 얼음 도끼가 머리 위를 넘어
 * 같은 쪽으로 내려침 (2026-11-13 3D 빙하 도끼 · 3D 얼음 — {@link #giantSwing})</li>
 * </ul>
 */
public final class JobTraits16 {
    private JobTraits16() {}

    static final String FR = "frostraider";
    static final String JOTUNN = "frostraider_jotunn";
    private static final String KEY_JOTUNN = "frostraider_jotunn";
    /** 얼음 빛 (겉 · 속 — 직업 색과 같음: 옅은 얼음빛 · 서리 흰빛) */
    static final double[] ICE = {0.6, 0.9, 1.0};
    static final double[] FROST_WHITE = {0.94, 0.99, 1.0};

    /** 쓴 사람 → (적 → {서리, 끝 틱}) */
    private static final Map<UUID, Map<UUID, long[]>> FROST = new HashMap<>();
    /** 얼어붙은 적 → {끝 틱, 얼린 사람 (UUID 해시 두 개 — most · least)} */
    private static final Map<UUID, long[]> FROZEN = new HashMap<>();
    /** 보스 받는 피해 증가 → 끝 틱 */
    private static final Map<UUID, Long> BOSS = new HashMap<>();
    /** 쓴 사람 → 마지막으로 친 적 (게이지) */
    private static final Map<UUID, UUID> LAST = new HashMap<>();

    static void init() {
        RpgCombat.addScaledBonusHook(JobTraits16::jotunnBonus);
        RpgCombat.addWeaponHitHook(JobTraits16::onWeaponHit);
        java.util.function.BiConsumer<ServerPlayer, Weapon> sh = RpgCombat.swingHook;
        RpgCombat.swingHook = (sp, w) -> {
            sh.accept(sp, w);
            onSwing(sp, w);
        };
        java.util.function.ToDoubleFunction<LivingEntity> tm = RpgCombat.takenMult;
        RpgCombat.takenMult = e -> tm.applyAsDouble(e) * bossTaken(e);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            FROST.clear();
            FROZEN.clear();
            BOSS.clear();
            LAST.clear();
        });
    }

    static Jobs.Job jobIf(ServerPlayer sp) {
        Jobs.Job j = JobSystem.job(sp);
        return j != null && FR.equals(j.id()) ? j : null;
    }

    static double trait(Jobs.Job j, String key, double fallback) {
        return j == null || j.trait() == null ? fallback : j.trait().def().numOr(key, fallback);
    }

    static void reset(ServerPlayer sp) {
        FROST.remove(sp.getUUID());
        LAST.remove(sp.getUUID());
    }

    static void forget(UUID id) {
        FROST.remove(id);
        LAST.remove(id);
        FROZEN.remove(id);
        BOSS.remove(id);
    }

    /** 40틱마다 (JobTraits): 끝난 서리 · 얼어붙음 · 보스 받는 피해를 지움 (쓰러진 적이 남지 않게) */
    static void tickGlobal(long now) {
        if (!FROZEN.isEmpty()) FROZEN.values().removeIf(v -> v[0] < now);
        if (!BOSS.isEmpty()) BOSS.values().removeIf(v -> v < now);
        for (Map<UUID, long[]> m : FROST.values()) m.values().removeIf(v -> v[1] < now);
    }

    // ------------------------------------------------------------ 서리

    /** e 의 서리 (이 사람이 쌓은 — 끝났으면 0) */
    public static int frost(ServerPlayer sp, LivingEntity e) {
        Map<UUID, long[]> m = FROST.get(sp.getUUID());
        long[] v = m == null || e == null ? null : m.get(e.getUUID());
        return v == null || v[1] < Tasks.now() ? 0 : (int) v[0];
    }

    /** 시험 · 영상: 서리를 정함 (얼리지는 않음) */
    public static void setFrost(ServerPlayer sp, LivingEntity e, int n) {
        Jobs.Job j = jobIf(sp);
        FROST.computeIfAbsent(sp.getUUID(), k -> new HashMap<>()).put(e.getUUID(), new long[] {Math.max(0, n), Tasks.now() + (long) trait(j, "frostFor", 120)});
        LAST.put(sp.getUUID(), e.getUUID());
        JobTraits.sendGauge(sp, false);
    }

    /** e 에 서리 + n (최대가 되면 얼어붙음 — 이미 얼어 있으면 그대로) */
    static void addFrost(ServerPlayer sp, Jobs.Job j, LivingEntity e, int n) {
        if (j == null || e == null || n <= 0 || !e.isAlive() || e.isRemoved()) return;
        int max = Math.max(1, j.trait() == null ? 5 : j.trait().max());
        long now = Tasks.now();
        Map<UUID, long[]> m = FROST.computeIfAbsent(sp.getUUID(), k -> new HashMap<>());
        long[] v = m.get(e.getUUID());
        int was = v == null || v[1] < now ? 0 : (int) v[0];
        int to = Math.min(max, was + n);
        m.put(e.getUUID(), new long[] {to, now + (long) trait(j, "frostFor", 120)});
        LAST.put(sp.getUUID(), e.getUUID());
        // 하나당 이동 −slowPer → 구속 단계 (서리 2 · 4 에서 한 단계씩 — 구속 한 단계 = 15%)
        int amp = to >= 4 ? 1 : to >= 2 ? 0 : -1;
        if (amp >= 0 && !JobKit.isBoss(e)) RuneKit.effect(e, "slowness", (int) trait(j, "frostFor", 120), amp);
        if (e.level() instanceof ServerLevel level) {
            Vec3 b = Kit.bodyPoint(e);
            SkillFx.sparks(level, b, FROST_WHITE, 3 + to, 2.6);
            SkillFx.fx(level, "rpg:snowfall", V.up(b, 0.4), Opt.of("size", 0.5 + to * 0.08, "life", 0.6, "color", FROST_WHITE, "alpha", 0.8));
        }
        if (to >= max && was < max && !frozen(e)) freeze(sp, j, e, (int) trait(j, "freezeFor", 30));
        JobTraits.sendGauge(sp, false);
    }

    /** 얼어붙음 (보스는 받는 피해 증가 대신) — 얼음 결정 · 소리 */
    static void freeze(ServerPlayer sp, Jobs.Job j, LivingEntity e, int ticks) {
        freeze(sp, j, e, ticks, true);
    }

    /** 같은 것 — mark 가 false 면 몸의 눈꽃 결정 (JobFx.frost) 을 그리지 않음 (샤터 · 핌불윈터는 얼음 결정 감옥을 따로 그림) */
    static void freeze(ServerPlayer sp, Jobs.Job j, LivingEntity e, int ticks, boolean mark) {
        if (e == null || e.isRemoved()) return;
        long until = Tasks.now() + Math.max(1, ticks);
        UUID by = sp.getUUID();
        FROZEN.put(e.getUUID(), new long[] {until, by.getMostSignificantBits(), by.getLeastSignificantBits()});
        if (JobKit.isBoss(e)) {
            BOSS.put(e.getUUID(), Tasks.now() + (long) trait(j, "bossFor", 80));
        } else {
            RuneKit.stun(e, ticks, ICE, false);
        }
        if (mark) JobFx.frost(e, ICE, FROST_WHITE, ticks);
        if (e.level() instanceof ServerLevel level) {
            SkillFx.play(level, "block.glass.place", e.position(), 0.7, 0.9);
            SkillFx.play(level, "block.amethyst_block.place", e.position(), 0.6, 0.8);
        }
    }

    /** 얼어붙어 있는지 (누가 얼렸든) */
    public static boolean frozen(LivingEntity e) {
        long[] f = e == null ? null : FROZEN.get(e.getUUID());
        return f != null && f[0] >= Tasks.now();
    }

    /** sp 가 얼린 적인지 */
    static boolean frozenBy(ServerPlayer sp, LivingEntity e) {
        long[] f = e == null ? null : FROZEN.get(e.getUUID());
        return f != null && f[0] >= Tasks.now() && f[1] == sp.getUUID().getMostSignificantBits() && f[2] == sp.getUUID().getLeastSignificantBits();
    }

    /** 풀어 줌 (서리 0 — 샤터 · 핌불윈터가 제 결정을 제 숫자로 깨기 전) */
    static void thaw(ServerPlayer sp, LivingEntity e) {
        if (e == null) return;
        FROZEN.remove(e.getUUID());
        Map<UUID, long[]> m = FROST.get(sp.getUUID());
        if (m != null) m.remove(e.getUUID());
        JobTraits.sendGauge(sp, false);
    }

    private static double bossTaken(LivingEntity e) {
        Long until = e == null ? null : BOSS.get(e.getUUID());
        if (until == null || until < Tasks.now()) return 1;
        Jobs.Job j = Jobs.job(FR);
        return 1 + trait(j, "bossTaken", 0.1);
    }

    // ------------------------------------------------------------ 깨짐 (직업 스킬)

    /** 직업 스킬 피해 배율 (JobKit.hit — 피해 전): 이 사람이 얼린 적이면 × (1 + shatter) */
    static double hitMult(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        if (j == null || !FR.equals(j.id()) || e == null) return 1;
        return frozenBy(sp, e) ? 1 + trait(j, "shatter", 0.3) : 1;
    }

    /** 직업 스킬 피해가 들어간 뒤 (JobKit.hit): 얼린 적이면 깨짐 (얼음 조각 · 서리 0) — 아니면 서리 +1 */
    static void afterJobHit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double dealt) {
        if (j == null || !FR.equals(j.id()) || e == null || !(dealt > 0)) return;
        if (frozenBy(sp, e)) {
            shatter(sp, j, e);
            return;
        }
        addFrost(sp, j, e, 1);
    }

    /** 깨짐: 얼음 조각이 둘레로 튐 (다른 적에 위력 × shard) · 결정이 깨지는 그림 · 소리 · 서리 0 */
    private static void shatter(ServerPlayer sp, Jobs.Job j, LivingEntity e) {
        thaw(sp, e);
        if (!(e.level() instanceof ServerLevel level)) return;
        Vec3 b = Kit.bodyPoint(e);
        shardsFx(level, b, 1.0 + Kit.bodyPad(e) * 0.6);
        SkillFx.play(level, "block.glass.break", b, 0.8, 1.0);
        SkillFx.play(level, "block.amethyst_block.break", b, 0.7, 0.9);
        double r = trait(j, "shardRadius", 2);
        double P = JobSystem.power(sp, j);
        String life = RuneKit.lifeToken(sp);
        for (LivingEntity o : Kit.around(sp, b, r + 0.5, 2.0)) {
            if (o == e) continue;
            JobKit.strike(sp, o, j, P, trait(j, "shard", 0.6), false, false, life, 1, null);
        }
    }

    /** 얼음 조각이 사방으로 튐 (흰 불티 · 얼음빛 조각 · 결정 그림이 깨짐) */
    static void shardsFx(ServerLevel level, Vec3 at, double size) {
        SkillFx.sparks(level, at, FROST_WHITE, (int) (10 + size * 6), 6.0 + size * 2);
        SkillFx.sparks(level, at, ICE, (int) (8 + size * 4), 4.5 + size * 2);
        RuneKit.rockBurst(level, at, (int) (6 + size * 4), 4.0 + size * 2, 0.12);
        JobFx.burst(level, at, 0.9 + size * 0.5, ICE, FROST_WHITE, 0.3, Opt.empty());
    }

    // ------------------------------------------------------------ 도끼 평타 · 요툰 그립

    /** 요툰 그립: 태세 동안 도끼 평타가 맞으면 평타 × echo 를 더함 (서리 거인의 일격) */
    private static RpgCombat.Bonus jotunnBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (w == null || !"axe".equals(w.type()) || !(dmg > 0) || !JobTraits.stanceOn(sp, JOTUNN)) return null;
        Jobs.Job j = jobIf(sp);
        if (j == null) return null;
        Jobs.Skill k = j.skill(JOTUNN);
        double echo = k == null ? 0.6 : JobUpgrades.num(sp, k, "echo", 0.6);
        return new RpgCombat.Bonus(dmg * echo, "", KEY_JOTUNN, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    /** 도끼 평타가 맞음: 서리 +1 (요툰 그립이면 +1 더 · 맞은 자리 얼음 충격) */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (w == null || victim == null || !"axe".equals(w.type()) || !Combat.isMeleeWeapon(w)) return;
        Jobs.Job j = jobIf(sp);
        if (j == null) return;
        boolean giant = false;
        if (bonus != null) for (RpgCombat.Bonus p : bonus.parts()) if (KEY_JOTUNN.equals(p.key())) giant = true;
        addFrost(sp, j, victim, giant ? 2 : 1);
        // 2026-11-07 원소 반응: 요툰 그립 (얼음 원소 스킬) 의 서리 거인 도끼가 맞은 적에 얼음을 묻힘 (평타 덤 — 쓰는 동안 밖이라 직접)
        if (giant) ElementSystem.apply(sp, victim, Jobs.element(j.skill(JOTUNN)), JobSystem.power(sp, j), true, false);
        if (giant && victim.level() instanceof ServerLevel level) {
            Vec3 g = victim.position();
            RuneKit.later(3, () -> {
                if (victim.isRemoved()) return;
                JobFx15.halo(level, V.up(g, 0.1), 1.6 + Kit.bodyPad(victim), 0.35, Opt.of("alpha", 0.9), ICE, FROST_WHITE);
                shardsFx(level, Kit.bodyPoint(victim), 0.8);
                SkillFx.play(level, "block.glass.break", g, 0.6, 0.6);
            });
        }
    }

    /** 2026-11-13 서리 거인의 3D 빙하 도끼 크기 (길이 약 5.7칸 — 직업 도감 메모 "얼음과 도끼 연출을 3d로", 예전 환영 도끼 판 2.8배 대신) */
    static final double GIANT_AXE = 2.0;

    /**
     * 요툰 그립 동안 도끼를 휘두름: 등 뒤 높이 (서리 거인의 어깨) 에서 거대한 3D 빙하 도끼가 머리 위를 넘어 같은 쪽 앞 땅으로 내려침 → 앞 땅에 3D 얼음 가시 ·
     * 결정 · 얼음 고리 · 조각
     */
    private static void onSwing(ServerPlayer sp, Weapon w) {
        if (w == null || !"axe".equals(w.type()) || !JobTraits.stanceOn(sp, JOTUNN)) return;
        Jobs.Job j = jobIf(sp);
        if (j == null) return;
        giantSwing(sp, j, GIANT_AXE, 0.16, 0.22);
        SkillFx.play(sp.level(), "entity.player.attack.sweep", sp.position(), 0.55, 0.7);
        SkillFx.play(sp.level(), "entity.polar_bear.warning", sp.position(), 1.6, 0.25);
    }

    /** 내려치는 각 deg (위 0° → 앞 90° → 앞 아래) 의 자루 쪽 (어깨 → 도끼머리) */
    private static Vec3 haftAt(Vec3 f, double deg) {
        double r = Math.toRadians(deg);
        return V.add(V.scale(V.UP, Math.cos(r)), f, Math.sin(r));
    }

    /** 내려치는 각 deg 의 3D 도끼 회전 (자루 = haftAt · 날 = 내려치는 쪽) */
    private static org.joml.Quaternionf giantPose(Vec3 f, double deg) {
        double r = Math.toRadians(deg);
        return JobAxe3d.pose(haftAt(f, deg), V.add(V.scale(V.UP, -Math.sin(r)), f, Math.cos(r)));
    }

    /**
     * 서리 거인의 내려침 한 번 (2026-11-13 3D): 어깨 (몸 뒤 0.6 · 높이 2.8) 를 손잡이로 −50° (뒤 위) → 128° (앞 땅) 를 secs 초 (내려칠수록 빨라짐 — 틱마다 180° 를 넘지
     * 않게) · 크기 scale → hold 초 박혀 있다 오그라들며 사라짐. 내려친 틱에 도끼머리 아래 땅: 3D 얼음 가시 셋 · 결정 셋 · 얼음 고리 · 조각
     */
    static void giantSwing(ServerPlayer sp, Jobs.Job j, double scale, double secs, double hold) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 pivot = V.up(V.add(sp.position(), f, -0.6), 2.8);
        int n = Math.max(2, (int) Math.round(secs * 20));
        int h = Math.max(2, (int) Math.round(hold * 20));
        double a0 = -50;
        double a1 = 128;
        Vfx.Fx fx = RuneProps.fx(level, pivot, n + h + 8);
        Vfx.Prop p = JobAxe3d.axe(fx, true, pivot, JobAxe3d.GRIP, giantPose(f, a0), scale * 0.7, ICE, FROST_WHITE);
        for (int i = 1; i <= n; i++) {
            double t = i / (double) n;
            double a = a0 + (a1 - a0) * t * t;
            fx.on(i, () -> p.to(null, giantPose(f, a), scale, 1));
        }
        fx.on(n + h, () -> p.size(scale * 0.1, 5));
        fx.on(n + h + 5, p::remove);
        Vec3 head = V.add(pivot, haftAt(f, a1), JobAxe3d.REACH * scale);
        RuneKit.later(n, () -> {
            if (sp.isRemoved()) return;
            ServerLevel lv = sp.level();
            Vec3 gg = new Vec3(head.x, RuneKit.groundAt(lv, V.up(head, 1.2), sp.getY()), head.z);
            JobAxe3d.spikeRing(lv, gg, 3, 0.5, 1.3, 0.9 * scale / GIANT_AXE, 1.4 * scale / GIANT_AXE, 6);
            JobAxe3d.crystals(lv, gg, 3, 0.2, 0.9, 0.8, 1.3, 0.4, 25, 45, false, 0, 4, 3);
            JobFx15.halo(lv, V.up(gg, 0.08), 1.4, 0.35, Opt.of("alpha", 0.9), ICE, FROST_WHITE);
            shardsFx(lv, V.up(gg, 0.4), 0.7);
            IceFx.shards(lv, V.up(gg, 0.3), 18, 0.6, 0.2, 0.18);
            SkillFx.play(lv, "block.glass.break", gg, 0.7, 0.5);
        });
    }

    /** 요툰 그립 태세가 켜짐: 서리 거인이 도끼를 한 번 크게 내려침 (포효) · 몸 둘레로 3D 얼음 결정이 솟았다 부서짐 (서리 갑옷) */
    static void jotunnOn(ServerPlayer sp, Jobs.Job j) {
        giantSwing(sp, j, GIANT_AXE * 1.25, 0.22, 0.35);
        JobAxe3d.crystals(sp.level(), sp.position(), 6, 1.3, 1.8, 1.2, 1.9, 0.36, 15, 30, false, 0, 14, 4);
    }

    // ------------------------------------------------------------ 게이지

    /** 게이지 = 마지막으로 친 적의 서리 */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        UUID t = LAST.get(sp.getUUID());
        if (t == null) return 0;
        Entity e = sp.level().getEntity(t);
        return e instanceof LivingEntity le && le.isAlive() && !le.isRemoved() ? frost(sp, le) : 0;
    }
}
