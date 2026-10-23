package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Combat;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Fx;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.WeaponTraits;
import mmorpg.fabric.mob.Monsters;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 0.29.0 암살자 · 창술사 · 사신 특성 (JobTraits 가 부름 — 자원 · 태세는 JobTraits 와 같은 틀):
 * <ul>
 * <li>암살자 「표식」: 단검 평타가 맞을 때마다 그 적에 표식 +1 (최대 max · markFor 틱 — 머리 위 꽃), 적의 등 뒤 (backArc°) 에서 단검으로 치면 치명타 + back (최대
 * RUNE_STRIKE.critCap — RpgCombat.addCritBonusHook). 암영 (태세): 투명 · 몹이 노리지 않음, 첫 공격 (단검 평타 · 직업 스킬의 첫 타 하나) 치명타 확정 · 피해 × first,
 * 공격하면 풀림 → 풀린 뒤 speedFor 틱 이동 + speed</li>
 * <li>창술사 「창끝」: 창 평타가 사거리 끝 edge 칸 안에서 맞으면 피해 + edgeBonus (RpgCombat.meleeMult) · 조금 밀쳐냄 (push), 앞으로 움직이며 창 평타를 맞히면
 * momentumFor 틱 안의 다음 직업 스킬 위력 + momentum (JobSystem). 창벽 (태세): 정면 front° 에서 온 피해 − guard (RpgHealth.sourceReduce)</li>
 * <li>사신 「혼」 (0 ~ max): 낫 평타 (넓게 베기에 맞은 적 포함) · 직업 스킬이 맞힌 적마다 +1, 처치하면 + kill. 하나당 이동 속도 + speedPer · 준 피해의 leechPer 만큼 회복
 * (DamageNumbers.dealtHook). 혼백 폭발이 씀. 망령 걸음 동안 받는 피해 없음, 사신 현신 (태세) 동안 넓게 베기 범위 · 비율 (WeaponTraits.sweepMod)</li>
 * </ul>
 */
public final class JobTraits3 {
    private JobTraits3() {}

    static final class State {
        // 암살자
        /** 게이지: 마지막으로 표식을 찍었거나 단검으로 친 적 */
        UUID target;
        /** 은신이 끝나는 틱 (0 = 없음 · 지난 틱이면 끝나는 중) */
        long veilUntil;
        /** 암영 중 쓴 직업 스킬의 첫 타가 강화되는 마지막 틱 (0 = 없음) */
        long firstUntil;
        /** 이 틱의 단검 평타가 암영의 첫 공격 (치명타 확정 — RpgCombat.sureCrit) */
        long veilHit = -1;
        /** 풀린 뒤 이동 + 가 끝나는 틱 */
        long speedUntil;
        // 창술사
        long momentumUntil;
        /** 이번 평타가 끝자락 (밀침 · 연출 — 맞힘 훅) */
        long edgeTick = -1;
        UUID edgeVictim;
        // 사신
        int souls;
        double leechAcc;
        long leechShown;
        long ghostUntil;
        /** 사신 현신 그림자가 휘두르는 중 (이 틱까지 — 서 있는 그림을 다시 그리지 않음) */
        long avatarSwingUntil;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** 표식: "공격자|적" → {수, 끝나는 틱} */
    private static final Map<String, long[]> MARKS = new HashMap<>();
    /** 표식이 찍힌 적 (머리 위 꽃을 다시 그림): 적 UUID → 적 */
    private static final Map<UUID, LivingEntity> MARKED = new HashMap<>();
    /** 이동 속도 수정치 (암영이 풀린 뒤 · 혼) */
    private static final Identifier VEIL_SPEED = Mmorpg.id("job_veil_speed");
    private static final Identifier SOUL_SPEED = Mmorpg.id("job_soul_speed");

