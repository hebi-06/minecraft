package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Combat;
import mmorpg.core.Jobs;
import mmorpg.core.SwingLooks;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.motion.SwingLook;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * 0.27.0 직업 특성 · 자원 · 태세 (서버): 검사 「검세」 (칼 평타가 맞으면 +1, 최대 5, 4초 못 맞히면 하나씩 사라짐), 마검사 「마력 각인」 (직업 스킬이 맞힌 적에 최대
 * 3 · 10초, 평타가 각인 찍힌 적을 치면 하나가 터짐 — 위력 × 1.0 마법), 태세 (명경지수 — 칼 평타 두 번째 베기 35% · 검세 유지 · 받는 피해 −15%, 마검 각성 — 평타마다
 * 마력 참격 잔상 위력 × 0.93 · 사거리 +1칸), 섬보 (다음 평타 치명타 확정). 칸 위 게이지는 {@link JobSkills.GaugePayload} (바뀔 때만).
 */
public final class JobTraits {
    private JobTraits() {}

    /** 한 사람의 자원 · 태세 */
    static final class State {
        /** 검세 */
        int stacks;
        long lastGain;
        long lastDecay;
        /** 태세 (스킬 id · 이름 · 끝 틱 · 전체 틱) */
        String stance = "";
        String stanceName = "";
        long stanceUntil;
        int stanceTotal;
        /** 섬보: 이 틱까지 다음 평타 치명타 확정 */
        long sureUntil;
        /** 마검사: 마지막으로 각인을 찍었거나 평타로 친 적 (게이지에 그 적의 각인 수) */
        UUID target;
        /** 마지막으로 보낸 게이지 (바뀔 때만 보냄) */
        String sent = "";
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** 마력 각인: "공격자|적" → {수, 끝나는 틱} */
    private static final Map<String, long[]> MARKS = new HashMap<>();
    /** 태세의 평타 사거리 +칸 (스킬 정의 reach — ENTITY_INTERACTION_RANGE) */
    private static final net.minecraft.resources.Identifier REACH_ID = Mmorpg.id("job_reach");
    /** 평타에 더한 몫의 구분 (WeaponBonus.parts — 들어간 뒤 HitHook 이 연출 · 각인 씀) */
    static final String KEY_MIRROR = "job_mirror";
    static final String KEY_MARK = "job_mark";
    /** 0.51.0 아케인 블레이드: 각인 다섯이 한꺼번에 터짐 (거대한 마력 대검) */
    static final String KEY_MARK_FULL = "job_mark_full";

