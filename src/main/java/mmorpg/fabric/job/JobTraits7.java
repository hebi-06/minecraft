package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Fx3d;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.SwingArea;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 — 마검사 각성 뵐룬드(룬 스미스) · 반전 스키르니르(소드 콜러) 특성 · 태세 (재설계안 2판 — claude/job-advance-redo.md):
 * <ul>
 * <li>룬 스미스 「마력 각인」 (쌓고 터지는 것은 마검사와 같음 — JobTraits.mark · markBonus, 최대 5): 각인이 터지면 둘레 spreadRadius 칸의 다른 적에게 룬 폭발이 번짐
 * (위력 × markBurst × spread · 마법). 달궈짐: 풀무 숨 · 용광로 · 쇳물 숫양에 맞은 적은 heatFor 틱 달궈짐 (빨갛게 달아오른 몸 · 불티) — 달궈진 적이 다른 직업 스킬에
 * 맞으면 식으며 깨짐 (그 피해에 위력 × quench 를 더함 · 큰 숫자 · quenchStun 틱 기절 — 보스는 기절 없음 · 김 · 깨지는 껍질). 담금질 (태세): 칼 평타 · 직업 스킬이
 * 맞힌 적에 달군 쇳조각이 박혀 shardAt 틱 뒤 터짐 (위력 × shard · 마법 — 적마다 shardGap 틱에 하나). 쇳조각 · 번진 룬은 식히지 않고 쇳조각을 박지 않음</li>
 * <li>소드 콜러 「스스로 싸우는 칼」 (1차와 거꾸로 — 내가 베는 광역 → 칼이 스스로 베는 한 적): 칼 평타를 휘두르면 마력 칼이 어깨에서 날아가 겨눈 적 (range 칸 · 둘로
 * 나뉘어라 동안 + range) 을 대신 벰 — 평타 한 번 그대로 (RpgCombat.areaHit), 둘레 범위 피해는 없음 (SwingArea.single). 가까이 조준해 바닐라 평타로 맞힌 적은 칼이
 * 그 적을 베는 그림만. 같은 적을 이어 벨 때마다 쌓임 (최대 max — lapse 틱 못 베거나 다른 적을 베면 1부터) · 하나당 공격 속도 + per (RpgCombat.attackSpeed).
 * 둘로 나뉘어라 (태세): 칼 평타마다 둘째 칼이 같은 적을 X 자로 함께 벰 (평타에 더한 몫 — 위력 × echo · 직업 색 숫자)</li>
 * </ul>
 */
public final class JobTraits7 {
    private JobTraits7() {}

    static final String RK = "runeknight";
    static final String SC = "swordcaller";
    static final String TEMPER = "runeknight_temper";
    static final String TWIN = "swordcaller_twin";
    /** 평타에 더한 몫의 구분 (WeaponBonus.parts) */
    static final String KEY_ECHO = "job_echo";

    /** 쇳물 (주황) · 흰 열 (노란 흰빛) · 깨지는 달군 껍질 (붉은 쇳빛) · 김 (흰 회색) */
    static final double[] MOLTEN = {1.0, 0.42, 0.06};
    static final double[] HOT = {1.0, 0.88, 0.5};
    static final double[] CRUST = {0.86, 0.3, 0.07};
    static final double[] STEAM = {0.88, 0.9, 0.92};
    private static final double[] WHITE = RuneKit.WHITE;

    static final class State {
        /** 소드 콜러: 이어 베는 적 · 쌓임 · 마지막으로 벤 틱 · 칼이 나간 쪽 (번갈아 — 오른쪽 · 왼쪽 어깨) */
        UUID chain;
        int stacks;
        long lastCut;
        boolean left;
        /** 소드 콜러 베어라: 꽂혀 기다리는 칼 (자리 · 끝나는 틱 · 입체 번호 · 그때의 삶) — 데려가라가 날아감 */
        Vec3 stuckAt;
        long stuckUntil;
        int stuckId;
        String stuckLife;
    }

    /** 달궈진 적 (적 · 끝나는 틱) */
    record Heat(LivingEntity e, long until) {}

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** 달궈진 적: "쓴 사람|적" */
    private static final Map<String, Heat> HEAT = new HashMap<>();
    /** 식히는 중 (JobKit — 피해가 안 들어가면 되돌림) */
    private static final Map<String, Heat> TAKEN = new HashMap<>();
    /** 담금질 쇳조각: "쓴 사람|적" → 다음에 박힐 수 있는 틱 */
    private static final Map<String, Long> SHARD_NEXT = new HashMap<>();
    /** 쇳조각 · 번진 룬 (특성 피해) 이 들어가는 중: 식히지 않고 쇳조각을 박지 않음 */
    private static int passive;

