package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Combat;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 특성 · 태세 (서버 — jobs.json advance):
 * <ul>
 * <li>시구르드(소드마스터) 「투지」: 쌓고 쓰는 것은 검사와 같음 (JobTraits — 최대 8). 투지 8 에서 피해 직업 스킬을 쓰면 그 스킬이 치명 — critFor 동안 직업 스킬 피해
 * × (1 + crit) · 치명 숫자 (JobKit), 나가면 투지 critUse 개를 씀. 용의 심장 (태세): 칼 평타 두 번째 베기 (echo) · 맞으면 받아치는 베기 (위력 × counter ·
 * counterGap 틱에 한 번 · counterRange 칸 안의 때린 적) · 받는 피해 −guard (Guards) · 투지 유지 (JobTraits)</li>
 * <li>다인슬레이프(블러드 나이트) 「피의 갈증」: 피해 직업 스킬을 쓸 때 체력이 costMin 이상이면 최대 체력 × cost 를 바쳐 그 스킬 위력 × (1 + bonus) (스킬이
 * 제 값 cost 를 바치면 그것만), 직업 스킬 피해 × leech 흡혈 (피의 질주 뒤 leechFor · 회그니의 맹세 동안 두 배), 직업 스킬이 맞힌 적에 상처 +1 (최대 trait.max ·
 * woundFor 틱 — 맹세 동안 사라지지 않음) → 상처 터뜨리기 · 끝나지 않는 전투가 씀. 회그니의 맹세: 칼 평타에 피 칼날 (echo — 그 몫도 두 배 흡혈)</li>
 * </ul>
 */
public final class JobTraits5 {
    private JobTraits5() {}

    static final String SM = "swordmaster";
    static final String BK = "bloodknight";
    static final String HEART = "swordmaster_heart";
    static final String OATH = "bloodknight_oath";
    /** 평타에 더한 몫의 구분 (WeaponBonus.parts) */
    static final String KEY_HEART = "job_heart";
    static final String KEY_OATH = "job_oath";
    /** 피 · 상처 빛 (블러드 나이트 색이 없을 때) */
    static final double[] BLOOD = {0.62, 0.02, 0.08};
    static final double[] BLOOD_LIGHT = {0.95, 0.22, 0.24};

    static final class State {
        /** 소드마스터: 이 틱까지 직업 스킬 피해 × (1 + crit) · 치명 숫자 */
        long critUntil;
        /** 이번 시전에 치명이 걸렸는지 (afterCast 가 투지를 씀) */
        boolean critPending;
        /** 블러드 나이트: 이번 시전에 체력을 바침 */
        boolean payNow;
        /** 피의 질주: 이 틱까지 흡혈 두 배 */
        long leechUntil;
        /** 용의 심장: 마지막 받아치기 틱 */
        long counterAt = Long.MIN_VALUE / 2;
        /** 흡혈 숫자 (모아서 1초에 한 번) */
        double leechAcc;
        long leechShown;
        /** 게이지: 마지막으로 상처를 낸 적 */
        UUID woundTarget;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** 상처: "공격자|적" → {수, 끝나는 틱} */
    private static final Map<String, long[]> WOUNDS = new HashMap<>();