    static void init() {
        RpgCombat.addScaledBonusHook(JobTraits::mirrorBonus);
        RpgCombat.addScaledBonusHook(JobTraits::markBonus);
        RpgCombat.addWeaponHitHook(JobTraits::onWeaponHit);
        RpgCombat.sureCrit = JobTraits::sureCrit;
        JobTraitsMore.init(); // 0.28.0 전사 · 매지션 · 궁수
        JobTraits3.init(); // 0.29.0 암살자 · 창술사 · 사신 (sureCrit 를 이어 받으므로 위의 sureCrit 다음)
        JobTraits4.init(); // 0.30.0 성기사 · 사제 · 수호기사
        JobTraits5.init(); // 0.51.0 검사 2차 (소드마스터 치명 · 용의 피 · 블러드 나이트 피의 갈증 · 광란)
        JobImpact.init(); // 0.51.0 타격감 (큰 타격 그림 · 멈칫)
        JobTraits6.init(); // 0.51.0 전사 2차 (워로드 곰의 화신 · 동면 · 거인의 무게 · 글래디에이터 결투 · 불사의 분노 · 홀름강)
        JobTraits7.init(); // 0.51.0 마검사 2차 (아케인 블레이드 각인 번짐 · 거대한 마력 대검 · 오버드라이브 검기 · 소드 콜러 스스로 싸우는 칼 · 제미니 소드)
        JobTraits8.init(); // 0.51.0 매지션 2차 (아크메이지 별자리 · 스펠 서지 · 네크로맨서 망자의 군대 · 무덤 · 표식)
        JobTraits9.init(); // 0.51.0 궁수 2차 (스나이퍼 급소 사격 · 헌터스 마크 · 배틀 아처 난사 · 스톰 스트라이드 · 팰컨 다이브 초기화)
        JobTraits10.init(); // 0.51.0 암살자 2차 (트릭스터 미러 팰리스 · 들불 표식 · 피에로 쇼타임 — 소환물 · 나눠 받음 · 핸드스프링 — 트릭스터의 표식은 JobTraits3)
        JobTraits11.init(); // 0.51.0 창술사 2차 (스톰 랜서 바람 · 질풍 창 · 렌딩 게일 · 드라군 비상 — 스톰 랜서의 창끝은 JobTraits3)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            JobTraitsMore.tickBreaks(Tasks.now());
            JobTraits6.tickGlobal(Tasks.now()); // 0.51.0 홀름강의 결투장
            JobTraits3.tickMarks(Tasks.now());
            JobTraits4.tickGlobal(server, Tasks.now());
            if (Tasks.now() % 4 == 0) tick(server);
        });
        // 죽음 · 차원 이동 · 탈것 부르기: 자원 · 태세 · 각인이 끝남
        RuneKit.onLifeEnded(JobTraits::lifeEnded);
        // 0.41.0 평타 강화 모습 (사용자 "직업 평타 버프도 포함"): 평타 태세 (명경지수 · 마검 각성 · 사신 현신 — rules_more SWING_LOOK 에 있는 태세) 동안
        // 그 태세의 궤적 색 · 범위 배율 (사신 현신의 칸은 jobs 의 range — 근접 평타만)
        SwingLook.addSource((sp, out) -> {
            State st = STATES.get(sp.getUUID());
            if (st == null || st.stance.isEmpty() || st.stanceUntil <= Tasks.now()) return;
            SwingLooks.Look l = SwingLooks.of(st.stance);
            if (l == null) return;
            Jobs.Job j = JobSystem.job(sp);
            Jobs.Skill k = j == null ? null : j.skill(st.stance);
            double range = k == null ? l.range() : k.num("range", l.range());
            if (range != l.range()) l = new SwingLooks.Look(l.key(), l.name(), l.scale(), Math.max(0, range), l.glow(), l.aura(), l.weapons());
            out.add(new SwingLook.Active(l, st.stanceUntil, false));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            MARKS.clear();
        });
    }

    static State of(ServerPlayer sp) {
        return STATES.computeIfAbsent(sp.getUUID(), k -> new State());
    }

    /** 직업을 바꿈 · 시험: 자원 · 태세 · 각인을 지우고 게이지를 다시 보냄 */
    static void reset(ServerPlayer sp) {
        endStance(sp);
        STATES.remove(sp.getUUID());
        JobTraitsMore.reset(sp);
        JobTraits3.reset(sp);
        JobTraits4.reset(sp);
        JobTraits5.reset(sp);
        JobTraits6.reset(sp);
        JobTraits7.reset(sp);
        JobTraits8.reset(sp);
        JobTraits9.reset(sp);
        JobTraits10.reset(sp);
        JobTraits11.reset(sp);
        String p = sp.getUUID() + "|";
        MARKS.keySet().removeIf(k -> k.startsWith(p));
        sendGauge(sp, true);
    }

    /** 나감 */
    static void forget(UUID id) {
        STATES.remove(id);
        JobTraitsMore.forget(id);
        JobTraits3.forget(id);
        JobTraits4.forget(id);
        JobTraits5.forget(id);
        JobTraits6.forget(id);
        JobTraits7.forget(id);
        JobTraits8.forget(id);
        JobTraits9.forget(id);
        JobTraits10.forget(id);
        JobTraits11.forget(id);
        String p = id + "|";
        MARKS.keySet().removeIf(k -> k.startsWith(p));
    }

    private static void lifeEnded(UUID id) {
        JobTraitsMore.lifeEnded(id);
        JobTraits3.lifeEnded(id);
        JobTraits4.lifeEnded(id);
        JobTraits5.lifeEnded(id);
        JobTraits6.lifeEnded(id);
        JobTraits7.lifeEnded(id);
        JobTraits8.lifeEnded(id);
        JobTraits9.lifeEnded(id);
        JobTraits10.lifeEnded(id);
        JobTraits11.lifeEnded(id);
        State st = STATES.get(id);
        if (st != null) {
            st.stacks = 0;
            st.stance = "";
            st.stanceName = "";
            st.stanceUntil = 0;
            st.sureUntil = 0;
            st.target = null;
        }
        String p = id + "|";
        MARKS.keySet().removeIf(k -> k.startsWith(p));
    }

    // ------------------------------------------------------------ 검세 (검사)

    /** 지금 검세 */
    public static int stacks(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.stacks;
    }

    /** 검세를 n 개까지 씀 — 쓴 수 */
    static int useStacks(ServerPlayer sp, int n) {
        State st = of(sp);
        int k = Math.max(0, Math.min(n, st.stacks));
        st.stacks -= k;
        st.lastDecay = Tasks.now();
        sendGauge(sp, false);
        return k;
    }

    /** 시험용: 검세를 정함 */
    public static void setStacks(ServerPlayer sp, int n) {
        State st = of(sp);
        st.stacks = Math.max(0, n);
        st.lastGain = st.lastDecay = Tasks.now();
        sendGauge(sp, false);
    }

    /** 0.51.0 검세 · 투지를 n 개 더 (받아넘기기 — 쌓일 때 연출 · 다 차면 고리) */
    static void addStacks(ServerPlayer sp, int n) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || !fervor(j)) return;
        for (int i = 0; i < n; i++) gain(sp, j);
    }

    private static void gain(ServerPlayer sp, Jobs.Job j) {
        State st = of(sp);
        int max = j.trait() == null ? 5 : Math.max(1, j.trait().max());
        long now = Tasks.now();
        st.lastGain = now;
        st.lastDecay = now;
        if (st.stacks >= max) return;
        st.stacks++;
        // 쌓임: 칼끝 쪽 작은 칼날 빛 (1인칭에서도 보이게 눈앞 오른쪽 아래) — 다섯째는 고리
        ServerLevel level = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        SkillFx.fx(level, "rpg:arrow_spark", V.up(h.hand(), 0.25), Opt.of("dir", V.UP, "speed", 1.4, "size", 0.14, "grow", 0.8, "life", 0.35, "color",
                JobKit.core(j), "alpha", 1.0));
        if (st.stacks == max) {
            // 다 참: 칼끝 앞 작은 터짐 (1인칭 오른쪽 아래)
            JobFx.burst(level, V.up(V.add(h.hand(), h.f(), 0.6), 0.3), 0.3, j, 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
            SkillFx.play(level, "block.bell.hit", sp.position(), 2.0, 0.25);
        }
        sendGauge(sp, false);
    }

    // ------------------------------------------------------------ 태세

    /** 태세 시작 (명경지수 · 마검 각성): ticks 틱 */
    static void startStance(ServerPlayer sp, Jobs.Skill k, int ticks) {
        State st = of(sp);
        st.stance = k.id();
        st.stanceName = k.name();
        st.stanceUntil = Tasks.now() + ticks;
        st.stanceTotal = ticks;
        // 마검 각성: 평타 사거리 +reach 칸
        double reach = k.num("reach", 0);
        AttributeInstance a = sp.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (a != null) {
            if (reach > 0) a.addOrUpdateTransientModifier(new AttributeModifier(REACH_ID, reach, AttributeModifier.Operation.ADD_VALUE));
            else a.removeModifier(REACH_ID);
        }
        sendGauge(sp, true);
    }

    /** 0.41.0 시험용: 직업 jobId 의 태세 스킬 skillId 를 ticks 틱 걺 (스킬을 쓰지 않고 — 사진 · 범위 시험). 없는 스킬이면 false */
    public static boolean testStance(ServerPlayer sp, String jobId, String skillId, int ticks) {
        Jobs.Job j = Jobs.job(jobId);
        Jobs.Skill k = j == null ? null : j.skill(skillId);
        if (k == null) return false;
        startStance(sp, k, ticks);
        return true;
    }

    /** 0.41.0 시험용: 태세를 조용히 끝냄 (끝난 뒤 처리 없이) */
    public static void testEndStance(ServerPlayer sp) {
        endStance(sp);
        sendGauge(sp, true);
    }

    /** 0.31.0 시험용: 지금 태세의 전체 틱 (강화가 늘린 지속 — 없으면 0) */
    public static int stanceTotal(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null || st.stance == null || st.stanceUntil <= Tasks.now() ? 0 : st.stanceTotal;
    }

    /** 이 태세가 걸려 있는지 */
    public static boolean stanceOn(ServerPlayer sp, String skillId) {
        State st = STATES.get(sp.getUUID());
        return st != null && stanceOn(st, skillId);
    }

    private static boolean stanceOn(State st, String skillId) {
        return skillId.equals(st.stance) && st.stanceUntil > Tasks.now();
    }

    /**
     * 마검 각성이 더한 사거리 칸 (걸린 속성 수정치 — 없으면 0). 시험 · 표시용: 창작 모드의 사거리 +2 (ServerPlayer 가 틱마다 붙임) 와 섞이지 않게
     * 값 대신 이 수정치를 봄
     */
    public static double reachBonus(ServerPlayer sp) {
        AttributeInstance a = sp.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        AttributeModifier m = a == null ? null : a.getModifier(REACH_ID);
        return m == null ? 0 : m.amount();
    }

    /**
     * 0.29.0 태세를 바로 끝냄 (암영이 공격으로 풀림) — 그 태세가 걸려 있을 때만. 끝난 뒤 처리 (JobTraitsMore · JobTraits3 stanceEnded) 도 부름
     */
    static void stopStance(ServerPlayer sp, String id) {
        State st = STATES.get(sp.getUUID());
        if (st == null || !id.equals(st.stance) || st.stanceUntil <= Tasks.now()) return;
        endStance(sp);
        JobTraitsMore.stanceEnded(sp, id);
        JobTraits3.stanceEnded(sp, id);
        JobTraits4.stanceEnded(sp, id);
        JobFx3d.drop(sp, id, 0.25); // 0.47.0 태세의 입체 물체 (도는 검 · 앞의 창 · 방패)
        sendGauge(sp, true);
    }

    private static void endStance(ServerPlayer sp) {
        AttributeInstance a = sp.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (a != null) a.removeModifier(REACH_ID);
        State st = STATES.get(sp.getUUID());
        if (st != null) {
            st.stance = "";
            st.stanceName = "";
            st.stanceUntil = 0;
            st.stanceTotal = 0;
        }
    }

    // ------------------------------------------------------------ 섬보: 다음 평타 치명타 확정

    static void sureCritFor(ServerPlayer sp, int ticks) {
        of(sp).sureUntil = Tasks.now() + ticks;
    }

    /** 0.51.0 마력 각인을 쓰는 직업 (마검사 · 각성 아케인 블레이드 — 반전 소드 콜러는 스스로 싸우는 칼) */
    static boolean markJob(Jobs.Job j) {
        return j != null && (j.id().equals("spellblade") || j.id().equals(JobTraits7.AB));
    }

    /** 0.51.0 투지를 쓰는 직업 (검사 · 각성 소드마스터 — 반전 블러드 나이트는 피의 갈증) */
    static boolean fervor(Jobs.Job j) {
        return j != null && (j.id().equals("swordsman") || j.id().equals(JobTraits5.SM));
    }

    /** 이번 평타가 치명타로 확정인지 (검사 · 칼 · 섬보 뒤 — 쓰면 지움. 0.51.0 소드마스터 그라니의 질주도) */
    private static boolean sureCrit(ServerPlayer sp, Weapon w) {
        State st = STATES.get(sp.getUUID());
        if (st == null || st.sureUntil < Tasks.now() || w == null || !"sword".equals(w.type())) return false;
        Jobs.Job j = JobSystem.job(sp);
        if (!fervor(j)) return false;
        st.sureUntil = 0;
        return true;
    }

    // ------------------------------------------------------------ 마력 각인 (마검사)

    private static String key(Entity attacker, Entity victim) {
        return attacker.getUUID() + "|" + victim.getUUID();
    }

    /** 각인 하나 더 (최대 trait.max · markFor 틱) — 머리 위에 각인 수만큼 룬 */
    static void mark(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        if (e == null || e.isRemoved()) return;
        long now = Tasks.now();
        long[] m = MARKS.computeIfAbsent(key(sp, e), k -> new long[2]);
        if (m[1] < now) m[0] = 0;
        int max = j.trait() == null ? 3 : Math.max(1, j.trait().max());
        m[0] = Math.min(max, m[0] + 1);
        m[1] = now + (long) j.trait().def().numOr("markFor", 200);
        of(sp).target = e.getUUID();
        if (e.level() instanceof ServerLevel level && RuneKit.fxQuota("jobMark", 6)) {
            // 각인: 머리 위에 각인 수만큼 반짝임이 한 줄로 (돌며 빛남)
            Vec3 over = RuneKit.overHead(e, 0.5);
            Vec3 right = V.rightOf(V.flat(V.sub(over, sp.getEyePosition())));
            int n = (int) m[0];
            for (int i = 0; i < n; i++) {
                Vec3 at = V.add(over, right, (i - (n - 1) / 2.0) * 0.3);
                RuneKit.rfx(level, "prop", at, Opt.of("size", 0.14, "life", 0.8, "cell", (double) RuneKit.RFX.get("glint")[0], "spin", 45.0, "spinRate", 160.0,
                        "color", i == n - 1 ? JobFx.inner(j) : JobFx.outer(j), "grow", 0.3, "fade", 0.4));
            }
        }
        sendGauge(sp, false);
    }

    /** 이 적의 각인 수 (끝났으면 0) */
    public static int marks(ServerPlayer sp, Entity e) {
        if (e == null) return 0;
        long[] m = MARKS.get(key(sp, e));
        return m == null || m[1] < Tasks.now() ? 0 : (int) m[0];
    }

    /** 각인을 모두 떼어 냄 (비전 거검 — 모두 터뜨림) — 뗀 수 */
    static int takeAllMarks(ServerPlayer sp, Entity e) {
        long[] m = MARKS.remove(key(sp, e));
        int n = m == null || m[1] < Tasks.now() ? 0 : (int) m[0];
        sendGauge(sp, false);
        return n;
    }

    /** 시험용: 각인을 정함 */
    public static void setMarks(ServerPlayer sp, LivingEntity e, int n) {
        MARKS.put(key(sp, e), new long[] {Math.max(0, n), Tasks.now() + 200});
        of(sp).target = e.getUUID();
        sendGauge(sp, false);
    }

    // ------------------------------------------------------------ 평타 훅

    /** 명경지수: 칼 평타가 두 번 베기 (두 번째 = 평타 × echo) */
    private static RpgCombat.Bonus mirrorBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (w == null || !"sword".equals(w.type()) || !(dmg > 0) || !stanceOn(sp, "swordsman_mirror")) return null;
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || !j.id().equals("swordsman")) return null;
        Jobs.Skill k = j.skill("swordsman_mirror");
        double echo = k == null ? 0.35 : JobUpgrades.num(sp, k, "echo", 0.35); // 0.31.0 강화
        return new RpgCombat.Bonus(dmg * echo, "", KEY_MIRROR, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    /**
     * 마력 각인 폭발: 각인 찍힌 적을 직업 무기 평타로 치면 하나가 터짐 (위력 × markBurst — 들어가면 씀). 0.51.0 아케인 블레이드: 각인이 다 찬 적 (trait.max — 다섯) 이면
     * 다섯이 한꺼번에 (위력 × markBurst × 다섯 · 큰 숫자 — 하늘에서 거대한 마력 대검)
     */
    private static RpgCombat.Bonus markBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        int n = victim == null ? 0 : marks(sp, victim);
        if (w == null || n <= 0) return null;
        Jobs.Job j = JobSystem.job(sp);
        if (!markJob(j) || !j.usesWeapon(w.type())) return null;
        double add = JobSystem.power(sp, j) * j.trait().def().numOr("markBurst", 1.0) * mmorpg.fabric.stattree.StatTrees.atkMult(sp);
        if (j.id().equals(JobTraits7.AB) && n >= Math.max(1, j.trait().max()))
            return new RpgCombat.Bonus(add * n, "§d◈×" + n + " ", KEY_MARK_FULL, DamageNumbers.Style.JOB_BIG, null, JobKit.grad(j));
        return new RpgCombat.Bonus(add, "§d◈", KEY_MARK, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    private static boolean has(RpgCombat.WeaponBonus b, String key) {
        if (b == null) return false;
        for (RpgCombat.Bonus p : b.parts()) if (key.equals(p.key())) return true;
        return false;
    }

    /** 무기로 맞힘 (피해가 들어간 뒤): 검세 +1, 각인을 씀 · 연출 (평타에 더한 몫이 들어간 것만) */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || w == null || victim == null) return;
        ServerLevel level = sp.level();
        Vec3 at = Kit.bodyPoint(victim);
        switch (j.id()) {
            case "swordsman", "swordmaster" -> {
                if (!"sword".equals(w.type()) || !Combat.isMeleeWeapon(w)) return;
                gain(sp, j);
                if (JobTraits.stanceOn(sp, JobTraits5.DRAGON)) gain(sp, j); // 0.51.0 용의 피: 칼 평타가 투지 +2
                if (has(bonus, KEY_MIRROR)) {
                    // 두 번째 베기: 맞은 자리에 반대로 그은 물빛 칼날 (조금 늦게)
                    RuneKit.later(3, () -> {
                        if (victim.isRemoved()) return;
                        JobFx.hit(victim, j, 1.0);
                        SkillFx.play(level, "item.spear.attack_miss", Kit.bodyPoint(victim), 1.7, 0.5);
                    });
                }
            }
            case "spellblade", "arcaneblade" -> {
                if (!j.usesWeapon(w.type())) return;
                of(sp).target = victim.getUUID();
                if (has(bonus, KEY_MARK_FULL)) {
                    // 0.51.0 아케인 블레이드: 다섯이 한꺼번에 — 하늘에서 거대한 마력 대검이 내리꽂히고 둘레로 다섯 몫이 번짐
                    int n = takeAllMarks(sp, victim);
                    JobTraits7.giantSword(sp, victim, j);
                    JobTraits7.spread(sp, victim, j, Math.max(1, n), JobTraits7.FULL_FALL);
                } else if (has(bonus, KEY_MARK)) {
                    long[] m = MARKS.get(key(sp, victim));
                    if (m != null && m[0] > 0) m[0]--;
                    // 각인 폭발: 맞은 자리에서 터짐
                    JobFx.burst(level, at, 0.85 + Kit.bodyPad(victim) * 0.4, j, 0.3, Opt.empty());
                    SkillFx.play(level, "chime.amethyst_block", at, 1.6, 0.8);
                    JobTraits7.spread(sp, victim, j, 1, 0); // 0.51.0 아케인 블레이드: 둘레로 마력 폭발이 번짐
                }
                sendGauge(sp, false);
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------ 틱: 검세가 사라짐 · 태세가 끝남 · 태세 연출 · 게이지

    private static void tick(MinecraftServer server) {
        long now = Tasks.now();
        if (MARKS.size() > 256) MARKS.values().removeIf(m -> m[1] < now);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            State st = STATES.get(sp.getUUID());
            Jobs.Job j = JobSystem.job(sp);
            if (j != null) {
                JobTraitsMore.tick(sp, j, now); // 0.28.0 분노 · 연쇄 · 호흡
                JobTraits3.tick(sp, j, now); // 0.29.0 암영 · 창끝 · 혼
                JobTraits4.tick(sp, j, now); // 0.30.0 수호의 맹세 회복
                JobTraits5.tick(sp, j, now); // 0.51.0 흡혈 숫자
                JobTraits6.tick(sp, j, now); // 0.51.0 곰의 화신 · 결투
                JobTraits7.tick(sp, j, now); // 0.51.0 소드 콜러 쌓임 · 꽂힌 칼
                JobTraits8.tick(sp, j, now); // 0.51.0 아크메이지 별 · 네크로맨서 무덤 · 표식
                JobTraits9.tick(sp, j, now); // 0.51.0 배틀 아처 갈래 게이지 · 스톰 스트라이드 이동 속도
                JobTraits10.tick(sp, j, now); // 0.51.0 피에로 (가장 가까운 소환물로 금빛 실)
                JobTraits11.tick(sp, j, now); // 0.51.0 스톰 랜서 바람이 사라짐 · 가득이면 창끝 소용돌이
            }
            if (st == null || j == null) continue;
            // 태세가 끝남
            if (!st.stance.isEmpty() && st.stanceUntil <= now) {
                String ended = st.stance;
                endStance(sp);
                SkillFx.play(sp.level(), "block.amethyst_block.chime", sp.position(), 0.8, 0.4);
                JobTraitsMore.stanceEnded(sp, ended); // 마나 과부하 → 이동 느려짐
                JobTraits3.stanceEnded(sp, ended); // 0.29.0 암영 → 이동 빨라짐 · 투명이 풀림
                JobTraits4.stanceEnded(sp, ended); // 0.30.0 철옹성 → 이동 수정치를 뗌
                JobFx3d.drop(sp, ended, 0.25); // 0.47.0 태세의 입체 물체 (도는 검 · 앞의 창 · 방패)
            }
            // 검세: decay 틱 동안 못 맞히면 하나씩 사라짐 (명경지수 동안은 그대로)
            if (fervor(j) && st.stacks > 0 && !stanceOn(st, "swordsman_mirror") && !stanceOn(st, JobTraits5.DRAGON)) { // 0.51.0 용의 피도 투지 유지
                int decay = (int) j.trait().def().numOr("decay", 80);
                if (now - st.lastGain >= decay && now - st.lastDecay >= decay) {
                    st.stacks--;
                    st.lastDecay = now;
                }
            }
            // 태세 연출 (8틱마다): 몸에서 솟는 옅은 빛 (3인칭) + 칼의 빛 (명경지수 = 물빛 줄 · 마검 각성 = 마력으로 길어진 칼날)
            if (now % 8 == 0 && !st.stance.isEmpty() && st.stanceUntil > now && !sp.isSpectator()) stanceFx(sp, j, st);
            sendGauge(sp, false);
        }
    }

    /**
     * 태세 동안 8틱마다: 몸에서 솟는 옅은 빛 (3인칭 · 다른 사람 — 1인칭에서는 숨김) + 1인칭에서 보이는 칼의 빛 (명경지수 = 칼날을 따라 물빛 줄)
     */
    private static void stanceFx(ServerPlayer sp, Jobs.Job j, State st) {
        ServerLevel level = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        // 은신 (암영) 은 몸의 빛을 그리지 않음
        if (!st.stance.equals("assassin_veil")) JobFx.aura(sp, j, 2.4, 0.5, 0.55);
        if (st.stance.equals("swordsman_mirror")) {
            SkillFx.fx(level, "rpg:light_cut", V.up(h.hand(), 0.2), Opt.of("dir", V.UP, "size", 0.45, "thick", 0.07, "life", 0.4, "color", JobKit.core(j), "alpha", 0.8));
        } else if (st.stance.equals("warrior_spirit")) {
            // 투혼: 몸의 불꽃 (위 aura) + 손에서 솟는 불티 · 1인칭 화면 아래 양옆 불티
            RuneKit.auraCue(sp, JobKit.glow(j), 2);
            SkillFx.fx(level, "rpg:arrow_spark", V.up(h.hand(), 0.1), Opt.of("dir", V.UP, "speed", 1.6, "size", 0.13, "grow", 0.8, "life", 0.35, "color", JobKit.glow(j),
                    "alpha", 1.0));
        } else if (JobTraits3.stanceFx(sp, j, st.stance)) {
            // 0.29.0 암영 (1인칭 화면 가장자리의 옅은 연기) · 창벽 (앞의 창끝 셋) · 사신 현신 (등 뒤의 사신 그림자)
        } else if (JobTraits4.stanceFx(sp, j, st.stance)) {
            // 0.30.0 수호의 맹세 (등 뒤 빛의 날개) · 철옹성 (앞의 큰 빛의 방패)
        } else if (JobTraits5.stanceFx(sp, j, st.stance)) {
            // 0.51.0 용의 심장 (가슴의 금빛 불씨 · 칼의 금빛) · 회그니의 맹세 (칼끝에서 떨어지는 핏방울)
        } else if (JobTraits7.stanceFx(sp, j, st.stance)) {
            // 0.51.0 제미니 소드 (손의 칼에서 흩날리는 청록 빛살)
        } else if (JobTraits8.stanceFx(sp, j, st.stance)) {
            // 0.51.0 스펠 서지 (몸 둘레를 도는 금빛 별 · 완드 끝 별빛)
        } else if (JobTraits9.stanceFx(sp, j, st.stance)) {
            // 0.51.0 헌터스 마크 (활 앞 은빛 · 적 머리 위 은빛 표식) · 스톰 스트라이드 (발밑 폭풍 바람 · 바람 줄)
        } else if (JobTraits10.stanceFx(sp, j, st.stance)) {
            // 0.51.0 미러 팰리스 (손의 청록 섬광 — 도는 거울은 JobTraits10.palaceTick)
        } else if (st.stance.equals("archer_focus")) {
            // 정조준: 활을 든 손 앞 금빛 반짝임
            RuneKit.rfx(level, "prop", V.add(RuneKit.bowHand(sp), sp.getViewVector(1F), 0.35), Opt.of("size", 0.14, "life", 0.4, "cell",
                    (double) RuneKit.RFX.get("glint")[0], "spin", 30.0, "spinRate", 160.0, "color", JobKit.core(j), "grow", 0.4, "fade", 0.5, "vel", RuneKit.bodyVel(sp)));
        }
    }

    // ------------------------------------------------------------ 게이지

    /** 지금 게이지 (직업이 없으면 빈 것) */
    static JobSkills.GaugePayload gauge(ServerPlayer sp) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || j.trait() == null) return new JobSkills.GaugePayload("", 0, 0, "", 0, 0, 1F);
        State st = STATES.get(sp.getUUID());
        long now = Tasks.now();
        String stance = st != null && st.stanceUntil > now ? st.stanceName : "";
        int left = st != null && st.stanceUntil > now ? (int) (st.stanceUntil - now) : 0;
        int total = st != null && st.stanceUntil > now ? st.stanceTotal : 0;
        Object[] line = stance.isEmpty() ? JobTraits6.stanceLine(sp, j) : null; // 0.51.0 곰의 화신 (태세가 아니라 따로 — 같은 줄에 보임)
        if (line == null && stance.isEmpty()) line = JobTraits10.stanceLine(sp, j); // 0.51.0 피에로 할루시네이트 (광대 인형의 남은 시간)
        if (line == null && stance.isEmpty()) line = JobTraits11.stanceLine(sp, j); // 0.51.0 스톰 랜서 렌딩 게일 (바람 창이 박히는 남은 시간)
        if (line != null) {
            stance = (String) line[0];
            left = (int) line[1];
            total = (int) line[2];
        }
        int value = 0;
        if (fervor(j)) value = st == null ? 0 : st.stacks; // 0.51.0 소드마스터도 투지 (최대 8)
        else if (j.id().equals(JobTraits5.BK)) value = JobTraits5.gaugeValue(sp, j); // 0.51.0 블러드 나이트 = 피의 갈증 (잃은 체력)
        else if (j.id().equals(JobTraits6.GL)) value = JobTraits6.gaugeValue(sp, j); // 0.51.0 글래디에이터 = 결투
        else if (j.id().equals(JobTraits7.SC)) value = JobTraits7.gaugeValue(sp, j); // 0.51.0 소드 콜러 = 이어 벤 쌓임
        else if (j.id().equals(JobTraits8.NC)) value = JobTraits8.gaugeValue(sp, j); // 0.51.0 네크로맨서 = 망자 선원 수
        else if (j.id().equals(JobTraits9.BA)) value = JobTraits9.gaugeValue(sp, j); // 0.51.0 배틀 아처 = 다음 활 평타의 갈래 (1 · 3)
        else if (j.id().equals(JobTraits10.PR)) value = JobTraits10.gaugeValue(sp, j); // 0.51.0 피에로 = 소환물 수
        else if (j.id().equals(JobTraits11.SL) || j.id().equals(JobTraits11.DG)) value = JobTraits11.gaugeValue(sp, j); // 0.51.0 스톰 랜서 = 바람 · 드라군 = 높이
        else if (markJob(j)) { // 마검사 · 0.51.0 룬 스미스 = 그 적의 각인
            if (st != null && st.target != null) {
                Entity e = sp.level().getEntity(st.target);
                value = e == null || e.isRemoved() || !e.isAlive() ? 0 : marks(sp, e);
            }
        } else value = JobTraitsMore.gaugeValue(sp, j); // 0.28.0 분노 · 연쇄 · 호흡
        // 0.51.0 공격 속도 (클라이언트 평타 사이 — 0.05 단위로 맞춤)
        float speed = (float) (Math.round(mmorpg.fabric.RpgCombat.attackSpeed.applyAsDouble(sp) * 20) / 20.0);
        return new JobSkills.GaugePayload(j.trait().name(), value, Math.max(0, j.trait().max()), stance, left, total, speed);
    }

    /** 게이지를 보냄 (force 가 아니면 바뀌었을 때만 — 남은 틱은 클라이언트가 셈) */
    static void sendGauge(ServerPlayer sp, boolean force) {
        if (sp == null || sp.isRemoved()) return;
        JobSkills.GaugePayload g = gauge(sp);
        String sig = g.name() + "|" + g.value() + "|" + g.max() + "|" + g.stance() + "|" + (g.left() > 0 ? Tasks.now() + g.left() : 0) + "|" + g.speed();
        State st = of(sp);
        if (!force && sig.equals(st.sent)) return;
        st.sent = sig;
        JobSkills.sendGauge(sp, g);
    }
}