    static void init() {
        RpgCombat.addScaledBonusHook(JobTraits7::echoBonus);
        RpgCombat.addWeaponHitHook(JobTraits7::onWeaponHit);
        java.util.function.BiConsumer<ServerPlayer, Weapon> sh = RpgCombat.swingHook;
        RpgCombat.swingHook = (sp, w) -> {
            sh.accept(sp, w);
            onSwing(sp, w);
        };
        java.util.function.ToDoubleFunction<ServerPlayer> as = RpgCombat.attackSpeed;
        RpgCombat.attackSpeed = p -> as.applyAsDouble(p) * attackSpeed(p);
        java.util.function.Predicate<ServerPlayer> single = SwingArea.single;
        SwingArea.single = p -> single.test(p) || flyingSword(p);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            HEAT.clear();
            TAKEN.clear();
            SHARD_NEXT.clear();
            passive = 0;
        });
    }

    static State of(ServerPlayer sp) {
        return STATES.computeIfAbsent(sp.getUUID(), k -> new State());
    }

    static Jobs.Job jobIf(ServerPlayer sp, String id) {
        Jobs.Job j = JobSystem.job(sp);
        return j != null && id.equals(j.id()) ? j : null;
    }

    static double trait(Jobs.Job j, String key, double fallback) {
        return j == null || j.trait() == null ? fallback : j.trait().def().numOr(key, fallback);
    }

    private static String key(Entity attacker, Entity victim) {
        return attacker.getUUID() + "|" + victim.getUUID();
    }

    static void reset(ServerPlayer sp) {
        dropStuck(sp, 0.2);
        forget(sp.getUUID());
        JobFx3d.drop(sp, TWIN, 0.2);
    }

    static void forget(UUID id) {
        STATES.remove(id);
        String p = id + "|";
        HEAT.keySet().removeIf(k -> k.startsWith(p));
        TAKEN.keySet().removeIf(k -> k.startsWith(p));
        SHARD_NEXT.keySet().removeIf(k -> k.startsWith(p));
    }

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st != null) {
            st.chain = null;
            st.stacks = 0;
            st.stuckAt = null;
            st.stuckUntil = 0;
        }
        String p = id + "|";
        HEAT.keySet().removeIf(k -> k.startsWith(p));
        TAKEN.keySet().removeIf(k -> k.startsWith(p));
    }

    /** 특성 피해 (쇳조각 · 번진 룬): fn 동안 식히지 않고 쇳조각을 박지 않음 */
    private static double passiveHit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double P, double mult) {
        passive++;
        try {
            return JobKit.hit(sp, e, j, P, mult, true, false);
        } finally {
            passive--;
        }
    }

    // ------------------------------------------------------------ 룬 스미스: 각인이 터지면 번짐

    /**
     * 각인 n 개가 터짐 (victim 에서): 둘레 spreadRadius 칸의 다른 적에게 룬 폭발이 번짐 (위력 × markBurst × spread × n — 마법). 그림: 터진 자리의 룬 글자 · 둘레로
     * 퍼지는 룬 고리 · 번진 적마다 작은 글자 터짐 · 쇳물 불티
     */
    static void spread(ServerPlayer sp, LivingEntity victim, Jobs.Job j, int n) {
        if (j == null || !RK.equals(j.id()) || victim == null || n <= 0 || !(victim.level() instanceof ServerLevel level)) return;
        double r = trait(j, "spreadRadius", 3.0);
        double mult = trait(j, "markBurst", 1.0) * trait(j, "spread", 0.5) * n;
        Vec3 at = Kit.bodyPoint(victim);
        if (RuneKit.fxQuota("jobSpread", 6)) {
            JobFx.glyph(level, at, 0.6 + Kit.bodyPad(victim) * 0.3, (int) (Kit.rand() * 3), 4, 2, 0.3, false, Opt.empty(), j);
            JobFx.runeRing(level, V.up(victim.position(), 0.12), r * 0.9, j, 8, Kit.rand() * 45, 0.35, Opt.of("alpha", 0.85));
            SkillFx.shock(level, V.up(victim.position(), 0.1), MOLTEN, r, 0.35);
            SkillFx.sparks(level, at, MOLTEN, 8, 4.0);
            SkillFx.play(level, "block.amethyst_block.resonate", at, 1.7, 0.6);
        }
        String life = RuneKit.lifeToken(sp);
        double P = JobSystem.power(sp, j);
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, victim.position(), r, 2.5)) {
            if (e == victim) continue;
            JobKit.hitLater(sp, e, life, Tasks.now() + 1, () -> {
                if (passiveHit(sp, e, j, P, mult) > 0 && cnt[0]++ < 6 && e.level() instanceof ServerLevel lv) {
                    JobFx.glyph(lv, Kit.bodyPoint(e), 0.42 + Kit.bodyPad(e) * 0.25, cnt[0] % 3, 4, 2, 0.28, false, Opt.empty(), j);
                    SkillFx.sparks(lv, Kit.bodyPoint(e), MOLTEN, 5, 3.0);
                }
            });
        }
    }

    // ------------------------------------------------------------ 룬 스미스: 달궈짐 · 식으며 깨짐

    /** 이 적을 달굼 (heatFor 틱 — 다시 달구면 늘어남): 몸이 빨갛게 달아오름 (rfx_jheat 번쩍) · 쇳물 불티 · 지글 */
    static void heat(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        if (j == null || !RK.equals(j.id()) || e == null || e.isRemoved() || !e.isAlive()) return;
        Heat old = HEAT.put(key(sp, e), new Heat(e, Tasks.now() + (long) trait(j, "heatFor", 100)));
        if (!(e.level() instanceof ServerLevel level) || !RuneKit.fxQuota("jobHeat", 8)) return;
        Vec3 b = Kit.bodyPoint(e);
        double s = 0.55 + Kit.bodyPad(e) * 0.6;
        JobFx.heat(level, b, s * 1.15, 0, 2, 0.22, false, Opt.of("vel", RuneKit.entVel(e)));
        RuneKit.later(4, () -> {
            if (!e.isRemoved() && e.level() instanceof ServerLevel lv) JobFx.heat(lv, Kit.bodyPoint(e), s, 1, 4, 0.5, true, Opt.of("vel", RuneKit.entVel(e), "alpha", 0.9));
        });
        SkillFx.sparks(level, b, MOLTEN, 8, 3.2);
        SkillFx.fx(level, "rpg:ember", V.up(e.position(), 0.3), Opt.of("size", 1.0 + Kit.bodyPad(e), "life", 0.7, "speed", 1.4, "color", MOLTEN, "alpha", 0.9));
        if (old == null || old.until() <= Tasks.now()) SkillFx.play(level, "block.lava.pop", b, 0.8, 0.7);
    }

    /** 달궈져 있는지 */
    static boolean heated(ServerPlayer sp, Entity e) {
        Heat h = e == null ? null : HEAT.get(key(sp, e));
        return h != null && h.until() > Tasks.now();
    }

    /**
     * 직업 스킬 피해 (JobKit.hit · hitSplit 의 앞): 달궈진 적이면 식힘 — 그 피해에 더할 위력의 몫 (trait.quench — 위력 × quench). 아니면 0. 쇳조각 · 번진 룬 (특성 피해) 은 식히지 않음.
     * 피해가 들어가면 quenchLanded, 안 들어가면 quenchMissed (다시 달궈진 채로)
     */
    static double quenchTake(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        if (passive > 0 || j == null || !RK.equals(j.id()) || e == null || !heated(sp, e)) return 0;
        String k = key(sp, e);
        TAKEN.put(k, HEAT.remove(k));
        return trait(j, "quench", 0.8);
    }

    static void quenchMissed(ServerPlayer sp, LivingEntity e) {
        Heat h = TAKEN.remove(key(sp, e));
        if (h != null) HEAT.put(key(sp, e), h);
    }

    /** 식힌 피해가 들어감: 짧은 기절 (보스는 없음) · 김이 솟고 껍질이 깨짐 · 쉬익 */
    static void quenchLanded(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        TAKEN.remove(key(sp, e));
        if (e.isRemoved()) return;
        if (!JobKit.isBoss(e)) RuneKit.stun(e, (int) trait(j, "quenchStun", 10), HOT, true);
        if (e.level() instanceof ServerLevel level && RuneKit.fxQuota("jobQuench", 6)) quenchFx(level, e, j);
    }

    /**
     * 식으며 깨짐: 몸에서 김 셋이 솟구침 · 달아오른 껍질이 깨져 튐 (rfx_jshatter — 붉은 쇳빛 · 흰 열 테. 영상 1 뒤: 숯빛 조각은 하늘 앞에서 검은 별표로 보였음) · 흰 불티 ·
     * 큰 쉬익 · 쇠 깨지는 소리
     */
    static void quenchFx(ServerLevel level, LivingEntity e, Jobs.Job j) {
        Vec3 b = Kit.bodyPoint(e);
        double s = 0.6 + Kit.bodyPad(e) * 0.5;
        for (int i = 0; i < 3; i++) {
            Vec3 p = V.add(V.up(b, 0.1 + 0.25 * i), new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), 0.5);
            JobFx.smoke(level, p, s * (0.8 + 0.25 * i), STEAM, WHITE, 0.75 + 0.1 * i, Opt.of("vel", new Vec3((Kit.rand() - 0.5) * 0.6, 1.6 + 0.4 * i, (Kit.rand() - 0.5) * 0.6),
                    "alpha", 0.75));
        }
        JobFx.shatter(level, b, s * 1.05, CRUST, HOT, 0.4, Opt.of("vel", RuneKit.entVel(e)));
        JobFx.heat(level, b, s * 1.25, 5, 1, 0.35, false, Opt.of("vel", RuneKit.entVel(e)));
        SkillFx.sparks(level, b, HOT, 12, 5.0);
        SkillFx.sparks(level, b, MOLTEN, 8, 3.5);
        SkillFx.play(level, "block.lava.extinguish", b, 1.0, 0.9);
        SkillFx.play(level, "random.fizz", b, 0.7, 0.7);
        SkillFx.play(level, "random.anvil_land", b, 1.7, 0.35);
    }

    /** 시험용: 달굼 (스킬을 쓰지 않고) */
    public static void setHeat(ServerPlayer sp, LivingEntity e, int ticks) {
        HEAT.put(key(sp, e), new Heat(e, Tasks.now() + ticks));
    }

    /** 시험용: 달궈져 있는지 */
    public static boolean heatedOf(ServerPlayer sp, Entity e) {
        return heated(sp, e);
    }

    // ------------------------------------------------------------ 룬 스미스: 담금질 쇳조각

    /**
     * 담금질 동안 맞힌 적에 달군 쇳조각이 박힘 (적마다 shardGap 틱에 하나): 맞은 쪽 몸에 꽂힌 빛나는 쇳조각 (점점 달아오름) → shardAt 틱 뒤 터짐 (위력 × shard · 마법 —
     * 무적 시간이면 끝난 뒤로 미룸) — 작은 쇳물 터짐 · 불티 · 소리
     */
    static void embedShard(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        if (passive > 0 || e == null || e.isRemoved() || !e.isAlive() || j == null || !RK.equals(j.id()) || !JobTraits.stanceOn(sp, TEMPER)) return;
        Jobs.Skill k = j.skill(TEMPER);
        if (k == null || !(e.level() instanceof ServerLevel level)) return;
        long now = Tasks.now();
        String key = key(sp, e);
        Long next = SHARD_NEXT.get(key);
        if (next != null && next > now) return;
        SHARD_NEXT.put(key, now + (long) k.num("shardGap", 10));
        double mult = JobUpgrades.num(sp, k, "shard", 0.45);
        int at = Math.max(1, (int) k.num("shardAt", 20));
        String life = RuneKit.lifeToken(sp);
        double P = JobSystem.power(sp, j);
        // 박힘: 맞은 쪽 (쓴 사람 쪽) 몸 겉에 비스듬히 꽂힘 — 꽂힌 조각 (rfx_icicle — 쇳물 · 흰 열) 이 at 틱 동안 몸을 따라감
        Vec3 b = Kit.bodyPoint(e);
        Vec3 toMe = V.flat(V.sub(sp.position(), e.position()));
        Vec3 side = V.rightOf(toMe);
        double off = (Kit.rand() - 0.5) * 0.7;
        Vec3 skin = V.up(V.add(V.add(b, toMe, Kit.bodyPad(e) * 0.9 + 0.05), side, off * (0.4 + Kit.bodyPad(e))), (Kit.rand() - 0.3) * 0.5);
        Vec3 into = V.unit(V.add(V.scale(toMe, -1), new Vec3(0, -0.35 + Kit.rand() * 0.3, 0)));
        if (RuneKit.fxQuota("jobShard", 10)) {
            RuneKit.rfx2(level, "icicle", V.add(skin, into, -0.2), Opt.of("dir", into, "size", 0.48, "life", at / 20.0, "fade", 0.05, "alpha", 1.0, "vel",
                    RuneKit.entVel(e)), MOLTEN, HOT);
            JobFx.two(level, "jimpact", JobImpact.IMPACT, skin, 0.2, Opt.of("size", 0.55, "spin", Kit.rand() * 360, "fade", 0.3, "vel", RuneKit.entVel(e)), MOLTEN, HOT);
            SkillFx.sparks(level, skin, HOT, 6, 3.0);
            SkillFx.play(level, "item.trident.hit", skin, 1.8, 0.35);
            // 터지기 앞 6틱: 조각이 하얗게 달아오름 (흰 열 번쩍)
            RuneKit.later(Math.max(1, at - 6), () -> {
                if (!e.isRemoved() && e.level() instanceof ServerLevel lv) RuneKit.rfx2(lv, "icicle", V.add(skin, into, -0.2), Opt.of("dir", into, "size", 0.56, "life",
                        0.32, "fade", 0.05, "alpha", 1.0, "vel", RuneKit.entVel(e)), HOT, WHITE);
            });
        }
        JobKit.hitLater(sp, e, life, now + at, () -> {
            if (passiveHit(sp, e, j, P, mult) > 0 && e.level() instanceof ServerLevel lv && RuneKit.fxQuota("jobShardBurst", 8)) shardBurstFx(lv, e);
        });
    }

    /** 쇳조각이 터짐: 쇳물 폭발 (rfx_boom) · 타격 섬광 · 튀는 쇳물 방울 · 불티 · 짧은 쾅 (영상 1 뒤 크게 — 작은 불꽃으로만 보였음) */
    private static void shardBurstFx(ServerLevel level, LivingEntity e) {
        Vec3 b = Kit.bodyPoint(e);
        double s = 0.85 + Kit.bodyPad(e) * 0.5;
        RuneKit.rfx(level, "boom", b, Opt.of("size", s, "life", 0.45, "frames", 8.0, "fps", 18.0, "vel", RuneKit.entVel(e)));
        JobFx.burst(level, b, s * 1.1, HOT, MOLTEN, 0.28, Opt.of("vel", RuneKit.entVel(e)));
        JobFx.two(level, "jimpact", JobImpact.IMPACT, b, 0.25, Opt.of("size", s * 1.25, "spin", Kit.rand() * 360, "fade", 0.3, "vel", RuneKit.entVel(e)), MOLTEN, WHITE);
        for (int i = 0; i < 4; i++) {
            double a = Kit.rand() * Math.PI * 2;
            SkillFx.fx(level, "rpg:glob", b, Opt.of("dir", V.unit(new Vec3(Math.cos(a), 1.2 + Kit.rand(), Math.sin(a))), "speed", 4.0 + Kit.rand() * 2, "size", 0.14
                    + Kit.rand() * 0.08, "life", 0.55, "spin", Kit.rand() * 360, "color", i % 2 == 0 ? HOT : MOLTEN, "alpha", 1.0));
        }
        SkillFx.sparks(level, b, HOT, 8, 4.5);
        SkillFx.play(level, "firework.blast", b, 1.3, 0.5);
        SkillFx.play(level, "block.anvil.land", b, 1.9, 0.2);
    }

    // ------------------------------------------------------------ 직업 스킬 피해가 들어간 뒤 (JobKit)

    /** 직업 스킬 피해가 들어간 뒤: 룬 스미스 담금질 — 쇳조각을 박음 (특성 피해는 빼고) */
    static void afterJobHit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double dealt) {
        if (j == null || !RK.equals(j.id()) || !(dealt > 0) || e == null || passive > 0) return;
        embedShard(sp, e, j);
    }

    // ------------------------------------------------------------ 소드 콜러: 스스로 싸우는 칼

    /** 칼을 날리는 소드 콜러인지 (그 직업의 칼을 듦 — 둘레 범위 피해 없음 · SwingArea.single) */
    static boolean flyingSword(ServerPlayer sp) {
        Jobs.Job j = jobIf(sp, SC);
        if (j == null) return false;
        Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
        return w != null && j.usesWeapon(w.type());
    }

    /** 칼이 날아가는 거리 (trait.range + 둘로 나뉘어라 동안 range) */
    static double flyRange(ServerPlayer sp, Jobs.Job j) {
        double r = trait(j, "range", 10);
        if (JobTraits.stanceOn(sp, TWIN)) {
            Jobs.Skill k = j.skill(TWIN);
            r += k == null ? 4 : k.num("range", 4);
        }
        return r;
    }

    /** 칼이 노릴 적: 조준한 적 (맞을 수 있으면), 없으면 시야 cone 도 안의 적대 무리 (곁의 동물은 아님) */
    static LivingEntity flyTarget(ServerPlayer sp, Jobs.Job j, double range) {
        RpgCombat.Aim r = RpgCombat.aimTarget(sp, range, 0);
        if (r.target() != null && Kit.canHit(sp, r.target())) return r.target();
        RpgCombat.Aim a = RpgCombat.assist(sp, range, trait(j, "cone", 12), x -> Kit.splashFoe(sp, x));
        return a == null ? null : a.target();
    }

    /** 공격 속도 배율 (쌓임 하나당 + per — lapse 틱 못 베면 1) */
    static double attackSpeed(ServerPlayer sp) {
        Jobs.Job j = jobIf(sp, SC);
        if (j == null) return 1;
        State st = STATES.get(sp.getUUID());
        if (st == null || st.stacks <= 0 || Tasks.now() - st.lastCut > trait(j, "lapse", 40)) return 1;
        return 1 + trait(j, "per", 0.2) * st.stacks;
    }

    /** 게이지 값: 소드 콜러 = 쌓임 */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !SC.equals(j.id())) return 0;
        State st = STATES.get(sp.getUUID());
        return st == null || Tasks.now() - st.lastCut > trait(j, "lapse", 40) ? 0 : st.stacks;
    }

    /** 시험용: 칼이 날아가는 거리 */
    public static double flyRangeOf(ServerPlayer sp) {
        Jobs.Job j = jobIf(sp, SC);
        return j == null ? 0 : flyRange(sp, j);
    }

    /** 시험용: 꽂혀 기다리는 칼이 있는지 */
    public static boolean stuckOf(ServerPlayer sp) {
        return stuckAt(sp) != null;
    }

    /** 시험용: 쌓임 */
    public static int stacksOf(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.stacks;
    }

    /**
     * 칼 평타를 휘두름 (Slash.once — 허공 포함): 1틱 뒤 (같은 누름의 공격 패킷까지 받은 뒤) — 가까이 조준해 바닐라 평타로 맞힌 적이 있으면 칼이 그 적을 베는 그림만,
     * 없으면 칼이 날아가 노린 적을 벰 (닿는 틱에 평타 한 번), 노릴 적이 없으면 칼이 앞으로 날았다 돌아옴
     */
    private static void onSwing(ServerPlayer sp, Weapon w) {
        Jobs.Job j = jobIf(sp, SC);
        if (j == null || w == null || !j.usesWeapon(w.type())) return;
        String life = RuneKit.lifeToken(sp);
        State st = of(sp);
        st.left = !st.left;
        boolean left = st.left;
        RuneKit.later(1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            UUID prim = SwingArea.primaryOf(sp);
            Entity pe = prim == null ? null : sp.level().getEntity(prim);
            if (pe instanceof LivingEntity le && le.isAlive()) {
                flyCut(sp, j, le, w, false, left);
                return;
            }
            LivingEntity t = flyTarget(sp, j, flyRange(sp, j));
            if (t == null) emptyCut(sp, j, left);
            else flyCut(sp, j, t, w, true, left);
        });
    }

    /** 어깨 위 칼이 떠나는 자리 (left 면 왼쪽 어깨) */
    static Vec3 shoulder(ServerPlayer sp, boolean left) {
        Vec3 f = Kit.facingOf(sp);
        return V.up(V.add(V.add(sp.position(), V.rightOf(f), left ? -0.62 : 0.62), f, -0.15), 1.72);
    }

    /**
     * 칼이 날아가 벰: 어깨 위에서 마력 칼이 휘어 날아가 (flySpeed 칸/틱) 닿는 틱에 적을 가로질러 벰 (칼이 몸을 꿰고 지나감 · 참격 판 · 맞은 자리 — 쌓일수록 밝게) →
     * 뒤로 물러나며 어깨로 돌아옴. deal 이면 닿는 틱에 평타 한 번 그대로 (그 무기를 아직 들고 쓸 수 있을 때). 둘로 나뉘어라 동안 둘째 칼이 다른 어깨에서 함께 날아 X 자로 벰
     */
    static void flyCut(ServerPlayer sp, Jobs.Job j, LivingEntity t, Weapon w, boolean deal, boolean left) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        boolean twin = JobTraits.stanceOn(sp, TWIN);
        Vec3 to = Kit.bodyPoint(t);
        Vec3 from = shoulder(sp, left);
        double speed = Math.max(0.5, trait(j, "flySpeed", 3.0));
        int ticks = Math.max(1, (int) Math.ceil(V.len(V.sub(to, from)) / speed));
        State st = of(sp);
        int stacks = Tasks.now() - st.lastCut <= trait(j, "lapse", 40) ? st.stacks : 0;
        int id1 = cutSword(sp, j, from, to, left, ticks, stacks);
        int id2 = twin ? cutSword(sp, j, shoulder(sp, !left), to, !left, ticks, stacks) : 0;
        SkillFx.play(level, "item.trident.throw", from, 1.5 + 0.06 * stacks, 0.45);
        RuneKit.later(ticks, () -> {
            if (!RuneKit.sameLife(sp, life)) {
                JobFx3d.kill(sp.level(), id1, to, 0.1);
                JobFx3d.kill(sp.level(), id2, to, 0.1);
                return;
            }
            ServerLevel lv = sp.level();
            if (deal && !t.isRemoved() && t.isAlive()) {
                Weapon held = RpgCombat.weaponOf(sp.getMainHandItem());
                if (held != null && held.id().equals(w.id()) && RpgCombat.lackOf(sp, held).isEmpty() && Kit.canHit(sp, t)) RpgCombat.areaHit(sp, t, held);
            }
            Vec3 at = t.isRemoved() ? to : Kit.bodyPoint(t);
            cutFx(lv, sp, j, at, Kit.bodyPad(t), left, twin, stacks);
            passBack(sp, id1, from, at, left);
            passBack(sp, id2, shoulder(sp, !left), at, !left);
        });
    }

    /** 날아가는 칼 하나 (어깨 → 적 — 바깥 위로 휘어 감, 칼끝이 가는 쪽): 번호 */
    private static int cutSword(ServerPlayer sp, Jobs.Job j, Vec3 from, Vec3 to, boolean left, int ticks, int stacks) {
        Vec3 f = Kit.facingOf(sp);
        Vec3 out = V.scale(V.rightOf(f), left ? -1 : 1);
        double len = V.len(V.sub(to, from));
        Vec3 ctrl = V.up(V.add(V.lerp(from, to, 0.45), out, Math.min(1.6, 0.35 + len * 0.12)), Math.min(1.0, 0.2 + len * 0.06));
        return JobFx3d.fly(sp.level(), "phantom_sword", from, to, JobFx3d.SWORD_TIP, 1.0, ticks / 20.0, 0.5, j, Opt.of("ctrl", ctrl, "shine", 0.3 + 0.08 * stacks,
                "glowAlpha", 0.5 + 0.06 * stacks, "fadeOut", 0.12));
    }

    /** 벤 뒤: 칼이 적을 꿰고 1.1칸 지나갔다가 (2틱) 적을 본 채 물러나 어깨로 돌아옴 (4틱) → 사라짐 */
    private static void passBack(ServerPlayer sp, int id, Vec3 from, Vec3 at, boolean left) {
        if (id == 0) return;
        ServerLevel level = sp.level();
        Vec3 d = V.unit(V.sub(at, from));
        Fx3d.move(level, id, V.add(at, d, 1.1), 0.1, "out");
        RuneKit.later(2, () -> Fx3d.moveBack(level, id, shoulder(sp, left), 0.2, "inout"));
        RuneKit.later(6, () -> JobFx3d.kill(level, id, sp.position(), 0.12));
    }

    /**
     * 칼이 벤 자리: 참격 판 (칼이 지나간 결 — 왼쪽 어깨에서 오면 반대로 기욺 · 둘이면 X 자) · 직업의 맞은 자리 · 청록 불티 · 쌓임 3 부터 잔상 한 겹 · 다섯이면 흰 고리 ·
     * 휙 · 칼 소리 (쌓일수록 높게)
     */
    static void cutFx(ServerLevel level, ServerPlayer sp, Jobs.Job j, Vec3 at, double pad, boolean left, boolean twin, int stacks) {
        if (!RuneKit.fxQuota("jobFlyCut", 8)) return;
        Vec3 d = V.unit(V.sub(at, sp.getEyePosition()));
        double tilt = (left ? 200 : -20) + (Kit.rand() - 0.5) * 24;
        double size = 1.0 + pad * 0.9 + 0.05 * stacks;
        JobFx.slashCard(level, at, d, tilt, size, 0.24, Vec3.ZERO, j, Opt.empty());
        if (twin) JobFx.slashCard(level, at, d, tilt + 90 + (Kit.rand() - 0.5) * 20, size, 0.24, Vec3.ZERO, j, Opt.empty());
        if (stacks >= 3) JobFx.slashCard(level, V.add(at, d, -0.1), d, tilt + 12, size * 1.18, 0.3, Vec3.ZERO, j, Opt.of("alpha", 0.5));
        JobFx.burst(level, at, 0.45 + pad * 0.4, j, 0.22, Opt.empty());
        SkillFx.sparks(level, at, JobFx.inner(j), 5 + stacks, 3.5 + 0.3 * stacks);
        if (stacks >= 5) JobFx.ringFacing(level, at, 0.7 + pad, j, d, 0.25, Opt.of("alpha", 0.85));
        SkillFx.play(level, "entity.player.attack.sweep", at, 1.45 + 0.08 * stacks, 0.45);
        SkillFx.play(level, "item.trident.hit", at, 1.3 + 0.08 * stacks, 0.4);
    }

    /** 노릴 적이 없음: 칼이 보는 쪽 앞 4칸까지 날았다가 돌아옴 (휙) */
    private static void emptyCut(ServerPlayer sp, Jobs.Job j, boolean left) {
        if (!RuneKit.fxQuota("jobFlyEmpty", 6)) return;
        ServerLevel level = sp.level();
        Vec3 look = sp.getViewVector(1F);
        Vec3 from = shoulder(sp, left);
        double reach = Math.min(4.0, Kit.rayDist(level, sp.getEyePosition(), look, 4.0));
        Vec3 to = V.add(sp.getEyePosition(), look, Math.max(1.5, reach));
        int id = cutSword(sp, j, from, to, left, 2, 0);
        RuneKit.later(2, () -> {
            Fx3d.moveBack(level, id, shoulder(sp, left), 0.2, "inout");
            JobFx.slashCard(level, to, look, left ? 200 : -20, 0.9, 0.18, Vec3.ZERO, j, Opt.of("alpha", 0.6));
        });
        RuneKit.later(6, () -> JobFx3d.kill(level, id, sp.position(), 0.12));
        SkillFx.play(level, "entity.player.attack.nodamage", from, 1.6, 0.35);
    }

    /** 둘로 나뉘어라: 칼 평타에 둘째 칼 (위력 × echo — 직업 색 숫자) */
    private static RpgCombat.Bonus echoBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (w == null || victim == null || !JobTraits.stanceOn(sp, TWIN)) return null;
        Jobs.Job j = jobIf(sp, SC);
        if (j == null || !j.usesWeapon(w.type())) return null;
        Jobs.Skill k = j.skill(TWIN);
        double echo = k == null ? 0.9 : JobUpgrades.num(sp, k, "echo", 0.9);
        double add = JobSystem.power(sp, j) * echo * mmorpg.fabric.stattree.StatTrees.atkMult(sp);
        return new RpgCombat.Bonus(add, "", KEY_ECHO, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    /**
     * 무기로 맞힘 (피해가 들어간 뒤): 룬 스미스 — 담금질이면 쇳조각, 소드 콜러 — 같은 적을 이어 벰 (쌓임 +1 · 최대 max, 다른 적이거나 lapse 틱이 지났으면 1)
     */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (victim == null || w == null) return;
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || !j.usesWeapon(w.type())) return;
        if (RK.equals(j.id())) {
            embedShard(sp, victim, j);
            return;
        }
        if (!SC.equals(j.id())) return;
        State st = of(sp);
        long now = Tasks.now();
        int max = j.trait() == null ? 5 : Math.max(1, j.trait().max());
        boolean same = victim.getUUID().equals(st.chain) && now - st.lastCut <= trait(j, "lapse", 40);
        int before = st.stacks;
        st.stacks = same ? Math.min(max, st.stacks + 1) : 1;
        st.chain = victim.getUUID();
        st.lastCut = now;
        if (st.stacks == max && before < max && RuneKit.fxQuota("jobChainFull", 4)) {
            // 가득 참: 몸 둘레 흰 바람 고리 · 높은 칼 소리
            JobFx.ring(sp.level(), V.up(sp.position(), 1.0), 1.3, j, false, 0.3, Opt.of("alpha", 0.9));
            SkillFx.play(sp.level(), "item.trident.return", sp.position(), 1.8, 0.6);
        }
        JobTraits.sendGauge(sp, false);
    }

    // ------------------------------------------------------------ 소드 콜러: 꽂혀 기다리는 칼 (베어라 → 데려가라)

    /** 꽂혀 기다리는 칼을 둠 (자리 · ticks 틱 · 입체 번호) — 앞의 것은 지움 */
    static void setStuck(ServerPlayer sp, Vec3 at, int ticks, int id) {
        dropStuck(sp, 0.15);
        State st = of(sp);
        st.stuckAt = at;
        st.stuckUntil = Tasks.now() + ticks;
        st.stuckId = id;
        st.stuckLife = RuneKit.lifeToken(sp);
    }

    /** 시험 · 영상용: 꽂혀 기다리는 칼을 at (땅) 에 ticks 틱 둠 (베어라를 쓰지 않고 — 데려가라가 그리로 날아감) */
    public static void testStuck(ServerPlayer sp, Vec3 at, int ticks) {
        Jobs.Job j = jobIf(sp, SC);
        if (j == null) return;
        setStuck(sp, at, ticks, stuckSword(sp.level(), at, ticks, j));
    }

    /**
     * 땅에 꽂혀 기다리는 칼 (g = 땅): 비스듬히 꽂힌 큰 칼 · 발밑 룬 고리 · 위로 옅어지는 청록 빛기둥 (멀리서도 데려가라가 날아갈 자리가 보이게 — 영상 1 뒤: 과녁 무리 뒤에
     * 묻혔음). 칼 번호
     */
    static int stuckSword(ServerLevel level, Vec3 g, int ticks, Jobs.Job j) {
        double secs = ticks / 20.0;
        int id = JobFx3d.stuck(level, "phantom_sword", V.up(g, -0.3), V.unit(new Vec3(0.12, -1, 0.08)), JobFx3d.SWORD_TIP, 1.25, secs, j, Opt.of("fade", 0.3));
        JobFx.runeRing(level, V.up(g, 0.08), 1.1, j, 6, 0, secs, Opt.of("alpha", 0.8));
        double[] alpha = {0.5, 0.36, 0.24, 0.13};
        for (int i = 0; i < alpha.length; i++) {
            JobFx.beam(level, V.up(g, 0.2 + i * 1.1), V.up(g, 0.2 + (i + 1) * 1.1), j, 1.1, secs, Opt.of("alpha", alpha[i], "fade", 0.3));
        }
        return id;
    }

    /** 꽂혀 기다리는 칼 자리 (없거나 끝났거나 삶이 바뀌었으면 null) */
    static Vec3 stuckAt(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        if (st == null || st.stuckAt == null || st.stuckUntil <= Tasks.now() || !RuneKit.sameLife(sp, st.stuckLife)) return null;
        return st.stuckAt;
    }

    /** 꽂혀 기다리는 칼을 지움 (fade 초) */
    static void dropStuck(ServerPlayer sp, double fade) {
        State st = STATES.get(sp.getUUID());
        if (st == null || st.stuckAt == null) return;
        JobFx3d.kill(sp.level(), st.stuckId, st.stuckAt, fade);
        st.stuckAt = null;
        st.stuckUntil = 0;
        st.stuckId = 0;
    }

    // ------------------------------------------------------------ 틱 · 태세

    /**
     * 4틱마다 (JobTraits.tick): 끝난 달궈짐 · 쇳조각 기록 지우기 · 룬 스미스 — 8틱마다 달궈진 적의 붉은 몸 (rfx_jheat 이어 돌림) · 오르는 불티, 소드 콜러 — 쌓임이
     * 끊김 (lapse) · 꽂힌 칼이 끝남 (흐려짐)
     */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        if (HEAT.size() > 256) HEAT.values().removeIf(h -> h.until() < now || h.e().isRemoved());
        if (SHARD_NEXT.size() > 256) SHARD_NEXT.values().removeIf(t -> t < now);
        if (RK.equals(j.id()) && now % 8 == 0) heatTick(sp, now);
        if (!SC.equals(j.id())) return;
        State st = STATES.get(sp.getUUID());
        if (st == null) return;
        if (st.stacks > 0 && now - st.lastCut > trait(j, "lapse", 40)) {
            st.stacks = 0;
            st.chain = null;
            JobTraits.sendGauge(sp, false);
        }
        if (st.stuckAt != null && st.stuckUntil <= now) dropStuck(sp, 0.3);
    }

    /** 이 사람이 달군 적들: 붉게 달아오른 몸 (0.45초 이어 돌림 — 다음 그림과 겹침) · 오르는 불티 (몸에) · 끝난 것은 지움 */
    private static void heatTick(ServerPlayer sp, long now) {
        String p = sp.getUUID() + "|";
        List<String> done = new ArrayList<>();
        int n = 0;
        for (Map.Entry<String, Heat> en : HEAT.entrySet()) {
            if (!en.getKey().startsWith(p)) continue;
            Heat h = en.getValue();
            LivingEntity e = h.e();
            if (h.until() <= now || e.isRemoved() || !e.isAlive()) {
                done.add(en.getKey());
                continue;
            }
            if (n++ >= 10 || !(e.level() instanceof ServerLevel level)) continue;
            double s = 0.55 + Kit.bodyPad(e) * 0.6;
            double left = (h.until() - now) / 20.0;
            JobFx.heat(level, Kit.bodyPoint(e), s, 1, 4, 0.5, true, Opt.of("vel", RuneKit.entVel(e), "alpha", left < 1 ? 0.55 : 0.85));
            SkillFx.fx(level, "rpg:arrow_spark", V.up(Kit.bodyPoint(e), 0.3), Opt.of("dir", V.UP, "speed", 1.6, "size", 0.1, "grow", 0.4, "life", 0.5, "color", MOLTEN,
                    "alpha", 1.0));
        }
        for (String k : done) HEAT.remove(k);
    }

    /**
     * 태세 연출 (8틱마다 — JobTraits.stanceFx): 담금질 = 달군 대검 (손에 든 칼날을 감싸는 붉은 열기 · 흰 열 심지 — 영상 1 뒤: 따로 세운 쇳물 칼 그림은 손 옆에 칼이 하나
     * 더 떠 있는 것처럼 보였음) · 떨어지는 쇳물 방울 · 오르는 불티 / 둘로 나뉘어라 = 손의 칼에서 흩날리는 청록 빛살. 그렸으면 true
     */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        ServerLevel level = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        if (TEMPER.equals(stance)) {
            Vec3 blade = V.up(V.add(h.hand(), h.f(), 0.25), 0.45);
            JobFx.heat(level, V.up(blade, -0.05), 0.42, 1, 4, 0.45, true, Opt.of("alpha", 0.8, "vel", RuneKit.bodyVel(sp)));
            SkillFx.fx(level, "rpg:arrow_spark", V.up(blade, 0.35), Opt.of("dir", V.UP, "speed", 1.4, "size", 0.12, "grow", 0.6, "life", 0.45, "color", HOT, "alpha", 1.0));
            if (Kit.rand() < 0.5) SkillFx.fx(level, "rpg:glob", V.up(blade, -0.2), Opt.of("dir", V.DOWN, "speed", 0.5, "size", 0.07, "life", 0.5, "spin", 0.0, "color",
                    MOLTEN, "alpha", 1.0));
            return true;
        }
        if (TWIN.equals(stance)) {
            Vec3 blade = V.up(V.add(h.hand(), h.f(), 0.3), 0.4);
            SkillFx.fx(level, "rpg:arrow_spark", blade, Opt.of("dir", V.unit(new Vec3(Kit.rand() - 0.5, 1, Kit.rand() - 0.5)), "speed", 1.2, "size", 0.1, "grow", 0.6,
                    "life", 0.4, "color", JobFx.inner(j), "alpha", 1.0));
            return true;
        }
        return false;
    }
}
