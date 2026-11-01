package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Combat;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgCombat;
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
 * 0.51.0 2차 직업 — 마검사 각성 뵐룬드(룬 나이트) · 반전 투르스(헥스 블레이드) 특성 · 태세 (서버 — jobs.json advance):
 * <ul>
 * <li>룬 나이트 「마력 각인」 (쌓고 터지는 것은 마검사와 같음 — JobTraits.mark · markBonus, 최대 trait.max = 5): 각인이 터질 때 (평타 · 스물네 룬의 대검) 둘레
 * spreadRadius 칸의 다른 적에게 룬 폭발이 번짐 (위력 × markBurst × spread × 터진 수 — 마법 피해). 룬 담금질 (태세): 칼 평타가 맞을 때마다 몸 둘레의 마력 검
 * swords 자루가 함께 벰 (위력 × echo × swords — 숫자 한 줄) · 사거리 +reach (JobTraits.startStance) · 금빛 룬 대검 (SWING_LOOK runeknight_temper)</li>
 * <li>헥스 블레이드 「뒤집힌 룬」 (1차와 거꾸로): 칼 평타가 맞힌 적에 저주 룬 +1 (저주의 칼 동안 + runes — 최대 trait.max · runeFor 틱). 직업 스킬 피해가 그 적에게
 * 들어가면 (JobKit) 그 피해 × (1 + perRune × 룬) — 들어간 뒤 룬을 모두 떼어 터뜨리고, rootAt 개였으면 rootFor 틱 묶음 (보스는 둔화). 저주의 칼 (태세): 칼 평타에
 * 저주 불꽃 (위력 × flame)</li>
 * </ul>
 */
public final class JobTraits7 {
    private JobTraits7() {}

    static final String RK = "runeknight";
    static final String HX = "hexblade";
    static final String TEMPER = "runeknight_temper";
    static final String CURSE = "hexblade_curse";
    /** 평타에 더한 몫의 구분 (WeaponBonus.parts) */
    static final String KEY_TEMPER = "job_temper";
    static final String KEY_CURSE = "job_curse";

    static final class State {
        /** 헥스 블레이드 게이지: 마지막으로 룬을 새긴 적 */
        UUID runeTarget;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** 저주 룬: "공격자|적" → {수, 끝나는 틱} */
    private static final Map<String, long[]> RUNES = new HashMap<>();

    static void init() {
        RpgCombat.addScaledBonusHook(JobTraits7::temperBonus);
        RpgCombat.addScaledBonusHook(JobTraits7::curseBonus);
        RpgCombat.addWeaponHitHook(JobTraits7::onWeaponHit);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            RUNES.clear();
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

    static void reset(ServerPlayer sp) {
        STATES.remove(sp.getUUID());
        String p = sp.getUUID() + "|";
        RUNES.keySet().removeIf(k -> k.startsWith(p));
    }

    static void forget(UUID id) {
        STATES.remove(id);
        String p = id + "|";
        RUNES.keySet().removeIf(k -> k.startsWith(p));
    }

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st != null) st.runeTarget = null;
        String p = id + "|";
        RUNES.keySet().removeIf(k -> k.startsWith(p));
    }

    // ------------------------------------------------------------ 룬 나이트: 각인이 터지면 번짐