    static void init() {
        RpgCombat.addScaledBonusHook(JobTraits5::heartBonus);
        RpgCombat.addScaledBonusHook(JobTraits5::oathBonus);
        RpgCombat.addWeaponHitHook(JobTraits5::onWeaponHit);
        RpgHealth.LossHook lh = RpgHealth.lossHook;
        RpgHealth.lossHook = (sp, src, loss, before, after) -> {
            lh.lost(sp, src, loss, before, after);
            onLoss(sp, src, loss);
        };
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            WOUNDS.clear();
        });
    }

    static State of(ServerPlayer sp) {
        return STATES.computeIfAbsent(sp.getUUID(), k -> new State());
    }

    /** 직업이 id 면 그 직업 (아니면 null) */
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
        WOUNDS.keySet().removeIf(k -> k.startsWith(p));
    }

    static void forget(UUID id) {
        STATES.remove(id);
        String p = id + "|";
        WOUNDS.keySet().removeIf(k -> k.startsWith(p));
    }

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st != null) {
            st.critUntil = 0;
            st.critPending = false;
            st.payNow = false;
            st.leechUntil = 0;
            st.woundTarget = null;
        }
        String p = id + "|";
        WOUNDS.keySet().removeIf(k -> k.startsWith(p));
    }

    // ------------------------------------------------------------ 시전 (JobSystem.Slot.cast)

    /** 위력 배율 (블러드 나이트 피의 갈증: 체력을 바칠 수 있으면 × (1 + bonus) — 바치는 것은 나간 뒤 afterCast). 다른 직업 1 */
    static double castMult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        if (j == null || !BK.equals(j.id())) return 1;
        State st = of(sp);
        st.payNow = false;
        if (k == null || k.total() <= 0 || k.def().has("cost")) return 1; // 태세 · 제 값을 바치는 스킬 (맹세 · 끝나지 않는 전투) 은 그것만
        if (sp.getHealth() < sp.getMaxHealth() * trait(j, "costMin", 0.25) || !RpgHealth.isRpgMode(sp)) return 1;
        st.payNow = true;
        return 1 + trait(j, "bonus", 0.1);
    }

    /** 소드마스터: 투지가 critAt 이상이면 이번 피해 스킬을 치명으로 (지금부터 그 스킬이 끝날 때까지 — 나가지 않으면 afterCast 가 거둠) */
    static void beforeCast(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        if (j == null || !SM.equals(j.id()) || k == null || k.total() <= 0) return;
        if (JobTraits.stacks(sp) < trait(j, "critAt", 8)) return;
        State st = of(sp);
        st.critPending = true;
        st.critUntil = Tasks.now() + Math.max(20, (long) k.num("t", 20) + 12);
    }

    /** 나간 뒤: 소드마스터 치명 → 투지 critUse 개를 씀 (나가지 않았으면 치명을 거둠), 블러드 나이트 → 체력을 바침. 알림 줄에 덧붙일 말 (없으면 null) */
    static String afterCast(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, boolean ok) {
        if (j == null) return null;
        State st = STATES.get(sp.getUUID());
        if (st == null) return null;
        if (SM.equals(j.id()) && st.critPending) {
            st.critPending = false;
            if (!ok) {
                st.critUntil = 0;
                return null;
            }
            JobTraits.useStacks(sp, (int) trait(j, "critUse", 3));
            critFx(sp, j);
            return "§6치명 +" + Math.round(trait(j, "crit", 0.3) * 100) + "%";
        }
        if (BK.equals(j.id()) && st.payNow) {
            st.payNow = false;
            if (!ok) return null;
            double paid = payBlood(sp, j, trait(j, "cost", 0.04));
            return paid > 0 ? "§4피 −" + Math.round(trait(j, "cost", 0.04) * 100) + "% · 위력 +" + Math.round(trait(j, "bonus", 0.1) * 100) + "%" : null;
        }
        return null;
    }

    /** 치명 시작: 칼끝 금빛 번쩍 · 몸의 기운 · 소리 */
    private static void critFx(ServerPlayer sp, Jobs.Job j) {
        ServerLevel level = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        JobFx.glint(level, V.up(V.add(h.hand(), h.f(), 0.7), 0.45), 0.7, j, 0.5, Opt.of("vel", RuneKit.bodyVel(sp), "spin", Kit.rand() * 30 - 15));
        JobFx.burst(level, V.up(V.add(h.hand(), h.f(), 0.6), 0.3), 0.45, j, 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
        JobFx.aura(sp, j, 2.6, 0.5, 0.85);
        SkillFx.play(level, "entity.player.attack.crit", sp.position(), 1.2, 0.9);
        SkillFx.play(level, "block.bell.hit", sp.position(), 1.6, 0.6);
    }

    /** 체력 frac (최대 체력의 몫) 을 바침 (1 아래로는 못 냄) — 핏방울이 몸에서 튐. 바친 양 (0 = 못 냄 · 크리에이티브) */
    static double payBlood(ServerPlayer sp, Jobs.Job j, double frac) {
        double paid = RpgHealth.payHp(sp, sp.getMaxHealth() * frac);
        if (paid > 0) {
            ServerLevel level = sp.level();
            Vec3 chest = V.up(sp.position(), 1.1);
            SkillFx.sparks(level, chest, BLOOD_LIGHT, 8, 2.6);
            blood(level, chest, 0.55, j, 0.35, JobFx.hide1p(sp));
            SkillFx.play(level, "entity.player.hurt_sweet_berry_bush", chest, 0.8, 0.6);
        }
        return paid;
    }

    /** 이번 직업 스킬 피해의 치명 배율 (소드마스터 치명 동안 1 + crit — 그 밖 1) */
    static double critMult(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !SM.equals(j.id())) return 1;
        State st = STATES.get(sp.getUUID());
        return st != null && st.critUntil >= Tasks.now() ? 1 + trait(j, "crit", 0.3) : 1;
    }

    // ------------------------------------------------------------ 직업 스킬 피해가 들어간 뒤 (JobKit.hit)

    /** 블러드 나이트: 흡혈 (피해 × leech — 질주 뒤 · 맹세 동안 두 배) · 상처 +1 */
    static void afterJobHit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double dealt) {
        if (j == null || !BK.equals(j.id()) || !(dealt > 0)) return;
        leech(sp, j, dealt * trait(j, "leech", 0.05) * leechMult(sp));
        addWound(sp, e, j, 1);
    }

    /** 흡혈 배수 (피의 질주 뒤 · 회그니의 맹세 동안 2 — 그 밖 1) */
    static double leechMult(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        boolean doubled = (st != null && st.leechUntil >= Tasks.now()) || JobTraits.stanceOn(sp, OATH);
        return doubled ? 2 : 1;
    }

    /** 체력을 amount 회복 (숫자는 모아서 1초에 한 번) — 실제로 오른 양 */
    static double leech(ServerPlayer sp, Jobs.Job j, double amount) {
        if (!(amount > 0) || !sp.isAlive()) return 0;
        double got = RpgHealth.healBy(sp, amount);
        if (got > 0) of(sp).leechAcc += got;
        return got;
    }

    /** 피의 질주: ticks 동안 흡혈 두 배 */
    static void leechFor(ServerPlayer sp, int ticks) {
        State st = of(sp);
        st.leechUntil = Math.max(st.leechUntil, Tasks.now() + ticks);
    }

    // ------------------------------------------------------------ 상처 (블러드 나이트)

    private static String key(Entity attacker, Entity victim) {
        return attacker.getUUID() + "|" + victim.getUUID();
    }

    /** 상처 n 개 더 (최대 trait.max · woundFor 틱) — 맞은 자리에 핏방울 · 머리 위 상처 수 */
    static void addWound(ServerPlayer sp, LivingEntity e, Jobs.Job j, int n) {
        if (e == null || e.isRemoved() || n <= 0) return;
        long now = Tasks.now();
        long[] w = WOUNDS.computeIfAbsent(key(sp, e), k -> new long[2]);
        if (w[1] < now) w[0] = 0;
        int max = j.trait() == null ? 5 : Math.max(1, j.trait().max());
        w[0] = Math.min(max, w[0] + n);
        w[1] = now + (long) trait(j, "woundFor", 160);
        of(sp).woundTarget = e.getUUID();
        if (e.level() instanceof ServerLevel level && RuneKit.fxQuota("jobWound", 6)) woundMarks(level, sp, e, j, (int) w[0]);
        JobTraits.sendGauge(sp, false);
    }

    /** 머리 위 상처 수 (핏방울이 한 줄로 — 마지막 것이 밝게) */
    static void woundMarks(ServerLevel level, ServerPlayer sp, LivingEntity e, Jobs.Job j, int n) {
        Vec3 over = RuneKit.overHead(e, 0.45);
        Vec3 right = V.rightOf(V.flat(V.sub(over, sp.getEyePosition())));
        for (int i = 0; i < n; i++) {
            Vec3 at = V.add(over, right, (i - (n - 1) / 2.0) * 0.26);
            drop(level, at, V.UP, 0.13, i == n - 1 ? JobFx.inner(j) : JobFx.outer(j), 0.8, Opt.of("vel", RuneKit.entVel(e), "grow", 0.2, "fade", 0.4));
        }
    }

    /** 이 적의 상처 수 (끝났으면 0 — 회그니의 맹세 동안은 끝나지 않음) */
    static int wounds(ServerPlayer sp, Entity e) {
        if (e == null) return 0;
        long[] w = WOUNDS.get(key(sp, e));
        if (w == null) return 0;
        long now = Tasks.now();
        if (JobTraits.stanceOn(sp, OATH)) w[1] = Math.max(w[1], now + 20);
        return w[1] < now ? 0 : (int) w[0];
    }

    /** 상처를 모두 떼어 냄 (터뜨림) — 뗀 수 */
    static int takeWounds(ServerPlayer sp, Entity e) {
        int n = wounds(sp, e);
        WOUNDS.remove(key(sp, e));
        JobTraits.sendGauge(sp, false);
        return n;
    }

    /** 시험용: 상처를 정함 */
    public static void setWounds(ServerPlayer sp, LivingEntity e, int n) {
        WOUNDS.put(key(sp, e), new long[] {Math.max(0, n), Tasks.now() + 200});
        of(sp).woundTarget = e.getUUID();
        JobTraits.sendGauge(sp, false);
    }

    /** 시험용: 상처 수 */
    public static int woundsOf(ServerPlayer sp, Entity e) {
        return wounds(sp, e);
    }

    /** 시험용: 치명 창이 열려 있는지 */
    public static boolean critOn(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.critUntil >= Tasks.now();
    }

    /** 게이지 값: 블러드 나이트 = 마지막으로 상처를 낸 적의 상처 수 */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !BK.equals(j.id())) return 0;
        State st = STATES.get(sp.getUUID());
        if (st == null || st.woundTarget == null) return 0;
        Entity e = sp.level().getEntity(st.woundTarget);
        return e == null || e.isRemoved() || !e.isAlive() ? 0 : wounds(sp, e);
    }

    // ------------------------------------------------------------ 평타 훅

    /** 용의 심장: 칼 평타 두 번째 베기 (평타 × echo) */
    private static RpgCombat.Bonus heartBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (w == null || !"sword".equals(w.type()) || !(dmg > 0) || !JobTraits.stanceOn(sp, HEART)) return null;
        Jobs.Job j = jobIf(sp, SM);
        if (j == null) return null;
        Jobs.Skill k = j.skill(HEART);
        double echo = k == null ? 0.42 : JobUpgrades.num(sp, k, "echo", 0.42);
        return new RpgCombat.Bonus(dmg * echo, "", KEY_HEART, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    /** 회그니의 맹세: 칼 평타에 피 칼날 (평타 × echo) */
    private static RpgCombat.Bonus oathBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (w == null || !"sword".equals(w.type()) || !(dmg > 0) || !JobTraits.stanceOn(sp, OATH)) return null;
        Jobs.Job j = jobIf(sp, BK);
        if (j == null) return null;
        Jobs.Skill k = j.skill(OATH);
        double echo = k == null ? 0.42 : JobUpgrades.num(sp, k, "echo", 0.42);
        return new RpgCombat.Bonus(dmg * echo, "", KEY_OATH, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    /** 무기로 맞힘: 맹세의 피 칼날이 들어갔으면 맞은 자리에 핏빛 베기 · 그 몫의 흡혈 (두 배) · 상처 +1 */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (bonus == null || victim == null || w == null || !Combat.isMeleeWeapon(w)) return;
        Jobs.Job j = jobIf(sp, BK);
        if (j == null) return;
        for (RpgCombat.Bonus p : bonus.parts()) {
            if (!KEY_OATH.equals(p.key())) continue;
            leech(sp, j, p.add() * trait(j, "leech", 0.05) * 2);
            addWound(sp, victim, j, 1);
            Jobs.Job jj = j;
            RuneKit.later(3, () -> {
                if (victim.isRemoved() || !(victim.level() instanceof ServerLevel lv)) return;
                JobFx.hit(victim, jj, 1.0);
                SkillFx.play(lv, "item.spear.attack_miss", Kit.bodyPoint(victim), 1.3, 0.5);
            });
            return;
        }
    }

    // ------------------------------------------------------------ 맞음 (용의 심장 받아치기)

    private static void onLoss(ServerPlayer sp, DamageSource src, double loss) {
        if (!(loss > 0) || !JobTraits.stanceOn(sp, HEART)) return;
        Jobs.Job j = jobIf(sp, SM);
        if (j == null) return;
        if (!(src.getEntity() instanceof LivingEntity att) || att == sp || !att.isAlive()) return;
        Jobs.Skill k = j.skill(HEART);
        if (k == null || att.distanceToSqr(sp) > Math.pow(k.num("counterRange", 6), 2)) return;
        State st = of(sp);
        long now = Tasks.now();
        if (now - st.counterAt < (long) k.num("counterGap", 20)) return;
        st.counterAt = now;
        double mult = JobUpgrades.num(sp, k, "counter", k.num("counter", 1.0));
        double P = JobSystem.power(sp, j);
        String life = RuneKit.lifeToken(sp);
        // 받아치기: 때린 적 쪽으로 몸을 틀어 금빛 초승달 (2틱 뒤 — 맞는 중에 겹쳐 치지 않게)
        RuneKit.later(2, () -> {
            if (!RuneKit.sameLife(sp, life) || att.isRemoved()) return;
            ServerLevel level = sp.level();
            Vec3 f = V.flat(V.sub(att.position(), sp.position()));
            Vec3 at = V.up(V.add(sp.position(), f, 1.1), 1.1);
            JobFx.slashCard(level, at, f, Kit.rand() < 0.5 ? 172.0 : -8.0, 2.8, 0.28, V.ZERO, j, Opt.empty());
            JobFx.glint(level, V.up(V.add(sp.position(), f, 0.8), 1.4), 0.45, j, 0.4, Opt.empty());
            SkillFx.play(level, "item.trident.hit", sp.position(), 1.5, 0.7);
            SkillFx.play(level, "block.amethyst_block.hit", sp.position(), 1.9, 0.7);
            JobKit.motion(sp, HEART + ":counter");
            JobKit.strike(sp, att, j, P, mult, false, false, life, 0, d -> JobFx.hit(att, j, 1.2));
        });
    }

    // ------------------------------------------------------------ 틱 · 태세 연출

    /** 4틱마다 (JobTraits.tick): 흡혈 숫자 (1초에 한 번) */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        State st = STATES.get(sp.getUUID());
        if (st == null) return;
        if (st.leechAcc >= 0.5 && now - st.leechShown >= 20) {
            DamageNumbers.show(sp, st.leechAcc, DamageNumbers.Style.HEAL, "", 0);
            st.leechAcc = 0;
            st.leechShown = now;
        }
        if (WOUNDS.size() > 256) WOUNDS.values().removeIf(w -> w[1] < now);
    }

    /** 태세 연출 (8틱마다 — JobTraits.stanceFx): 용의 심장 = 가슴의 금빛 불씨 · 칼의 금빛 / 회그니의 맹세 = 칼에서 떨어지는 핏방울. 그렸으면 true */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        ServerLevel level = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        if (HEART.equals(stance)) {
            Vec3 chest = V.add(V.up(sp.position(), 1.25), V.flat(sp.getViewVector(1F)), 0.25);
            JobFx.burst(level, chest, 0.3, j, 0.35, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.8));
            SkillFx.fx(level, "rpg:light_cut", V.up(h.hand(), 0.2), Opt.of("dir", V.UP, "size", 0.5, "thick", 0.08, "life", 0.4, "color", JobKit.glow(j), "alpha", 0.85));
            SkillFx.fx(level, "rpg:arrow_spark", V.up(h.hand(), 0.3), Opt.of("dir", V.UP, "speed", 1.2, "size", 0.12, "grow", 0.6, "life", 0.4, "color", JobKit.glow(j),
                    "alpha", 1.0));
            return true;
        }
        if (OATH.equals(stance)) {
            // 칼끝에서 떨어지는 핏방울 (1인칭 오른쪽 아래에도 보임) · 몸의 핏빛 기운은 JobTraits 가 (aura)
            Vec3 tip = V.up(V.add(h.hand(), h.f(), 0.55), 0.15);
            drop(level, tip, V.scale(V.UP, -1), 0.12, JobFx.outer(j), 0.45, Opt.of("speed", 2.4, "fade", 0.3));
            SkillFx.sparks(level, tip, JobFx.outer(j), 2, 1.2);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------ 피 그림

    /** 핏방울 하나 (rfx_jdrop — 눈물 꼴, dir 쪽으로 늘어짐: 빛 칸 0 · 심지 칸 1). o: speed · vel · grow · fade · view · owner */
    static void drop(ServerLevel level, Vec3 at, Vec3 dir, double size, double[] color, double life, Opt o) {
        Opt b = o.with("dir", dir, "size", size, "life", life);
        RuneKit.rfx(level, "jdrop", at, b.with("cell", 0.0, "color", color, "alpha", o.num("alpha", 0.95)));
        RuneKit.rfx(level, "jdrop", at, b.with("cell", 1.0, "color", lighter(color), "alpha", o.num("alpha", 0.95)));
    }

    /** 피 튀김 (rfx_jblood — 카메라를 봄: 빛 0 ~ 5 · 심지 6 ~ 11): 가운데에서 핏방울이 튀어 흩어짐 */
    static void blood(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        double[] glow = j == null ? BLOOD : JobFx.outer(j);
        double[] core = j == null ? BLOOD_LIGHT : JobFx.inner(j);
        JobFx.two(level, "jblood", BLOOD_FRAMES, at, life, o.with("size", size, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360, "fade", o.num("fade",
                0.3)), glow, core);
    }

    /** rfx_jblood 장 수 */
    static final int BLOOD_FRAMES = 6;

    static double[] lighter(double[] c) {
        return new double[] {c[0] + (1 - c[0]) * 0.35, c[1] + (1 - c[1]) * 0.35, c[2] + (1 - c[2]) * 0.35};
    }
}
