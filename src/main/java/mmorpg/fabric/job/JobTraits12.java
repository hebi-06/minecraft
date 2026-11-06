package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 — 사신 2차 (재설계안 2판 — 2026-11-06 사용자 "다음 작업") 의 특성 (서버 — jobs.json advance, 소울 리퍼의 영혼 쌓임 · 이동 · 흡혈은 JobTraits3 의
 * 사신 줄기):
 * <ul>
 * <li>소울 리퍼 「영혼」 (최대 15): 직업 스킬이 맞힌 적마다 영혼 +1 ({@link #afterJobHit}) → 영혼이 가득일 때 직업 스킬이 맞으면 영혼 해방 ({@link #release} — 망령
 * wisps 개가 둘레 wispRange 칸 적들에게 날아가 박힘 · 위력 × wispHit 씩 · 체력 heal 회복 · 영혼 0), 바운티풀 하베스트 동안 흡혈 × leech · 느려짐
 * ({@link #harvestOn}), 랜턴 오브 소울즈의 남은 시간 (태세 줄 — {@link #stanceLine})</li>
 * <li>엑시큐셔너 「처형」 (1차와 거꾸로 — 흡혈 없음): 직업 스킬은 대상이 잃은 체력 10% 마다 + per (최대 maxBonus — {@link #hitMult}), 체력 execute 아래인 보스 아닌 적은
 * 직업 스킬 한 방에 쓰러짐 (보스는 + bossBonus), 직업 스킬 (궁극기 빼고 — 궁극기는 다시 쓰기) 로 쓰러뜨리면 그 스킬의 재사용 대기 초기화 ({@link #killedBy}), 컨뎀 선고
 * 표식 (받는 직업 스킬 피해 + markBonus) · 섀도 스토크 (다음 직업 스킬 + next) · 원 라스트 데이 (처형 기준 · 이동 · 다른 직업 스킬 재사용 대기), 게이지 = 마지막으로 친
 * 적의 잃은 체력 (한 칸 10%)</li>
 * </ul>
 */
public final class JobTraits12 {
    private JobTraits12() {}

    static final String SR = "soulreaper";
    static final String EX = "executioner";
    static final String LASTDAY = "executioner_lastday";

    /** 소울 리퍼 영혼 · 엑시큐셔너 처형 그림 빛 (영혼 푸른 불 · 해 질 녘 금빛 · 보랏빛) */
    static final double[] SOUL = {0.3, 0.85, 1.0};
    static final double[] SOUL_CORE = {0.85, 0.97, 1.0};
    static final double[] DUSK = {1.0, 0.72, 0.26};
    static final double[] DUSK_CORE = {1.0, 0.93, 0.74};
    static final double[] VIOLET = {0.62, 0.22, 0.6};

    static final class State {
        /** 엑시큐셔너: 마지막으로 친 적 · 그 적의 잃은 체력 (한 칸 10% — 게이지) */
        UUID target;
        int missing;
        /** 컨뎀 선고 표식: 적 → 끝나는 틱 */
        final Map<UUID, Long> condemned = new HashMap<>();
        /** 섀도 스토크: 다음 직업 스킬 + next (그 스킬이 정해지기 전 · 정해진 뒤 — 스킬 id · 끝나는 틱) */
        long stalkUntil;
        String stalkSkill;
        long stalkSkillUntil;
        /** 원 라스트 데이: 처형 뒤 이동 속도 + speed 가 끝나는 틱 */
        long hasteUntil;
        /** 소울 리퍼 바운티풀 하베스트: 흡혈 × leech · 느려짐이 끝나는 틱 */
        long harvestUntil;
        double harvestLeech = 1;
        /** 소울 리퍼 랜턴 오브 소울즈: 등불이 꺼지는 틱 · 뜬 틱 (태세 줄) */
        long lanternUntil;
        long lanternAt;
    }

    /** 바운티풀 하베스트 동안 이동 속도 − slow (낫을 땅에 꽂음) */
    private static final Identifier HARVEST_SLOW = Mmorpg.id("job_harvest_slow");

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** 영혼 해방 · 처형의 피해 (영혼 · 처형을 다시 부르지 않게) */
    private static boolean inner;

    static void init() {
        RpgCombat.addWeaponHitHook(JobTraits12::onWeaponHit);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
    }

    static Jobs.Job jobIf(ServerPlayer sp, String id) {
        Jobs.Job j = JobSystem.job(sp);
        return j != null && id.equals(j.id()) ? j : null;
    }

    static double trait(Jobs.Job j, String key, double fallback) {
        return j == null || j.trait() == null ? fallback : j.trait().def().numOr(key, fallback);
    }

    private static State of(ServerPlayer sp) {
        return STATES.computeIfAbsent(sp.getUUID(), k -> new State());
    }

    static void reset(ServerPlayer sp) {
        STATES.remove(sp.getUUID());
        JobTraits3.hasteMod(sp, 0);
        slowMod(sp, 0);
    }

    static void forget(UUID id) {
        STATES.remove(id);
    }

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st == null) return;
        st.condemned.clear();
        st.stalkUntil = 0;
        st.stalkSkill = null;
        st.hasteUntil = 0;
        st.harvestUntil = 0;
        st.harvestLeech = 1;
        st.lanternUntil = 0;
    }

    // ------------------------------------------------------------ 직업 스킬 피해 (JobKit.hit · hitSplit)

    /**
     * 직업 스킬 피해 배율 (JobKit.hit · hitSplit — 피해를 넣기 전): 엑시큐셔너 처형 덤 (잃은 체력 × per × 10 — 최대 maxBonus, 보스는 체력 execute 아래면 + bossBonus)
     * · 선고 표식 + markBonus · 섀도 스토크 + next. 다른 직업은 1
     */
    static double hitMult(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        if (j == null || !EX.equals(j.id()) || e == null || inner) return 1;
        double max = e.getMaxHealth();
        double missing = max > 0 ? Math.max(0, Math.min(1, 1 - e.getHealth() / max)) : 0;
        double m = 1 + Math.min(trait(j, "maxBonus", 0.5), missing * trait(j, "per", 0.05) * 10);
        if (JobKit.isBoss(e) && e.getHealth() <= max * threshold(sp, j)) m *= 1 + trait(j, "bossBonus", 0.3);
        State st = STATES.get(sp.getUUID());
        if (st == null) return m;
        long now = Tasks.now();
        Long until = st.condemned.get(e.getUUID());
        if (until != null && until > now) {
            Jobs.Skill c = j.skill("executioner_condemn");
            m *= 1 + (c == null ? 0.1 : c.num("markBonus", 0.1));
        }
        if (st.stalkSkill != null && st.stalkSkillUntil > now) {
            Jobs.Skill s = j.skill("executioner_stalk");
            m *= 1 + (s == null ? 0.2 : s.num("next", 0.2));
        }
        return m;
    }

    /**
     * 직업 스킬 피해가 들어감 (JobKit.hit · hitSplit): 소울 리퍼는 영혼 +1 → 가득이면 영혼 해방, 엑시큐셔너는 게이지 (그 적의 잃은 체력) · 체력 기준 아래인 보스 아닌
     * 적을 처형
     */
    static void afterJobHit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double got) {
        if (j == null || e == null || inner || !(got > 0)) return;
        if (SR.equals(j.id())) {
            JobTraits3.gainSouls(sp, j, 1, e);
            if (JobTraits3.souls(sp) >= Math.max(1, j.trait().max())) release(sp, j);
            return;
        }
        if (!EX.equals(j.id())) return;
        if (e.isAlive() && !e.isRemoved() && !JobKit.isBoss(e) && e.getHealth() <= e.getMaxHealth() * threshold(sp, j)) execute(sp, e, j);
        track(sp, e, j);
    }

    /** 시험: 지금 처형 기준 (엑시큐셔너가 아니면 0) */
    public static double thresholdOf(ServerPlayer sp) {
        Jobs.Job j = jobIf(sp, EX);
        return j == null ? 0 : threshold(sp, j);
    }

    /** 처형 기준 (체력 몫): 특성 execute · 원 라스트 데이 동안 그 스킬의 execute */
    static double threshold(ServerPlayer sp, Jobs.Job j) {
        double base = trait(j, "execute", 0.15);
        if (JobTraits.stanceOn(sp, LASTDAY)) {
            Jobs.Skill k = j.skill(LASTDAY);
            return Math.max(base, k == null ? 0.25 : k.num("execute", 0.25));
        }
        return base;
    }

    // ------------------------------------------------------------ 소울 리퍼 영혼 해방

    /** 영혼 해방: 영혼을 모두 씀 → 망령 wisps 개가 둘레 wispRange 칸 적들에게 날아가 박힘 (한 적에 여럿이면 한 번에 그 몫) · 체력 heal 회복 */
    static void release(ServerPlayer sp, Jobs.Job j) {
        int had = JobTraits3.takeSouls(sp);
        if (had <= 0) return;
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 c = V.up(sp.position(), 1.2);
        double P = JobSystem.power(sp, j);
        int n = Math.max(1, (int) trait(j, "wisps", 6));
        double each = trait(j, "wispHit", 0.6);
        List<LivingEntity> foes = new ArrayList<>(Kit.around(sp, sp.position(), trait(j, "wispRange", 8), 4));
        foes.removeIf(e -> !e.isAlive() || e.isRemoved());
        foes.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
        if (foes.size() > n) foes = new ArrayList<>(foes.subList(0, n));
        // 터짐: 몸에서 망령이 터져 나옴 · 비명 · 푸른 불 고리 · 회복
        JobFx.wail(level, V.up(c, 0.5), 1.6, j, 0.45, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
        JobFx.burst(level, c, 1.6, SOUL, SOUL_CORE, 0.35, Opt.of("vel", RuneKit.bodyVel(sp)));
        JobFx.halo(level, V.up(sp.position(), 0.08), 3.0, j, 0.5, Opt.empty());
        SkillFx.play(level, "particle.soul_escape", sp.position(), 0.7, 1.2);
        SkillFx.play(level, "entity.vex.charge", sp.position(), 0.7, 1.0);
        double healed = RpgHealth.healBy(sp, sp.getMaxHealth() * trait(j, "heal", 0.1));
        if (healed >= 0.05) DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
        Notify.notify(sp, "§b§l영혼 해방 §7— 망령 " + n, 30);
        if (foes.isEmpty()) {
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n;
                JobFx.soulFly(level, c, V.add(c, new Vec3(Math.cos(a), 0.6, Math.sin(a)), 3.0), 10, 0.3, j, Opt.empty());
            }
            return;
        }
        int[] got = new int[foes.size()];
        for (int i = 0; i < n; i++) got[i % foes.size()]++;
        for (int q = 0; q < foes.size(); q++) {
            LivingEntity e = foes.get(q);
            int k = got[q];
            int at = 0;
            for (int w = 0; w < k; w++) {
                double a = (q * 2.4 + w * 1.3);
                Vec3 from = V.add(c, new Vec3(Math.cos(a) * 0.8, 0.5 + 0.2 * w, Math.sin(a) * 0.8), 1.0);
                at = Math.max(at, JobFx.soulFly(level, from, Kit.bodyPoint(e), 12, 0.32, j, Opt.empty()));
            }
            double mult = each * k;
            JobKit.hitLater(sp, e, life, JobKit.now() + at, () -> {
                inner = true;
                try {
                    if (JobKit.hit(sp, e, j, P, mult, true, false) > 0 && e.level() instanceof ServerLevel el) {
                        JobFx.soul(el, Kit.bodyPoint(e), 0.4 + 0.05 * k, j, 0.4, Opt.of("vel", RuneKit.entVel(e)));
                        JobFx.burst(el, Kit.bodyPoint(e), 0.7 + Kit.bodyPad(e) * 0.4, SOUL, SOUL_CORE, 0.28, Opt.of("vel", RuneKit.entVel(e)));
                        SkillFx.play(el, "entity.vex.hurt", e.position(), 0.9, 0.8);
                    }
                } finally {
                    inner = false;
                }
            });
        }
    }

    // ------------------------------------------------------------ 소울 리퍼 바운티풀 하베스트 · 랜턴 오브 소울즈

    /** 바운티풀 하베스트: ticks 틱 동안 흡혈 × leech · 이동 속도 − slow (몫) */
    static void harvestOn(ServerPlayer sp, int ticks, double slow, double leech) {
        State st = of(sp);
        st.harvestUntil = Tasks.now() + ticks;
        st.harvestLeech = Math.max(1, leech);
        slowMod(sp, slow);
    }

    /** 흡혈 배율 (JobTraits3.leech — 바운티풀 하베스트 동안 leech · 아니면 1) */
    static double leechMult(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.harvestUntil > Tasks.now() ? st.harvestLeech : 1;
    }

    /** 시험: 바운티풀 하베스트 중인지 */
    public static boolean harvesting(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.harvestUntil > Tasks.now();
    }

    private static void slowMod(ServerPlayer sp, double amount) {
        AttributeInstance a = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        if (amount > 0) a.addOrUpdateTransientModifier(new AttributeModifier(HARVEST_SLOW, -Math.min(0.9, amount), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else a.removeModifier(HARVEST_SLOW);
    }

    /** 랜턴 오브 소울즈: ticks 틱 동안 등불이 뜸 (태세 줄) */
    static void lanternOn(ServerPlayer sp, int ticks) {
        State st = of(sp);
        st.lanternAt = Tasks.now();
        st.lanternUntil = Tasks.now() + ticks;
        JobTraits.sendGauge(sp, true);
    }

    /** 등불이 떠 있는지 (직업을 바꾸거나 죽으면 꺼짐 — 시험도 씀) */
    public static boolean lanternUp(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.lanternUntil > Tasks.now();
    }

    /** 랜턴 오브 소울즈의 남은 시간 (태세 줄 — {이름, 남은 틱, 전체 틱} · 없으면 null) */
    static Object[] stanceLine(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !SR.equals(j.id())) return null;
        State st = STATES.get(sp.getUUID());
        long now = Tasks.now();
        if (st == null || st.lanternUntil <= now) return null;
        Jobs.Skill k = j.skill("soulreaper_lantern");
        return new Object[] {k == null ? "랜턴 오브 소울즈" : k.name(), (int) (st.lanternUntil - now), (int) Math.max(1, st.lanternUntil - st.lanternAt)};
    }

    /** 적 자리 c 의 건너편 (toward 쪽 dist 칸) 에 설 자리 — 막혔으면 ±35° · ±70° (없으면 null): 엄브럴 트레스패스 · 섀도 스토크 · 비포 선셋 */
    static Vec3 beyond(ServerPlayer sp, Vec3 c, Vec3 toward, double dist) {
        ServerLevel level = sp.level();
        for (double turn : new double[] {0, 35, -35, 70, -70}) {
            Vec3 want = V.add(c, V.turnY(toward, turn), dist);
            Vec3 s = Kit.standSpot(level, new Vec3(want.x, Math.max(want.y, c.y) + 0.6, want.z), 2.5);
            if (s == null || Math.abs(s.y - c.y) > 1.6) continue;
            if (!level.noCollision(sp, sp.getBoundingBox().move(s.x - sp.getX(), s.y - sp.getY(), s.z - sp.getZ()))) continue;
            return s;
        }
        return null;
    }

    // ------------------------------------------------------------ 엑시큐셔너 처형

    /** 처형: 남은 체력만큼 (큰 숫자) · 위에서 떨어지는 금빛 단두대 빛 · 해골 · 원 라스트 데이면 이동 · 재사용 대기 */
    private static void execute(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        if (!(e.level() instanceof ServerLevel level)) return;
        Vec3 b = Kit.bodyPoint(e);
        double h = e.getBbHeight();
        inner = true;
        try {
            // 방금 맞은 무적 시간 (바닐라는 그 사이의 더 큰 피해를 차이만큼만 넣음 — 남은 체력이 다 깎이지 않았음) 을 지우고, 마법 (방어구 무시) 으로 남은 체력 (흡수 포함)
            // 보다 넉넉히 — 숫자는 실제로 깎인 양 (LivingEntityMixin)
            e.setInvulnerableTime(0);
            Kit.applyJobDamage(sp, e, (e.getHealth() + e.getAbsorptionAmount()) * 1.25 + 2, true, Kit.Cause.MAGIC, DamageNumbers.Style.JOB_BIG, JobKit.grad(j));
        } finally {
            inner = false;
        }
        JobFx.line(level, V.up(b, h + 2.4), V.up(b, -0.6), DUSK, DUSK_CORE, 0.3, Opt.empty());
        JobFx.cross(level, b, 1.0 + Kit.bodyPad(e) * 0.5, j, 0.35, Opt.empty());
        JobFx.skull(level, V.up(b, h * 0.6 + 0.6), 0.55 + Kit.bodyPad(e) * 0.3, 4, 2, 0.4, false, Opt.empty(), j);
        SkillFx.sparks(level, b, DUSK, 12, 5.0);
        SkillFx.play(level, "item.trident.hit", b, 0.6, 1.0);
        SkillFx.play(level, "entity.wither.break_block", b, 0.8, 0.5);
        Notify.notify(sp, "§6§l처형", 20);
        if (JobTraits.stanceOn(sp, LASTDAY)) {
            Jobs.Skill k = j.skill(LASTDAY);
            State st = of(sp);
            st.hasteUntil = Tasks.now() + (long) (k == null ? 60 : k.num("speedFor", 60));
            JobTraits3.hasteMod(sp, k == null ? 0.2 : k.num("speed", 0.2));
            RuneKit.later(1, () -> JobSkills.reduceCooldowns(sp, (int) (k == null ? 40 : k.num("cdr", 40)), LASTDAY)); // 다른 직업 스킬만
            JobFx.speed(sp, j, 0.4, 0.9);
        }
    }

    /**
     * 직업 스킬로 적이 쓰러짐 (ExecutionerSkills — 피해가 들어간 뒤 그 적이 쓰러졌으면): 그 스킬의 재사용 대기를 1틱 뒤 지움 (스킬을 쓴 틱에 쓰러지면 재사용 대기가
     * 그 뒤에 걸리므로). 궁극기는 다시 쓰기 (ExecutionerSkills) 로 따로
     */
    static void killedBy(ServerPlayer sp, Jobs.Skill k) {
        if (k == null || k.slot() == 4 || k.slot() == 3) return;
        RuneKit.later(1, () -> {
            if (sp.isRemoved() || JobSkills.cooldownLeft(sp, k.id()) <= 0) return;
            JobSkills.clearCooldown(sp, k.id());
            SkillFx.play(sp.level(), "block.amethyst_block.chime", sp.position(), 1.3, 0.8);
            JobSystem.note(sp, "§6처형 §7— " + k.name() + " 다시");
        });
    }

    /** 컨뎀 선고 표식 (ticks 틱) */
    static void condemn(ServerPlayer sp, LivingEntity e, int ticks) {
        of(sp).condemned.put(e.getUUID(), Tasks.now() + ticks);
    }

    /** 선고 표식이 남았는지 (시험 · 그림) */
    public static boolean condemned(ServerPlayer sp, Entity e) {
        State st = STATES.get(sp.getUUID());
        Long until = st == null || e == null ? null : st.condemned.get(e.getUUID());
        return until != null && until > Tasks.now();
    }

    /** 섀도 스토크가 맞음: ticks 틱 안의 다음 직업 스킬 + next */
    static void stalkReady(ServerPlayer sp, int ticks) {
        State st = of(sp);
        st.stalkUntil = Tasks.now() + ticks;
        st.stalkSkill = null;
    }

    /** 엑시큐셔너 직업 스킬을 씀 (ExecutionerSkills — 섀도 스토크 말고): 섀도 스토크의 덤이 남았으면 이 스킬의 피해에 (그 스킬이 끝날 때까지 — t + 10틱) */
    static void beginSkill(ServerPlayer sp, Jobs.Skill k) {
        State st = STATES.get(sp.getUUID());
        if (st == null || k == null) return;
        long now = Tasks.now();
        if (st.stalkUntil > now) {
            st.stalkUntil = 0;
            st.stalkSkill = k.id();
            st.stalkSkillUntil = now + (long) k.num("t", 20) + 10;
            JobFx.speed(sp, JobSystem.job(sp), 0.3, 0.7);
        } else if (st.stalkSkillUntil <= now) st.stalkSkill = null;
    }

    /** 섀도 스토크의 덤이 기다리는지 (시험) */
    public static boolean stalkPending(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.stalkUntil > Tasks.now();
    }

    /** 낫 평타 (엑시큐셔너): 게이지 = 그 적의 잃은 체력 */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        Jobs.Job j = jobIf(sp, EX);
        if (j == null || w == null || victim == null || !"scythe".equals(w.type())) return;
        RuneKit.later(1, () -> {
            if (!sp.isRemoved()) track(sp, victim, j);
        });
    }

    private static void track(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        State st = of(sp);
        st.target = e.isAlive() && !e.isRemoved() ? e.getUUID() : null;
        st.missing = st.target == null ? 0 : missingTenths(e);
        JobTraits.sendGauge(sp, false);
    }

    private static int missingTenths(LivingEntity e) {
        double max = e.getMaxHealth();
        return max <= 0 ? 0 : (int) Math.max(0, Math.min(10, Math.floor((1 - e.getHealth() / max) * 10 + 1e-6)));
    }

    /** 게이지: 엑시큐셔너 = 마지막으로 친 적의 잃은 체력 (한 칸 10% — 쓰러지면 0) */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        State st = STATES.get(sp.getUUID());
        if (st == null || st.target == null) return 0;
        Entity e = sp.level().getEntity(st.target);
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isRemoved()) return 0;
        return missingTenths(le);
    }

    /** 시험: 게이지 값 */
    public static int gaugeOf(ServerPlayer sp) {
        Jobs.Job j = JobSystem.job(sp);
        return j == null ? 0 : gaugeValue(sp, j);
    }

    /**
     * 4틱마다 (JobTraits.tick): 엑시큐셔너 — 원 라스트 데이 동안 처형할 수 있는 둘레 16칸 적 머리 위 해골 (8틱마다) · 선고 표식 해골 (8틱마다) · 처형 뒤 이동 속도가 끝남
     */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        State st = STATES.get(sp.getUUID());
        if (SR.equals(j.id())) {
            // 바운티풀 하베스트가 끝남 (낫을 뽑음): 느려짐을 풂
            if (st != null && st.harvestUntil > 0 && st.harvestUntil <= now) {
                st.harvestUntil = 0;
                st.harvestLeech = 1;
                slowMod(sp, 0);
            }
            return;
        }
        if (!EX.equals(j.id())) return;
        if (st != null && st.hasteUntil > 0 && st.hasteUntil <= now) {
            st.hasteUntil = 0;
            JobTraits3.hasteMod(sp, 0);
        }
        if (now % 8 != 0) return;
        ServerLevel level = sp.level();
        if (st != null && !st.condemned.isEmpty()) {
            st.condemned.values().removeIf(u -> u <= now);
            for (UUID id : st.condemned.keySet()) {
                Entity e = level.getEntity(id);
                if (e instanceof LivingEntity le && le.isAlive() && !le.isRemoved()) {
                    JobFx.skull(level, V.up(le.position(), le.getBbHeight() + 0.75), 0.42, 1, 3, 0.45, true, Opt.of("vel", RuneKit.entVel(le), "alpha", 0.9), j);
                }
            }
        }
        // 섀도 스토크의 덤이 기다림: 낫 끝의 보랏빛 (다음 직업 스킬 + next)
        if (stalkPending(sp)) {
            JobFx.glint(level, V.up(RuneKit.handOf(sp).hand(), 0.35), 0.4, VIOLET, DUSK_CORE, 0.4, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
        }
        if (JobTraits.stanceOn(sp, LASTDAY)) {
            // 해 질 녘: 발밑의 금빛 고리 · 낫 끝 금빛 (3인칭 — 1인칭에서는 숨김)
            JobFx.halo(level, V.up(sp.position(), 0.06), 1.1, j, 0.42, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.55));
            JobFx.glint(level, V.up(RuneKit.handOf(sp).hand(), 0.35), 0.35, DUSK, DUSK_CORE, 0.4, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
            double th = threshold(sp, j);
            for (LivingEntity e : Kit.around(sp, sp.position(), 16, 6)) {
                if (!e.isAlive() || e.isRemoved() || JobKit.isBoss(e) || e.getHealth() > e.getMaxHealth() * th) continue;
                JobFx.skull(level, V.up(e.position(), e.getBbHeight() + 1.1), 0.5, 1, 3, 0.45, true, Opt.of("vel", RuneKit.entVel(e), "alpha", 1.0), j);
            }
        }
    }
}