    /**
     * 각인 n 개가 터짐 (victim 에서): 둘레 spreadRadius 칸의 다른 적에게 룬 폭발이 번짐 (위력 × markBurst × spread × n — 마법 피해). 그림: 터진 자리의 금빛 글자 ·
     * 둘레로 퍼지는 룬 고리 · 번진 적마다 작은 글자 터짐
     */
    static void spread(ServerPlayer sp, LivingEntity victim, Jobs.Job j, int n) {
        if (j == null || !RK.equals(j.id()) || victim == null || n <= 0 || !(victim.level() instanceof ServerLevel level)) return;
        double r = trait(j, "spreadRadius", 3.0);
        double mult = trait(j, "markBurst", 1.0) * trait(j, "spread", 0.5) * n;
        Vec3 at = Kit.bodyPoint(victim);
        if (RuneKit.fxQuota("jobSpread", 6)) {
            JobFx.glyph(level, at, 0.55 + Kit.bodyPad(victim) * 0.3, (int) (Kit.rand() * 3), 4, 2, 0.3, false, Opt.empty(), j);
            JobFx.runeRing(level, V.up(victim.position(), 0.12), r * 0.9, j, 8, Kit.rand() * 45, 0.35, Opt.of("alpha", 0.85));
            SkillFx.shock(level, V.up(victim.position(), 0.1), JobFx.inner(j), r, 0.35);
            SkillFx.play(level, "block.amethyst_block.resonate", at, 1.7, 0.6);
        }
        String life = RuneKit.lifeToken(sp);
        double P = JobSystem.power(sp, j);
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, victim.position(), r, 2.5)) {
            if (e == victim) continue;
            JobKit.strike(sp, e, j, P, mult, true, false, life, 0, d -> {
                if (cnt[0]++ < 6 && e.level() instanceof ServerLevel lv) JobFx.glyph(lv, Kit.bodyPoint(e), 0.4 + Kit.bodyPad(e) * 0.25, cnt[0] % 3, 4, 2, 0.28, false,
                        Opt.empty(), j);
            });
        }
    }

    // ------------------------------------------------------------ 헥스 블레이드: 저주 룬

    private static String key(Entity attacker, Entity victim) {
        return attacker.getUUID() + "|" + victim.getUUID();
    }

    /** 이 적의 저주 룬 수 (끝났으면 0) */
    static int runes(ServerPlayer sp, Entity e) {
        if (e == null) return 0;
        long[] m = RUNES.get(key(sp, e));
        return m == null || m[1] < Tasks.now() ? 0 : (int) m[0];
    }

    /** 저주 룬 n 개 더 (최대 trait.max · runeFor 틱) — 머리 위 룬 수 (거꾸로 선 ᚦ 가 한 줄로) */
    static void addRunes(ServerPlayer sp, LivingEntity e, Jobs.Job j, int n) {
        if (e == null || e.isRemoved() || n <= 0 || j == null) return;
        long now = Tasks.now();
        long[] m = RUNES.computeIfAbsent(key(sp, e), k -> new long[2]);
        if (m[1] < now) m[0] = 0;
        int max = j.trait() == null ? 5 : Math.max(1, j.trait().max());
        m[0] = Math.min(max, m[0] + n);
        m[1] = now + (long) trait(j, "runeFor", 200);
        of(sp).runeTarget = e.getUUID();
        if (e.level() instanceof ServerLevel level && RuneKit.fxQuota("jobHexRune", 6)) runeMarks(level, sp, e, j, (int) m[0], true);
        JobTraits.sendGauge(sp, false);
    }

    /** 머리 위 룬 수: 작은 ᚦ 가 한 줄로 (마지막 것이 밝게 박힘) */
    static void runeMarks(ServerLevel level, ServerPlayer sp, LivingEntity e, Jobs.Job j, int n, boolean fresh) {
        Vec3 over = RuneKit.overHead(e, 0.5);
        Vec3 right = V.rightOf(V.flat(V.sub(over, sp.getEyePosition())));
        for (int i = 0; i < n; i++) {
            Vec3 at = V.add(over, right, (i - (n - 1) / 2.0) * 0.3);
            boolean last = fresh && i == n - 1;
            JobFx.thorn(level, at, 0.15, last ? 0 : 2, last ? 2 : 1, last ? 0.5 : 0.7, false, Opt.of("vel", RuneKit.entVel(e), "alpha", last ? 1.0 : 0.85), j);
        }
    }

    /** 저주 룬을 모두 떼어 냄 — 뗀 수 */
    static int takeRunes(ServerPlayer sp, Entity e) {
        int n = runes(sp, e);
        RUNES.remove(key(sp, e));
        JobTraits.sendGauge(sp, false);
        return n;
    }

    /** 이번 직업 스킬 피해의 룬 배율 (헥스 블레이드: 1 + perRune × 그 적의 룬 — 들어간 뒤 afterJobHit 가 뗌). 다른 직업 1 */
    static double runeMult(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        if (j == null || !HX.equals(j.id()) || e == null) return 1;
        int n = runes(sp, e);
        return n <= 0 ? 1 : 1 + trait(j, "perRune", 0.12) * n;
    }

    /** 직업 스킬 피해가 들어간 뒤 (JobKit): 헥스 블레이드 — 그 적의 룬을 모두 터뜨림 (rootAt 개면 rootFor 틱 묶음 · 보스는 둔화) */
    static void afterJobHit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double dealt) {
        if (j == null || !HX.equals(j.id()) || !(dealt > 0) || e == null) return;
        int n = takeRunes(sp, e);
        if (n <= 0) return;
        boolean full = n >= trait(j, "rootAt", 5);
        if (full) {
            boolean boss = mmorpg.fabric.boss.Bosses.isBossEntity(e);
            RuneKit.effect(e, "slowness", (int) trait(j, "rootFor", 20), boss ? 1 : 10);
        }
        if (e.level() instanceof ServerLevel level && RuneKit.fxQuota("jobHexBurst", 6)) detonateFx(level, e, j, n, full);
    }

    /** 룬이 터짐: 몸에서 거꾸로 선 ᚦ 가 금이 가 터짐 (수만큼 커짐) · 먹빛 불꽃 · 다섯이면 발밑을 묶는 룬 고리 */
    static void detonateFx(ServerLevel level, LivingEntity e, Jobs.Job j, int n, boolean full) {
        Vec3 b = Kit.bodyPoint(e);
        double s = 0.45 + 0.08 * n + Kit.bodyPad(e) * 0.3;
        JobFx.thorn(level, b, s, 4, 2, 0.32, false, Opt.of("vel", RuneKit.entVel(e), "spin", Kit.rand() * 20 - 10), j);
        JobFx.burst(level, b, 0.5 + 0.1 * n + Kit.bodyPad(e) * 0.3, j, 0.32, Opt.empty());
        SkillFx.sparks(level, b, JobFx.inner(j), 4 + n * 2, 3.5);
        SkillFx.play(level, "block.amethyst_cluster.break", b, 0.7 + 0.05 * n, 0.8);
        if (full) {
            JobFx.runeRing(level, V.up(e.position(), 0.1), 0.7 + Kit.bodyPad(e), j, 6, Kit.rand() * 60, 1.0, Opt.of("alpha", 0.9, "vel", RuneKit.entVel(e)));
            SkillFx.play(level, "block.chain.break", b, 0.6, 0.9);
        }
    }

    /** 시험용: 저주 룬을 정함 */
    public static void setRunes(ServerPlayer sp, LivingEntity e, int n) {
        RUNES.put(key(sp, e), new long[] {Math.max(0, n), Tasks.now() + 200});
        of(sp).runeTarget = e.getUUID();
        JobTraits.sendGauge(sp, false);
    }

    /** 시험용: 저주 룬 수 */
    public static int runesOf(ServerPlayer sp, Entity e) {
        return runes(sp, e);
    }

    /** 게이지 값: 헥스 블레이드 = 마지막으로 룬을 새긴 적의 룬 수 */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !HX.equals(j.id())) return 0;
        State st = STATES.get(sp.getUUID());
        if (st == null || st.runeTarget == null) return 0;
        Entity e = sp.level().getEntity(st.runeTarget);
        return e == null || e.isRemoved() || !e.isAlive() ? 0 : runes(sp, e);
    }

    // ------------------------------------------------------------ 평타 훅

    /** 룬 담금질: 평타가 맞을 때마다 마력 검 swords 자루가 함께 벰 (위력 × echo × swords) */
    private static RpgCombat.Bonus temperBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (w == null || victim == null || !JobTraits.stanceOn(sp, TEMPER)) return null;
        Jobs.Job j = jobIf(sp, RK);
        if (j == null || !j.usesWeapon(w.type())) return null;
        Jobs.Skill k = j.skill(TEMPER);
        double echo = k == null ? 0.37 : JobUpgrades.num(sp, k, "echo", 0.37);
        int swords = k == null ? 3 : Math.max(1, (int) k.num("swords", 3));
        double add = JobSystem.power(sp, j) * echo * swords * mmorpg.fabric.stattree.StatTrees.atkMult(sp);
        return new RpgCombat.Bonus(add, "", KEY_TEMPER, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    /** 저주의 칼: 칼 평타에 저주 불꽃 (위력 × flame) */
    private static RpgCombat.Bonus curseBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (w == null || victim == null || !JobTraits.stanceOn(sp, CURSE)) return null;
        Jobs.Job j = jobIf(sp, HX);
        if (j == null || !j.usesWeapon(w.type())) return null;
        Jobs.Skill k = j.skill(CURSE);
        double flame = k == null ? 0.5 : JobUpgrades.num(sp, k, "flame", 0.5);
        double add = JobSystem.power(sp, j) * flame * mmorpg.fabric.stattree.StatTrees.atkMult(sp);
        return new RpgCombat.Bonus(add, "", KEY_CURSE, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    private static boolean has(RpgCombat.WeaponBonus b, String key) {
        if (b == null) return false;
        for (RpgCombat.Bonus p : b.parts()) if (key.equals(p.key())) return true;
        return false;
    }

    /**
     * 무기로 맞힘 (피해가 들어간 뒤): 룬 나이트 — 담금질의 마력 검 셋이 맞은 적을 둘러 벰 (그림), 헥스 블레이드 — 저주 룬 +1 (저주의 칼 동안 + runes) · 불꽃 그림
     */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (victim == null || w == null) return;
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || !j.usesWeapon(w.type())) return;
        if (RK.equals(j.id()) && has(bonus, KEY_TEMPER)) {
            Jobs.Skill k = j.skill(TEMPER);
            int swords = k == null ? 3 : Math.max(1, (int) k.num("swords", 3));
            RuneKit.later(2, () -> {
                if (victim.isRemoved() || !(victim.level() instanceof ServerLevel lv)) return;
                temperSlashes(sp, lv, victim, j, swords);
            });
            return;
        }
        if (!HX.equals(j.id()) || !Combat.isMeleeWeapon(w)) return;
        boolean cursed = JobTraits.stanceOn(sp, CURSE);
        Jobs.Skill k = j.skill(CURSE);
        addRunes(sp, victim, j, cursed ? Math.max(1, (int) (k == null ? 2 : k.num("runes", 2))) : 1);
        if (has(bonus, KEY_CURSE)) {
            ServerLevel level = sp.level();
            Vec3 at = Kit.bodyPoint(victim);
            JobFx.burst(level, at, 0.55 + Kit.bodyPad(victim) * 0.35, j, 0.3, Opt.of("vel", RuneKit.entVel(victim)));
            JobFx.smoke(level, V.up(at, 0.2), 0.5 + Kit.bodyPad(victim) * 0.3, j, 0.5, Opt.of("vel", V.up(RuneKit.entVel(victim), 0.6)));
            SkillFx.play(level, "item.firecharge.use", at, 0.7, 0.45);
        }
    }

    /** 룬 담금질 평타: 몸 둘레의 마력 검 swords 자루가 맞은 적을 세 쪽에서 둘러 벰 (적 쪽으로 날아가 교차 — 금빛 줄 · 맞은 자리 글자 반짝) */
    static void temperSlashes(ServerPlayer sp, ServerLevel level, LivingEntity victim, Jobs.Job j, int swords) {
        Vec3 b = Kit.bodyPoint(victim);
        Vec3 toMe = V.flat(V.sub(sp.position(), victim.position()));
        Vec3 f = V.len(toMe) > 0.1 ? V.unit(toMe) : Kit.facingOf(sp);
        for (int i = 0; i < swords; i++) {
            double a = Math.toRadians(-60 + 60 * i + (Kit.rand() - 0.5) * 16);
            Vec3 dir = V.turnY(f, Math.toDegrees(a));
            Vec3 from = V.up(V.add(b, dir, 1.6 + Kit.bodyPad(victim)), 0.5 + 0.35 * (i % 2));
            Vec3 to = V.add(V.up(b, -0.2 - 0.2 * (i % 2)), dir, -0.9 - Kit.bodyPad(victim));
            JobFx3d.swordFly(level, from, to, 26, 0.7, 0.08, j, Opt.of("shine", 0.4));
        }
        JobFx.hit(victim, j, 1.0);
        JobFx.glyph(level, b, 0.4 + Kit.bodyPad(victim) * 0.25, (int) (Kit.rand() * 3), 4, 2, 0.25, false, Opt.of("vel", RuneKit.entVel(victim)), j);
        SkillFx.play(level, "item.trident.hit", b, 1.5, 0.6);
    }

    // ------------------------------------------------------------ 틱 · 태세

    /** 4틱마다 (JobTraits.tick): 끝난 룬 지우기 · 16틱마다 룬이 남은 적 머리 위에 옅은 ᚦ (게이지 적만 — 다른 사람의 화면을 덮지 않게) */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        if (RUNES.size() > 256) RUNES.values().removeIf(m -> m[1] < now);
        if (!HX.equals(j.id()) || now % 16 != 0) return;
        State st = STATES.get(sp.getUUID());
        if (st == null || st.runeTarget == null) return;
        Entity e = sp.level().getEntity(st.runeTarget);
        if (!(e instanceof LivingEntity le) || !le.isAlive() || !RuneKit.fxQuota("jobHexMark", 4)) return;
        int n = runes(sp, le);
        if (n > 0) runeMarks(sp.level(), sp, le, j, n, false);
    }

    /** 태세 연출 (8틱마다 — JobTraits.stanceFx): 룬 담금질 = 칼의 금빛 룬 불 · 글자 불티 / 저주의 칼 = 칼의 먹빛 불꽃 · 호박빛 불티. 그렸으면 true */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        ServerLevel level = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        if (TEMPER.equals(stance)) {
            RuneKit.rfx2(level, "sword", V.up(h.hand(), 0.45), Opt.of("dir", V.UP, "size", 0.7, "life", 0.42, "alpha", 0.75, "fade", 0.5, "vel", RuneKit.bodyVel(sp)),
                    JobKit.glow(j), JobKit.core(j));
            SkillFx.fx(level, "rpg:arrow_spark", V.up(h.hand(), 0.5), Opt.of("dir", V.UP, "speed", 1.5, "size", 0.13, "grow", 0.7, "life", 0.4, "color", JobKit.core(j),
                    "alpha", 1.0));
            return true;
        }
        if (CURSE.equals(stance)) {
            Vec3 blade = V.up(V.add(h.hand(), h.f(), 0.45), 0.3);
            JobFx.smoke(level, blade, 0.28, j, 0.45, Opt.of("vel", V.up(RuneKit.bodyVel(sp), 0.8), "alpha", 0.8));
            SkillFx.fx(level, "rpg:arrow_spark", blade, Opt.of("dir", V.UP, "speed", 1.4, "size", 0.12, "grow", 0.6, "life", 0.4, "color", JobFx.inner(j), "alpha", 1.0));
            return true;
        }
        return false;
    }

    /** 이 적들 중 저주 룬이 있는 적 (재앙의 룬 — 터지기 전에 그림) */
    static int cursedCount(ServerPlayer sp, List<LivingEntity> list) {
        int n = 0;
        for (LivingEntity e : list) if (runes(sp, e) > 0) n++;
        return n;
    }
}
