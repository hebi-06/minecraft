package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Combat;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
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
    /** 마검 각성 사거리 +칸 (ENTITY_INTERACTION_RANGE) */
    private static final net.minecraft.resources.Identifier REACH_ID = Mmorpg.id("job_reach");
    /** 평타에 더한 몫의 구분 (WeaponBonus.parts — 들어간 뒤 HitHook 이 연출 · 각인 씀) */
    static final String KEY_MIRROR = "job_mirror";
    static final String KEY_MARK = "job_mark";
    static final String KEY_AWAKEN = "job_awaken";

    static void init() {
        RpgCombat.addScaledBonusHook(JobTraits::mirrorBonus);
        RpgCombat.addScaledBonusHook(JobTraits::markBonus);
        RpgCombat.addScaledBonusHook(JobTraits::awakenBonus);
        RpgCombat.addWeaponHitHook(JobTraits::onWeaponHit);
        RpgCombat.sureCrit = JobTraits::sureCrit;
        JobTraitsMore.init(); // 0.28.0 전사 · 매지션 · 궁수
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            JobTraitsMore.tickBreaks(Tasks.now());
            if (Tasks.now() % 4 == 0) tick(server);
        });
        // 죽음 · 차원 이동 · 탈것 부르기: 자원 · 태세 · 각인이 끝남
        RuneKit.onLifeEnded(JobTraits::lifeEnded);
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
        String p = sp.getUUID() + "|";
        MARKS.keySet().removeIf(k -> k.startsWith(p));
        sendGauge(sp, true);
    }

    /** 나감 */
    static void forget(UUID id) {
        STATES.remove(id);
        JobTraitsMore.forget(id);
        String p = id + "|";
        MARKS.keySet().removeIf(k -> k.startsWith(p));
    }

    private static void lifeEnded(UUID id) {
        JobTraitsMore.lifeEnded(id);
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

    /** 이번 평타가 치명타로 확정인지 (검사 · 칼 · 섬보 뒤 — 쓰면 지움) */
    private static boolean sureCrit(ServerPlayer sp, Weapon w) {
        State st = STATES.get(sp.getUUID());
        if (st == null || st.sureUntil < Tasks.now() || w == null || !"sword".equals(w.type())) return false;
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || !j.id().equals("swordsman")) return false;
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
        double echo = k == null ? 0.35 : k.num("echo", 0.35);
        return new RpgCombat.Bonus(dmg * echo, "", KEY_MIRROR, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    /** 마력 각인 폭발: 각인 찍힌 적을 직업 무기 평타로 치면 하나가 터짐 (위력 × markBurst — 들어가면 씀) */
    private static RpgCombat.Bonus markBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (w == null || victim == null || marks(sp, victim) <= 0) return null;
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || !j.id().equals("spellblade") || !j.usesWeapon(w.type())) return null;
        double add = JobSystem.power(sp, j) * j.trait().def().numOr("markBurst", 1.0) * mmorpg.fabric.stattree.StatTrees.atkMult(sp);
        return new RpgCombat.Bonus(add, "§d◈", KEY_MARK, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    /** 마검 각성: 평타가 맞을 때마다 마력 참격 잔상 (위력 × echo) */
    private static RpgCombat.Bonus awakenBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (w == null || !stanceOn(sp, "spellblade_awaken")) return null;
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || !j.id().equals("spellblade") || !j.usesWeapon(w.type())) return null;
        Jobs.Skill k = j.skill("spellblade_awaken");
        double add = JobSystem.power(sp, j) * (k == null ? 0.93 : k.num("echo", 0.93)) * mmorpg.fabric.stattree.StatTrees.atkMult(sp);
        return new RpgCombat.Bonus(add, "", KEY_AWAKEN, DamageNumbers.Style.JOB, null, JobKit.grad(j));
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
            case "swordsman" -> {
                if (!"sword".equals(w.type()) || !Combat.isMeleeWeapon(w)) return;
                gain(sp, j);
                if (has(bonus, KEY_MIRROR)) {
                    // 두 번째 베기: 맞은 자리에 반대로 그은 물빛 칼날 (조금 늦게)
                    RuneKit.later(3, () -> {
                        if (victim.isRemoved()) return;
                        JobFx.hit(victim, j, 1.0);
                        SkillFx.play(level, "item.spear.attack_miss", Kit.bodyPoint(victim), 1.7, 0.5);
                    });
                }
            }
            case "spellblade" -> {
                if (!j.usesWeapon(w.type())) return;
                of(sp).target = victim.getUUID();
                if (has(bonus, KEY_MARK)) {
                    long[] m = MARKS.get(key(sp, victim));
                    if (m != null && m[0] > 0) m[0]--;
                    // 각인 폭발: 맞은 자리에서 터짐
                    JobFx.burst(level, at, 0.85 + Kit.bodyPad(victim) * 0.4, j, 0.3, Opt.empty());
                    SkillFx.play(level, "chime.amethyst_block", at, 1.6, 0.8);
                }
                if (has(bonus, KEY_AWAKEN)) {
                    // 마력 참격 잔상: 평타 궤적을 따라 보라 · 청록 참격이 한 번 더 (2틱 뒤)
                    RuneKit.later(2, () -> {
                        if (victim.isRemoved()) return;
                        JobFx.slash(sp, j, 2.0, 0.3, 1.3, 160.0, 0.22);
                        JobFx.hit(victim, j, 1.1);
                    });
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
            if (j != null) JobTraitsMore.tick(sp, j, now); // 0.28.0 분노 · 연쇄 · 호흡
            if (st == null || j == null) continue;
            // 태세가 끝남
            if (!st.stance.isEmpty() && st.stanceUntil <= now) {
                String ended = st.stance;
                endStance(sp);
                SkillFx.play(sp.level(), "block.amethyst_block.chime", sp.position(), 0.8, 0.4);
                JobTraitsMore.stanceEnded(sp, ended); // 마나 과부하 → 이동 느려짐
            }
            // 검세: decay 틱 동안 못 맞히면 하나씩 사라짐 (명경지수 동안은 그대로)
            if (j.id().equals("swordsman") && st.stacks > 0 && !stanceOn(st, "swordsman_mirror")) {
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
     * 태세 동안 8틱마다: 몸에서 솟는 옅은 빛 (3인칭 · 다른 사람 — 1인칭에서는 숨김) + 1인칭에서 보이는 칼의 빛 (명경지수 = 칼날을 따라 물빛 줄 ·
     * 마검 각성 = 칼이 마력으로 길어짐)
     */
    private static void stanceFx(ServerPlayer sp, Jobs.Job j, State st) {
        ServerLevel level = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        JobFx.aura(sp, j, 2.4, 0.5, 0.55);
        if (st.stance.equals("swordsman_mirror")) {
            SkillFx.fx(level, "rpg:light_cut", V.up(h.hand(), 0.2), Opt.of("dir", V.UP, "size", 0.45, "thick", 0.07, "life", 0.4, "color", JobKit.core(j), "alpha", 0.8));
        } else if (st.stance.equals("spellblade_awaken")) {
            RuneKit.rfx2(level, "sword", V.up(h.hand(), 0.35), Opt.of("dir", V.UP, "size", 0.5, "life", 0.42, "alpha", 0.7, "fade", 0.5, "vel", RuneKit.bodyVel(sp)),
                    JobKit.glow(j), JobKit.core(j));
        } else if (st.stance.equals("warrior_spirit")) {
            // 투혼: 몸의 불꽃 (위 aura) + 손에서 솟는 불티 · 1인칭 화면 아래 양옆 불티
            RuneKit.auraCue(sp, JobKit.glow(j), 2);
            SkillFx.fx(level, "rpg:arrow_spark", V.up(h.hand(), 0.1), Opt.of("dir", V.UP, "speed", 1.6, "size", 0.13, "grow", 0.8, "life", 0.35, "color", JobKit.glow(j),
                    "alpha", 1.0));
        } else if (st.stance.equals("mage_overload")) {
            // 마나 과부하: 완드 끝에 작은 구체 · 몸 둘레를 도는 반짝임 (3인칭)
            Vec3 tip = RpgCombat.circleAt(sp)[0];
            JobFx.orb(level, tip, 0.11, j, 0.42, Opt.of("vel", RuneKit.bodyVel(sp), "alpha", 0.95));
            double a = Tasks.now() * 0.35;
            for (int i = 0; i < 3; i++) {
                double b = a + i * Math.PI * 2 / 3;
                Vec3 p = V.up(sp.position(), 0.6 + 0.5 * i);
                RuneKit.rfx(level, "prop", new Vec3(p.x + Math.cos(b) * 0.85, p.y, p.z + Math.sin(b) * 0.85), JobFx.hide1p(sp).with("size", 0.13, "life", 0.42, "cell",
                        (double) RuneKit.RFX.get("glint")[0], "spin", 0.0, "spinRate", 180.0, "color", JobKit.core(j), "fade", 0.4, "vel", RuneKit.bodyVel(sp)));
            }
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
        if (j == null || j.trait() == null) return new JobSkills.GaugePayload("", 0, 0, "", 0, 0);
        State st = STATES.get(sp.getUUID());
        long now = Tasks.now();
        String stance = st != null && st.stanceUntil > now ? st.stanceName : "";
        int left = st != null && st.stanceUntil > now ? (int) (st.stanceUntil - now) : 0;
        int total = st != null && st.stanceUntil > now ? st.stanceTotal : 0;
        int value = 0;
        if (j.id().equals("swordsman")) value = st == null ? 0 : st.stacks;
        else if (j.id().equals("spellblade")) {
            if (st != null && st.target != null) {
                Entity e = sp.level().getEntity(st.target);
                value = e == null || e.isRemoved() || !e.isAlive() ? 0 : marks(sp, e);
            }
        } else value = JobTraitsMore.gaugeValue(sp, j); // 0.28.0 분노 · 연쇄 · 호흡
        return new JobSkills.GaugePayload(j.trait().name(), value, Math.max(0, j.trait().max()), stance, left, total);
    }

    /** 게이지를 보냄 (force 가 아니면 바뀌었을 때만 — 남은 틱은 클라이언트가 셈) */
    static void sendGauge(ServerPlayer sp, boolean force) {
        if (sp == null || sp.isRemoved()) return;
        JobSkills.GaugePayload g = gauge(sp);
        String sig = g.name() + "|" + g.value() + "|" + g.max() + "|" + g.stance() + "|" + (g.left() > 0 ? Tasks.now() + g.left() : 0);
        State st = of(sp);
        if (!force && sig.equals(st.sent)) return;
        st.sent = sig;
        JobSkills.sendGauge(sp, g);
    }
}