    static void init() {
        // 치명타 확정: 앞의 것 (검사 섬보) 이 아니면 암영의 첫 단검 평타
        java.util.function.BiPredicate<ServerPlayer, Weapon> sc = RpgCombat.sureCrit;
        RpgCombat.sureCrit = (p, w) -> sc.test(p, w) || veilCrit(p, w);
        RpgCombat.MeleeMult mm = RpgCombat.meleeMult;
        RpgCombat.meleeMult = (p, w, v) -> mm.mult(p, w, v) * meleeMult(p, w, v);
        RpgCombat.addCritBonusHook(JobTraits3::backCrit);
        RpgCombat.addWeaponHitHook(JobTraits3::onWeaponHit);
        java.util.function.ToDoubleBiFunction<ServerPlayer, DamageSource> sr = RpgHealth.sourceReduce;
        RpgHealth.sourceReduce = (p, src) -> Math.max(sr.applyAsDouble(p, src), sourceReduce(p, src));
        WeaponTraits.SweepMod sm = WeaponTraits.sweepMod;
        WeaponTraits.sweepMod = (p, w) -> {
            double[] m = sweepMod(p, w);
            return m != null ? m : sm.mod(p, w);
        };
        java.util.function.BiConsumer<ServerPlayer, LivingEntity> sw = WeaponTraits.swept;
        WeaponTraits.swept = (p, e) -> {
            sw.accept(p, e);
            onSwept(p, e);
        };
        DamageNumbers.DealtHook dh = DamageNumbers.dealtHook;
        DamageNumbers.dealtHook = (a, v, d) -> {
            dh.dealt(a, v, d);
            leech(a, v, d);
        };
        ServerLivingEntityEvents.AFTER_DEATH.register(JobTraits3::onDeath);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            MARKS.clear();
            MARKED.clear();
        });
    }

    static State of(ServerPlayer sp) {
        return STATES.computeIfAbsent(sp.getUUID(), k -> new State());
    }

    /** 직업을 바꿈 · 시험: 은신 · 이동 수정치 · 표식 · 혼을 지움 */
    static void reset(ServerPlayer sp) {
        State st = STATES.remove(sp.getUUID());
        if (st != null && st.veilUntil > 0) removeInvisibility(sp);
        speedMod(sp, VEIL_SPEED, 0);
        speedMod(sp, SOUL_SPEED, 0);
        String p = sp.getUUID() + "|";
        MARKS.keySet().removeIf(k -> k.startsWith(p));
    }

    static void forget(UUID id) {
        STATES.remove(id);
        String p = id + "|";
        MARKS.keySet().removeIf(k -> k.startsWith(p));
    }

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st != null) {
            st.veilUntil = 0;
            st.firstUntil = 0;
            st.momentumUntil = 0;
            st.souls = 0;
            st.ghostUntil = 0;
            st.speedUntil = 0;
            st.leechAcc = 0;
        }
        String p = id + "|";
        MARKS.keySet().removeIf(k -> k.startsWith(p));
    }

    private static Jobs.Job jobIf(ServerPlayer sp, String id) {
        Jobs.Job j = JobSystem.job(sp);
        return j != null && j.id().equals(id) ? j : null;
    }

    private static double trait(Jobs.Job j, String key, double fallback) {
        return j == null || j.trait() == null ? fallback : j.trait().def().numOr(key, fallback);
    }

    private static Jobs.Skill skill(Jobs.Job j, String id) {
        return j == null ? null : j.skill(id);
    }

    /** 이동 속도 수정치 (amount = 비율 — 0 이면 뗌) */
    private static void speedMod(ServerPlayer sp, Identifier id, double amount) {
        AttributeInstance a = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        if (amount > 0) a.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        else a.removeModifier(id);
    }

    /** 걸린 이동 속도 수정치 (없으면 0 — 시험) */
    public static double speedBonus(ServerPlayer sp, boolean souls) {
        AttributeInstance a = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeModifier m = a == null ? null : a.getModifier(souls ? SOUL_SPEED : VEIL_SPEED);
        return m == null ? 0 : m.amount();
    }

    // ------------------------------------------------------------ 암살자 「표식」

    private static String key(Entity attacker, Entity victim) {
        return attacker.getUUID() + "|" + victim.getUUID();
    }

    /** 이 적의 표식 수 (끝났으면 0) */
    public static int marks(ServerPlayer sp, Entity e) {
        if (e == null) return 0;
        long[] m = MARKS.get(key(sp, e));
        return m == null || m[1] < Tasks.now() ? 0 : (int) m[0];
    }

    /** 시험용: 표식을 정함 */
    public static void setMarks(ServerPlayer sp, LivingEntity e, int n) {
        MARKS.put(key(sp, e), new long[] {Math.max(0, n), Tasks.now() + 200});
        MARKED.put(e.getUUID(), e);
        of(sp).target = e.getUUID();
        JobTraits.sendGauge(sp, false);
    }

    /** 표식 하나 더 (최대 trait.max · markFor 틱) — 머리 위에 꽃이 하나 더 핌 */
    static void mark(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        if (e == null || e.isRemoved()) return;
        long now = Tasks.now();
        long[] m = MARKS.computeIfAbsent(key(sp, e), k -> new long[2]);
        if (m[1] < now) m[0] = 0;
        int max = j.trait() == null ? 5 : Math.max(1, j.trait().max());
        m[0] = Math.min(max, m[0] + 1);
        m[1] = now + (long) trait(j, "markFor", 200);
        MARKED.put(e.getUUID(), e);
        of(sp).target = e.getUUID();
        if (e.level() instanceof ServerLevel level && RuneKit.fxQuota("jobAsMark", 6)) {
            markFx(level, sp, e, j, (int) m[0], true);
            if (m[0] == max) SkillFx.play(level, "block.amethyst_block.chime", Kit.bodyPoint(e), 1.7, 0.6);
        }
        JobTraits.sendGauge(sp, false);
    }

    /** 머리 위 꽃 n 송이 (한 줄 — 새로 핀 것 (fresh) 은 크게 피어남) */
    private static void markFx(ServerLevel level, ServerPlayer sp, LivingEntity e, Jobs.Job j, int n, boolean fresh) {
        Vec3 over = RuneKit.overHead(e, 0.5);
        Vec3 right = V.rightOf(V.flat(V.sub(over, sp.getEyePosition())));
        for (int i = 0; i < n; i++) {
            Vec3 at = V.add(over, right, (i - (n - 1) / 2.0) * 0.32);
            boolean last = fresh && i == n - 1;
            JobFx.blossom(level, at, last ? 0.2 : 0.15, j, fresh ? 0.9 : 0.85, Opt.of("vel", RuneKit.entVel(e), "grow", last ? -0.25 : 0.0, "fade", 0.35, "alpha",
                    last ? 1.0 : 0.85));
        }
    }

    /** 표식을 모두 떼어 냄 (낙화) — 뗀 수 */
    static int takeMarks(ServerPlayer sp, Entity e) {
        long[] m = MARKS.remove(key(sp, e));
        int n = m == null || m[1] < Tasks.now() ? 0 : (int) m[0];
        JobTraits.sendGauge(sp, false);
        return n;
    }

    /** 머리 위 꽃 다시 그리기 (서버 틱마다 한 번 — JobTraits): 끝난 표식을 지우고 16틱마다 */
    static void tickMarks(long now) {
        if (MARKS.isEmpty()) {
            MARKED.clear();
            return;
        }
        if (now % 16 != 0) return;
        MARKS.values().removeIf(m -> m[1] < now);
        MARKED.values().removeIf(e -> e.isRemoved() || !e.isAlive());
        Jobs.Job as = Jobs.job("assassin");
        if (as == null) return;
        for (Map.Entry<String, long[]> en : MARKS.entrySet()) {
            int bar = en.getKey().indexOf('|');
            if (bar < 0) continue;
            LivingEntity e = MARKED.get(uuidOr(en.getKey().substring(bar + 1)));
            if (e == null || !(e.level() instanceof ServerLevel level) || !RuneKit.fxQuota("jobAsMarks", 12)) continue;
            ServerPlayer sp = RuneKit.player(level, uuidOr(en.getKey().substring(0, bar)));
            if (sp == null) continue;
            markFx(level, sp, e, as, (int) en.getValue()[0], false);
        }
    }

    private static UUID uuidOr(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return new UUID(0, 0);
        }
    }

    /** 적의 등 뒤 (몸이 향한 쪽의 반대 arc° 안) 에서 치는지 */
    public static boolean behind(Entity attacker, LivingEntity victim, double arc) {
        double yaw = Math.toRadians(victim.yBodyRot);
        Vec3 face = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 to = new Vec3(attacker.getX() - victim.getX(), 0, attacker.getZ() - victim.getZ());
        double len = V.len(to);
        if (len < 1e-4) return false;
        double cos = -V.dot(face, to) / len; // 등 쪽 (−face) 과 공격자 쪽의 각
        return cos >= Math.cos(Math.toRadians(arc / 2));
    }

    /** 치명타 +: 암살자 단검이 적의 등 뒤에서 (victim 이 없으면 0 — 표시용) */
    private static double backCrit(ServerPlayer sp, Weapon w, LivingEntity victim) {
        if (victim == null || w == null || !"dagger".equals(w.type())) return 0;
        Jobs.Job j = jobIf(sp, "assassin");
        if (j == null || !behind(sp, victim, trait(j, "backArc", 120))) return 0;
        return trait(j, "back", 0.25);
    }

    // ------------------------------------------------------------ 암영 (은신)

    /** 은신 중인지 */
    public static boolean veilOn(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.veilUntil > Tasks.now();
    }

    /** 암영 시작 (AssassinSkills): dur 틱 투명 · 몹이 노리던 것을 놓음 · 태세 (남은 시간) */
    static void veilStart(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        State st = of(sp);
        long now = Tasks.now();
        int dur = (int) k.num("dur", 120);
        st.veilUntil = now + dur;
        st.firstUntil = 0;
        st.veilHit = -1;
        JobTraits.startStance(sp, k, dur);
        sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, dur, 0, false, false, true));
        Monsters.forgetMonsterFights(sp.getUUID());
        for (Mob m : sp.level().getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(32), x -> x.getTarget() == sp)) m.setTarget(null);
    }

    private static void removeInvisibility(ServerPlayer sp) {
        MobEffectInstance e = sp.getEffect(MobEffects.INVISIBILITY);
        // 암영이 건 짧은 투명만 (물약의 긴 투명은 그대로)
        if (e != null && !e.isInfiniteDuration() && e.getDuration() <= 200) sp.removeEffect(MobEffects.INVISIBILITY);
    }

    /** 암영이 풀림 (공격 · 직업 스킬 · 시간이 다 됨): 투명을 없애고 speedFor 틱 이동 + speed, 연기 · 소리 */
    static void veilBreak(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        if (st == null || st.veilUntil <= 0) return;
        st.veilUntil = 0;
        removeInvisibility(sp);
        Jobs.Job j = jobIf(sp, "assassin");
        Jobs.Skill k = skill(j, "assassin_veil");
        if (j != null && k != null) {
            st.speedUntil = Tasks.now() + (long) k.num("speedFor", 60);
            speedMod(sp, VEIL_SPEED, k.num("speed", 0.3));
            ServerLevel level = sp.level();
            Vec3 c = V.up(sp.position(), 1.0);
            JobFx.smoke(level, c, 0.9, j, 0.55, JobFx.hide1p(sp));
            JobFx.smoke(level, V.up(c, -0.6), 1.1, j, 0.6, JobFx.hide1p(sp).with("alpha", 0.8));
            JobFx.petals(level, c, 5, 2.0, 0.12, j, 0.6, Opt.empty());
            SkillFx.play(level, "mob.phantom.flap", c, 1.4, 0.6);
        }
        JobTraits.stopStance(sp, "assassin_veil");
    }

    /** 단검 평타의 배율: 암영의 첫 공격 × first (은신이 풀림 — 치명타 확정은 이 틱의 sureCrit) */
    private static double veilBasic(ServerPlayer sp, Weapon w) {
        if (w == null || !"dagger".equals(w.type()) || !veilOn(sp)) return 1;
        Jobs.Job j = jobIf(sp, "assassin");
        Jobs.Skill k = skill(j, "assassin_veil");
        if (k == null) return 1;
        State st = of(sp);
        st.veilHit = Tasks.now();
        veilBreak(sp);
        DamageNumbers.showFace(sp, "§d§l암습", 2.2, 0, 0.1);
        return JobUpgrades.num(sp, k, "first", 2.5); // 0.31.0 강화 (1 을 넘는 몫)
    }

    /** 이번 평타가 암영의 첫 단검 평타인지 (쓰면 지움) */
    private static boolean veilCrit(ServerPlayer sp, Weapon w) {
        State st = STATES.get(sp.getUUID());
        if (st == null || st.veilHit != Tasks.now() || w == null || !"dagger".equals(w.type())) return false;
        st.veilHit = -1;
        return true;
    }

    /** 직업 스킬을 쓰기 직전 (JobSystem): 암영 중이면 그 스킬의 첫 타가 강화됨 (60틱 안) */
    static void beforeCast(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        if (j == null || !j.id().equals("assassin") || k.id().equals("assassin_veil") || !veilOn(sp)) return;
        of(sp).firstUntil = Tasks.now() + 60;
    }

    /** 직업 스킬을 쓴 뒤: 나갔으면 은신이 풀림, 안 나갔으면 첫 타 강화를 거둠 */
    static void afterCast(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, boolean ok) {
        if (j == null || !j.id().equals("assassin") || k.id().equals("assassin_veil")) return;
        State st = STATES.get(sp.getUUID());
        if (st == null || st.veilUntil <= 0) return;
        if (ok) veilBreak(sp);
        else st.firstUntil = 0;
    }

    /** 직업 스킬 피해 한 번의 배율 (JobKit): 암영 중 쓴 스킬의 첫 타면 first (쓰면 지움 — 안 들어가면 firstHitMissed 가 되돌림) */
    static double firstHit(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !j.id().equals("assassin")) return 1;
        State st = STATES.get(sp.getUUID());
        if (st == null || st.firstUntil < Tasks.now() || st.firstUntil == 0) return 1;
        st.firstUntil = -st.firstUntil; // 쓰는 중 (안 들어가면 되돌림)
        Jobs.Skill k = skill(j, "assassin_veil");
        return k == null ? 2.5 : JobUpgrades.num(sp, k, "first", 2.5); // 0.31.0 강화
    }

    static void firstHitMissed(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        if (st != null && st.firstUntil < 0) st.firstUntil = -st.firstUntil;
    }

    /** 첫 타가 들어감: 치명타 연출 · 글자 */
    static void firstHitLanded(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        State st = STATES.get(sp.getUUID());
        if (st != null) st.firstUntil = 0;
        Fx.crit(e);
        if (e.level() instanceof ServerLevel level) {
            JobFx.cross(level, Kit.bodyPoint(e), 0.9 + Kit.bodyPad(e) * 0.5, j, 0.35, Opt.empty());
            SkillFx.play(level, "entity.player.attack.crit", Kit.bodyPoint(e), 0.9, 1.0);
        }
        DamageNumbers.showFace(sp, "§d§l암습", 2.2, 0, 0.1);
    }

    // ------------------------------------------------------------ 창술사 「창끝」

    /** 창 평타가 사거리 끝 edge 칸 안 (눈에서 몸 상자의 가장 가까운 점까지 ≥ 사거리 − edge) */
    public static boolean edgeHit(ServerPlayer sp, LivingEntity victim, double edge) {
        Vec3 eye = sp.getEyePosition();
        AABB b = victim.getBoundingBox();
        double dx = Math.max(Math.max(b.minX - eye.x, 0), eye.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - eye.y, 0), eye.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - eye.z, 0), eye.z - b.maxZ);
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return d >= sp.entityInteractionRange() - edge - 1e-6;
    }

    /** 근접 평타 배율 (RpgCombat.meleeMult): 창술사 창 끝자락 + edgeBonus · 암살자 암영의 첫 단검 평타 × first */
    private static double meleeMult(ServerPlayer sp, Weapon w, LivingEntity victim) {
        if (w == null) return 1;
        Jobs.Job j = JobSystem.job(sp);
        if (j == null) return 1;
        if (j.id().equals("assassin")) return veilBasic(sp, w);
        if (j.id().equals("lancer") && "lance".equals(w.type()) && victim != null && edgeHit(sp, victim, trait(j, "edge", 1.0))) {
            State st = of(sp);
            st.edgeTick = Tasks.now();
            st.edgeVictim = victim.getUUID();
            return 1 + trait(j, "edgeBonus", 0.15);
        }
        return 1;
    }

    /** 다음 직업 스킬의 기세가 남았는지 */
    public static boolean momentum(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.momentumUntil > Tasks.now();
    }

    /** 시험용: 기세를 정함 */
    public static void setMomentum(ServerPlayer sp, boolean on) {
        Jobs.Job j = JobSystem.job(sp);
        of(sp).momentumUntil = on ? Tasks.now() + (long) trait(j, "momentumFor", 100) : 0;
        JobTraits.sendGauge(sp, false);
    }

    /** 이번 시전의 위력 배율 (JobSystem): 창술사 기세가 남았으면 1 + momentum */
    static double castMult(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !j.id().equals("lancer") || !momentum(sp)) return 1;
        return 1 + trait(j, "momentum", 0.1);
    }

    /** 기세를 씀 (직업 스킬이 나감) */
    static void castCommit(ServerPlayer sp, Jobs.Job j) {
        State st = STATES.get(sp.getUUID());
        if (st == null) return;
        st.momentumUntil = 0;
        JobTraits.sendGauge(sp, false);
    }

    // ------------------------------------------------------------ 받는 피해 (창벽 · 망령 걸음)

    /** RpgHealth.sourceReduce: 망령 걸음 동안 1 (받지 않음), 창벽 태세 동안 정면 front° 에서 온 피해 guard */
    private static double sourceReduce(ServerPlayer sp, DamageSource src) {
        State st = STATES.get(sp.getUUID());
        if (st != null && st.ghostUntil > Tasks.now() && jobIf(sp, "reaper") != null) return 1;
        if (!JobTraits.stanceOn(sp, "lancer_wall")) return 0;
        Jobs.Job j = jobIf(sp, "lancer");
        Jobs.Skill k = skill(j, "lancer_wall");
        if (k == null) return 0;
        Vec3 from = src.getSourcePosition();
        if (from == null && src.getEntity() != null) from = src.getEntity().position();
        if (from == null) return 0;
        Vec3 to = new Vec3(from.x - sp.getX(), 0, from.z - sp.getZ());
        double len = V.len(to);
        if (len < 0.05) return 0;
        Vec3 f = Kit.facingOf(sp);
        if (V.dot(f, to) / len < Math.cos(Math.toRadians(k.num("front", 120) / 2))) return 0;
        // 막음: 앞의 창끝 번쩍 (같은 틱에 한 번)
        if (RuneKit.fxQuota("jobWallGuard" + sp.getId(), 1)) {
            ServerLevel level = sp.level();
            Vec3 at = V.up(V.add(sp.position(), f, 0.9), 1.2);
            JobFx.stab(level, at, 0.55, j, 0.25, JobFx.hide1p(sp));
            SkillFx.sparks(level, at, JobKit.glow(j), 6, 3);
            SkillFx.play(level, "item.shield.block", at, 1.3, 0.6);
        }
        return k.num("guard", 0.3);
    }

    /** 망령 걸음: ticks 틱 동안 받는 피해 없음 */
    static void ghostFor(ServerPlayer sp, int ticks) {
        of(sp).ghostUntil = Tasks.now() + ticks;
    }

    /** 망령 걸음 중인지 */
    public static boolean ghost(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.ghostUntil > Tasks.now();
    }

    // ------------------------------------------------------------ 사신 「혼」

    /** 지금 혼 */
    public static int souls(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.souls;
    }

    /** 시험용: 혼을 정함 */
    public static void setSouls(ServerPlayer sp, int n) {
        Jobs.Job j = JobSystem.job(sp);
        State st = of(sp);
        st.souls = Math.max(0, Math.min(j == null || j.trait() == null ? 10 : j.trait().max(), n));
        soulSpeed(sp, j, st);
        JobTraits.sendGauge(sp, false);
    }

    private static void soulSpeed(ServerPlayer sp, Jobs.Job j, State st) {
        speedMod(sp, SOUL_SPEED, j != null && j.id().equals("reaper") ? st.souls * trait(j, "speedPer", 0.01) : 0);
    }

    /** 혼 + n (최대 max): from 에서 혼불이 날아옴 (적이 없으면 둘레에서) */
    static void gainSouls(ServerPlayer sp, Jobs.Job j, int n, LivingEntity from) {
        if (j == null || !j.id().equals("reaper") || n <= 0) return;
        State st = of(sp);
        int max = j.trait() == null ? 10 : Math.max(1, j.trait().max());
        int before = st.souls;
        st.souls = Math.min(max, st.souls + n);
        if (st.souls != before) soulSpeed(sp, j, st);
        ServerLevel level = sp.level();
        if (from != null && RuneKit.fxQuota("jobSoulGain", 4)) {
            JobFx.soulFly(level, Kit.bodyPoint(from), V.up(sp.position(), 1.1), 9, 0.16, j, Opt.empty());
        }
        if (before < max && st.souls >= max) {
            JobFx.soul(level, V.up(sp.position(), 1.2), 0.45, j, 0.5, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
            JobFx.eyeRing(sp, j, 2.4, 0.7, 0.4);
            SkillFx.play(level, "particle.soul_escape", sp.position(), 0.8, 1.0);
            Notify.notify(sp, "§3§l혼 가득 §7— 이동 +" + Math.round(st.souls * trait(j, "speedPer", 0.01) * 100) + "% · 흡혈 "
                    + Math.round(st.souls * trait(j, "leechPer", 0.001) * 1000) / 10.0 + "%", 30);
        }
        JobTraits.sendGauge(sp, false);
    }

    /** 혼을 모두 씀 (혼백 폭발) — 쓴 수 */
    static int takeSouls(ServerPlayer sp) {
        State st = of(sp);
        int n = st.souls;
        st.souls = 0;
        soulSpeed(sp, JobSystem.job(sp), st);
        JobTraits.sendGauge(sp, false);
        return n;
    }

    /** 흡혈 (DamageNumbers.dealtHook): 사신이 준 피해 × 혼 × leechPer — 숫자는 모아서 (1초에 한 번) */
    private static void leech(ServerPlayer sp, LivingEntity victim, double dealt) {
        State st = STATES.get(sp.getUUID());
        if (st == null || st.souls <= 0 || !(dealt > 0)) return;
        Jobs.Job j = jobIf(sp, "reaper");
        if (j == null || !sp.isAlive() || sp.getHealth() <= 0) return;
        double got = RpgHealth.healBy(sp, dealt * st.souls * trait(j, "leechPer", 0.001));
        if (got > 0) st.leechAcc += got;
    }

    /** 처치 (AFTER_DEATH): 사신이 잡으면 혼 + kill */
    private static void onDeath(LivingEntity e, DamageSource src) {
        if (!(src.getEntity() instanceof ServerPlayer sp) || sp == e) return;
        Jobs.Job j = jobIf(sp, "reaper");
        if (j == null) return;
        gainSouls(sp, j, (int) trait(j, "kill", 3), e);
        if (e.level() instanceof ServerLevel level) {
            Vec3 c = Kit.bodyPoint(e);
            JobFx.soul(level, c, 0.35, j, 0.6, Opt.of("dir", V.UP, "speed", 1.5));
            SkillFx.play(level, "particle.soul_escape", c, 1.0, 1.0);
        }
    }

    /** 넓게 베기 (WeaponTraits.swept): 사신 낫 평타가 옆의 적에게도 들어감 → 혼 +1 */
    private static void onSwept(ServerPlayer sp, LivingEntity e) {
        Jobs.Job j = jobIf(sp, "reaper");
        if (j != null) gainSouls(sp, j, 1, e);
    }

    /** 넓게 베기 바꾸기 (사신 현신 — 낫 · 태세 동안 {range, ratio}) */
    private static double[] sweepMod(ServerPlayer sp, Weapon w) {
        if (w == null || !"scythe".equals(w.type()) || !JobTraits.stanceOn(sp, "reaper_avatar")) return null;
        Jobs.Skill k = skill(jobIf(sp, "reaper"), "reaper_avatar");
        return k == null ? null : new double[] {k.num("range", 5), JobUpgrades.num(sp, k, "ratio", 0.6)}; // 0.31.0 강화: 비율
    }

    // ------------------------------------------------------------ 무기로 맞힘

    /** 단검 → 표식 +1 · 등 뒤면 엇갈린 베기, 창 → 끝자락 밀침 · 기세, 낫 → 혼 +1 · 사신 현신의 그림자가 따라 휘두름 */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || w == null || victim == null || !Combat.isMeleeWeapon(w)) return;
        ServerLevel level = sp.level();
        long now = Tasks.now();
        switch (j.id()) {
            case "assassin" -> {
                if (!"dagger".equals(w.type())) return;
                mark(sp, victim, j);
                if (behind(sp, victim, trait(j, "backArc", 120)) && RuneKit.fxQuota("jobBackstab", 4)) {
                    JobFx.cross(level, Kit.bodyPoint(victim), 0.7 + Kit.bodyPad(victim) * 0.4, j, 0.28, Opt.empty());
                    SkillFx.play(level, "entity.player.attack.crit", Kit.bodyPoint(victim), 1.5, 0.6);
                }
            }
            case "lancer" -> {
                if (!"lance".equals(w.type())) return;
                State st = of(sp);
                Vec3 f = Kit.facingOf(sp);
                if (st.edgeTick >= now - 1 && victim.getUUID().equals(st.edgeVictim)) {
                    st.edgeTick = -1;
                    if (JobKit.pushable(victim)) RuneKit.knock(victim, V.sub(victim.position(), sp.position()), trait(j, "push", 0.3), 0.08);
                    Vec3 at = Kit.bodyToward(victim, sp.getEyePosition(), sp.getViewVector(1F));
                    JobFx.stab(level, V.add(at, sp.getViewVector(1F), -0.3), 0.55 + Kit.bodyPad(victim) * 0.3, j, 0.25, Opt.empty());
                    SkillFx.play(level, "item.trident.hit", at, 1.6, 0.6);
                }
                // 앞으로 움직이며 찌름 → 다음 직업 스킬 + momentum
                Vec3 mv = sp.getKnownMovement();
                if (mv.x * f.x + mv.z * f.z > 0.05) {
                    boolean was = st.momentumUntil > now;
                    st.momentumUntil = now + (long) trait(j, "momentumFor", 100);
                    if (!was) {
                        RuneKit.Hand h = RuneKit.handOf(sp);
                        SkillFx.fx(level, "rpg:arrow_spark", V.up(h.hand(), 0.2), Opt.of("dir", V.UP, "speed", 1.8, "size", 0.15, "grow", 0.8, "life", 0.35, "color",
                                JobKit.glow(j), "alpha", 1.0));
                        SkillFx.play(level, "block.amethyst_block.chime", sp.position(), 1.5, 0.6);
                    }
                    JobTraits.sendGauge(sp, false);
                }
            }
            case "reaper" -> {
                if (!"scythe".equals(w.type())) return;
                gainSouls(sp, j, 1, victim);
                if (JobTraits.stanceOn(sp, "reaper_avatar")) avatarSwing(sp, j);
            }
            default -> {}
        }
    }

    /** 사신 현신의 그림자 자리 (가운데 — 그림 아래 끝이 발밑): 몸 뒤 왼쪽 (3인칭 카메라와 몸 사이를 가리지 않게) */
    static Vec3 avatarAt(ServerPlayer sp) {
        Vec3 f = Kit.facingOf(sp);
        return V.up(V.add(V.add(sp.position(), f, -0.6), V.rightOf(f), -1.1), 2.0);
    }

    /** 사신 현신: 등 뒤 그림자가 낫을 휘두름 (3인칭 · 다른 사람) + 눈앞 큰 초승달 (1인칭도) — 같은 틱에 한 번 */
    private static void avatarSwing(ServerPlayer sp, Jobs.Job j) {
        if (!RuneKit.fxQuota("jobAvatarSwing" + sp.getId(), 1)) return;
        ServerLevel level = sp.level();
        // 휘두르는 그림이 서 있는 그림을 바꿈 (solo — 두 장이 겹치지 않게), 끝나기 한 틱 전에 서 있는 그림이 다시 바꿈 (그사이 다른 휘두름이면 그쪽이)
        of(sp).avatarSwingUntil = Tasks.now() + 6;
        JobFx.reaper(level, avatarAt(sp), 1.5, j, 1, 0.35, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.9, "solo", 1.0));
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(6, () -> {
            State st = STATES.get(sp.getUUID());
            if (!RuneKit.sameLife(sp, life) || !JobTraits.stanceOn(sp, "reaper_avatar") || st == null || st.avatarSwingUntil > Tasks.now()) return;
            avatarStand(sp, j);
        });
        JobFx.slash(sp, j, 2.6, 0.1, 2.3, Kit.rand() < 0.5 ? 10.0 : 190.0, 0.26);
        SkillFx.play(level, "entity.player.attack.sweep", sp.position(), 0.55, 0.8);
    }

    /**
     * 사신 현신을 걸 때: 땅에서 솟는 그림자 (자라며 올라옴) — 같은 solo 라 서 있는 그림이 이어 받음 (솟는 동안은 서 있는 그림을 다시 그리지 않고, 끝나기 전에
     * 서 있는 그림이 바꿈 — 겹친 두 장의 줄무늬 없이)
     */
    static void avatarRise(ServerPlayer sp, Jobs.Job j) {
        of(sp).avatarSwingUntil = Tasks.now() + 10;
        JobFx.reaper(sp.level(), V.up(avatarAt(sp), -0.6), 1.5, j, 0, 0.6, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "grow", 0.25, "alpha", 0.9, "solo",
                1.0));
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(10, () -> {
            State st = STATES.get(sp.getUUID());
            if (!RuneKit.sameLife(sp, life) || !JobTraits.stanceOn(sp, "reaper_avatar") || st == null || st.avatarSwingUntil > Tasks.now()) return;
            avatarStand(sp, j);
        });
    }

    /**
     * 사신 현신: 서 있는 그림자 (4장을 6 fps 로 되풀이 — 장면은 서버 시간으로 이어서, 다시 그려도 튀지 않음). 주인마다 하나만 (solo 1 — 다시 그릴 때 앞 그림이
     * 바로 사라짐: 겹친 두 장이 깊이 싸움으로 줄무늬가 지던 것, 사진 검토). 수명은 다시 그리는 간격 (8틱) 보다 길게 (늦게 와도 비지 않게)
     */
    private static void avatarStand(ServerPlayer sp, Jobs.Job j) {
        double phase = (Tasks.now() * 6 / 20.0) % 4;
        JobFx.reaper(sp.level(), avatarAt(sp), 1.5, j, 0, 0.6, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.75, "solo", 1.0, "phase", phase));
    }

    // ------------------------------------------------------------ 틱 (JobTraits 가 4틱마다) · 태세 연출 · 태세 끝 · 게이지

    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        State st = STATES.get(sp.getUUID());
        if (st == null) return;
        if (st.speedUntil > 0 && st.speedUntil <= now) {
            st.speedUntil = 0;
            speedMod(sp, VEIL_SPEED, 0);
        }
        if (j.id().equals("reaper")) {
            if (st.leechAcc >= 0.5 && now - st.leechShown >= 20) {
                DamageNumbers.show(sp, st.leechAcc, DamageNumbers.Style.HEAL, "", 0);
                st.leechAcc = 0;
                st.leechShown = now;
            }
            // 혼불이 몸 둘레를 돎 (3인칭 · 다른 사람 — 8틱마다)
            if (now % 8 == 0 && st.souls > 0 && !sp.isSpectator()) {
                ServerLevel level = sp.level();
                int n = Math.min(st.souls, 10);
                double a0 = now * 0.12;
                for (int i = 0; i < n; i++) {
                    double a = a0 + i * Math.PI * 2 / n;
                    Vec3 p = new Vec3(sp.getX() + Math.cos(a) * 0.85, sp.getY() + 0.9 + 0.25 * Math.sin(a * 2 + i), sp.getZ() + Math.sin(a) * 0.85);
                    JobFx.soul(level, p, 0.1, j, 0.42, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.85, "fade", 0.3));
                }
            }
        } else if (st.leechAcc > 0) st.leechAcc = 0;
    }

    /** 태세 연출 (JobTraits — 8틱마다): 그린 태세면 true */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        ServerLevel level = sp.level();
        if (stance.equals("assassin_veil")) {
            // 1인칭: 화면 네 귀퉁이의 옅은 그림자 연기 (다른 사람에게는 아무것도 — 은신)
            JobFx.cornerSmoke(sp, j, 0.55, 0.45, 0.45, RuneKit.bodyVel(sp));
            return true;
        }
        if (stance.equals("lancer_wall")) {
            // 앞의 창끝 셋 (가슴 높이 — 1인칭은 화면 아래 창끝 번쩍, 3인칭은 앞으로 뻗은 빛의 창)
            Vec3 f = Kit.facingOf(sp);
            Vec3 r = V.rightOf(f);
            for (int i = -1; i <= 1; i++) {
                Vec3 base = V.add(V.up(sp.position(), 1.1), r, i * 0.55);
                JobFx.spear(level, V.add(base, f, 1.9), V.unit(V.add(f, r, i * 0.25)), 0.75, 0, 0.42, j, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha",
                        0.8));
            }
            Vec3 look = sp.getViewVector(1F);
            for (int i = -1; i <= 1; i++) {
                Vec3 at = V.add(V.add(V.add(sp.getEyePosition(), look, 2.0), V.rightOf(V.flat(look)), i * 0.6), JobFx.screenUp(look), -0.55);
                JobFx.stab(level, at, 0.18, j, 0.4, JobFx.only1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.8, "spin", 0.0));
            }
            return true;
        }
        if (stance.equals("reaper_avatar")) {
            // 등 뒤의 사신 그림자 (서서 흔들림 — 1인칭에서는 숨김, 휘두르는 동안은 그대로) · 손의 혼불
            State st = STATES.get(sp.getUUID());
            if (st == null || st.avatarSwingUntil <= Tasks.now()) avatarStand(sp, j);
            RuneKit.Hand h = RuneKit.handOf(sp);
            JobFx.soul(level, V.up(h.hand(), 0.15), 0.12, j, 0.4, Opt.of("vel", RuneKit.bodyVel(sp), "alpha", 0.9));
            return true;
        }
        return false;
    }

    /** 태세가 끝남 (JobTraits): 암영 → 은신이 풀림 (이동 +) */
    static void stanceEnded(ServerPlayer sp, String id) {
        if (id.equals("assassin_veil")) veilBreak(sp);
        else if (id.equals("reaper_avatar")) {
            Jobs.Job j = jobIf(sp, "reaper");
            if (j != null) {
                JobFx.smoke(sp.level(), V.up(sp.position(), 1.4), 1.2, j, 0.6, JobFx.hide1p(sp));
                SkillFx.play(sp.level(), "particle.soul_escape", sp.position(), 0.7, 0.8);
            }
        } else if (id.equals("lancer_wall")) {
            sp.removeEffect(MobEffects.SLOWNESS);
        }
    }

    /** 게이지 값: 암살자 표식 (마지막 대상) · 창술사 기세 (0 · 1) · 사신 혼 */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        if (j.id().equals("paladin") || j.id().equals("priest") || j.id().equals("guardian")) return JobTraits4.gaugeValue(sp, j); // 0.30.0 심판 · 신앙 · 반격
        State st = STATES.get(sp.getUUID());
        if (st == null) return 0;
        return switch (j.id()) {
            case "assassin" -> {
                if (st.target == null) yield 0;
                Entity e = sp.level().getEntity(st.target);
                yield e == null || e.isRemoved() || !e.isAlive() ? 0 : marks(sp, e);
            }
            case "lancer" -> st.momentumUntil > Tasks.now() ? 1 : 0;
            case "reaper" -> st.souls;
            default -> 0;
        };
    }
}
