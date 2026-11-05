package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 — 매지션 각성 아크메이지 · 반전 네크로맨서 특성 · 태세 (재설계안 2판 — 2026-11-06 사용자 "다음 작업", 매지션 차례):
 * <ul>
 * <li>아크메이지 「주문 연쇄」 (이어지는 것 · +12% 는 매지션과 같음 — JobTraitsMore.chainNext · chainCommit, 최대 trait.max = 5): 이은 스킬 (연쇄 1 ~ 5) 마다 그 자리 (겨눈 적 ·
 * 조준한 곳) 에 별 — 같은 적이면 오각형 꼭짓점을 하나 건너 (차례로 이으면 ★). 다섯째 별에 별자리 완성 — 별을 잇는 금빛 선 (닫힌 고리) → starDelay 틱 뒤 별마다 폭발
 * (위력 × star · 둘레 starRadius 칸) · 선에 닿은 적 (lineWidth) 위력 × line — 한 적에게 한 번에 (숫자 여러 줄) → 연쇄는 처음부터. 연쇄가 끊기면 별도 사라짐.
 * 스펠 서지 (태세 SURGE — 롤 라이즈 Q): 그동안 다른 직업 스킬을 쓰면 스타 체인 (CHAIN) 의 재사용 대기가 바로 돌아옴 · 연쇄가 surge.window 틱 이어짐 ({@link #chainWindow})</li>
 * <li>네크로맨서 「망자의 군대」 (롤 요릭 패시브): 둘레 graveRange 칸에서 쓰러진 적대 몹의 자리에 graveFor 틱 무덤 (rfx_jgrave — 녹색 혼불이 타는 묘비, 그레이브 콜을 다시
 * 쓰면 무덤마다 망자 선원 NecroCrew). 그레이브 콜 · 소울 미스트의 표식이 남은 적과 블라이트 안에서 쓰러진 적은 무덤 없이 바로 망자로. 소울 미스트 표식은 망자가 먼저
 * 노리고 crewBonus 더 셈 · 그레이브야드 고리 안의 적도 망자가 먼저 노림</li>
 * </ul>
 */
public final class JobTraits8 {
    private JobTraits8() {}

    static final String AM = "arcmage";
    static final String NC = "necromancer";
    static final String SURGE = "arcmage_surge";
    static final String CHAIN = "arcmage_chain";
    static final String MIST = "necromancer_mist";
    /** 밤하늘 자주 (별자리 선의 바깥 빛 · 별 무리) */
    static final double[] NIGHT = {0.42, 0.26, 0.86};
    /** 금빛 별 (선 · 별 심지) */
    static final double[] GOLD = {1.0, 0.86, 0.42};

    // ------------------------------------------------------------ 상태

    /** 아크메이지: 지금 연쇄의 별 (스킬을 쓴 자리 — 차례대로) */
    private static final Map<UUID, List<Vec3>> STARS = new HashMap<>();
    /** 네크로맨서 표식: 적 → {주인, 끝 틱, 소울 미스트 (망자가 먼저 노리고 더 셈)} — 끝나기 전에 쓰러지면 바로 망자로 */
    private record Mark(UUID owner, long until, boolean mist) {}

    private static final Map<UUID, Mark> MARKS = new HashMap<>();
    /** 네크로맨서가 마지막으로 친 적 (망자가 노림) */
    private static final Map<UUID, UUID> FOCUS = new HashMap<>();

    /** 무덤 (쓰러진 적대 몹의 자리 — 그레이브 콜을 다시 쓰면 망자가 일어남) */
    record Grave(ServerLevel level, Vec3 at, float yaw, long born, long until) {}

    private static final List<Grave> GRAVES = new ArrayList<>();

    /** 그레이브야드 고리 (안의 적은 망자가 먼저 노림) */
    private record Yard(UUID owner, ServerLevel level, Vec3 c, double r, long until) {}

    private static final List<Yard> YARDS = new ArrayList<>();

    /** 블라이트 (주인을 따라가는 썩은 땅 — 안에서 쓰러진 적은 바로 망자로) */
    private record Blight(double r, long until) {}

    private static final Map<UUID, Blight> BLIGHTS = new HashMap<>();

    static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register(JobTraits8::onDeath);
        RpgCombat.addWeaponHitHook(JobTraits8::onWeaponHit);
        NecroCrew.init();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STARS.clear();
            MARKS.clear();
            FOCUS.clear();
            GRAVES.clear();
            YARDS.clear();
            BLIGHTS.clear();
        });
    }

    static Jobs.Job jobIf(ServerPlayer sp, String id) {
        Jobs.Job j = JobSystem.job(sp);
        return j != null && id.equals(j.id()) ? j : null;
    }

    static double trait(Jobs.Job j, String key, double fallback) {
        return j == null || j.trait() == null ? fallback : j.trait().def().numOr(key, fallback);
    }

    private static void drop(UUID id) {
        STARS.remove(id);
        FOCUS.remove(id);
        BLIGHTS.remove(id);
        MARKS.values().removeIf(m -> m.owner().equals(id));
        YARDS.removeIf(y -> y.owner().equals(id));
    }

    static void reset(ServerPlayer sp) {
        drop(sp.getUUID());
        NecroCrew.disband(sp.getUUID(), true);
    }

    static void forget(UUID id) {
        drop(id);
        NecroCrew.disband(id, false);
    }

    static void lifeEnded(UUID id) {
        drop(id);
        NecroCrew.disband(id, true);
    }

    // ------------------------------------------------------------ 아크메이지: 별 · 별자리 · 스펠 서지

    /** 지금 별 수 (시험) */
    public static int stars(ServerPlayer sp) {
        List<Vec3> l = STARS.get(sp.getUUID());
        return l == null ? 0 : l.size();
    }

    /** 시험용: 지금 겨눈 곳에 별 n 개 (연쇄는 JobTraitsMore.setChain 으로 따로) */
    public static void testStars(ServerPlayer sp, int n) {
        List<Vec3> l = STARS.computeIfAbsent(sp.getUUID(), u -> new ArrayList<>());
        l.clear();
        for (int i = 0; i < n; i++) l.add(starPoint(sp, i));
    }

    /** 별을 지움 (연쇄가 끊김 · 시험의 setChain) */
    static void clearStars(ServerPlayer sp) {
        STARS.remove(sp.getUUID());
    }

    /** 스펠 서지가 켜져 있는지 */
    public static boolean surgeOn(ServerPlayer sp) {
        return JobTraits.stanceOn(sp, SURGE);
    }

    /** 주문 연쇄가 이어지는 틱: 스펠 서지 동안 surge.window (8초), 아니면 특성 window (4초) */
    static double chainWindow(ServerPlayer sp, Jobs.Job j) {
        double base = trait(j, "window", 80);
        if (j == null || !AM.equals(j.id()) || !surgeOn(sp)) return base;
        Jobs.Skill k = j.skill(SURGE);
        return k == null ? base : Math.max(base, k.num("window", base * 2));
    }

    /**
     * 별 자리 (스킬을 쓴 때): 겨눈 적 (20칸) 의 몸 가운데, 없으면 조준한 곳 (14칸) 땅 위 1.6칸 — 별 i (0 ~ 4) 는 보는 쪽을 마주한 오각형 (반지름 1.4) 의 꼭짓점
     * (2i mod 5 — 위가 0) 으로 비킴: 같은 적이면 차례로 이은 선이 ★
     */
    static Vec3 starPoint(ServerPlayer sp, int i) {
        LivingEntity t = Kit.pickTarget(sp, 20, 12);
        Vec3 base;
        if (t != null) base = V.up(Kit.bodyPoint(t), 0.2);
        else {
            Vec3 a = Kit.aimPoint(sp, 14);
            base = new Vec3(a.x, RuneKit.groundAt(sp.level(), a, sp.getY()) + 1.6, a.z);
        }
        Vec3 r = V.rightOf(Kit.facingOf(sp));
        double ang = Math.toRadians(-90 + 72 * ((2 * i) % 5));
        double R = 1.4;
        return V.add(V.add(base, r, Math.cos(ang) * R), V.UP, -Math.sin(ang) * R);
    }

    /**
     * 시전이 나감 (JobTraitsMore.chainCommit — 아크메이지): 스펠 서지 동안 다른 스킬이면 스타 체인의 재사용 대기를 바로 돌려 놓음 (완드 끝 금빛 번쩍 · 맑은 소리),
     * 연쇄 1 ~ 5 면 별 하나 (연쇄가 늘었을 때만 — 같은 스킬을 이어 쓰면 그대로). 다섯째 별이면 별자리 완성 → true (연쇄를 처음부터)
     */
    static boolean commit(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, int chain) {
        if (j == null || !AM.equals(j.id())) return false;
        ServerLevel level = sp.level();
        if (surgeOn(sp) && !SURGE.equals(k.id()) && !CHAIN.equals(k.id()) && JobSkills.cooldownLeft(sp, CHAIN) > 0) {
            JobSkills.clearCooldown(sp, CHAIN);
            Vec3 tip = ArcmageSkills.wandTip(sp);
            JobFx.starNode(level, tip, 0.42, 6, 2, 0.3, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), NIGHT, GOLD);
            SkillFx.sparks(level, tip, GOLD, 8, 3);
            SkillFx.play(level, "block.amethyst_cluster.break", sp.position(), 1.6, 0.9);
        }
        List<Vec3> stars = STARS.computeIfAbsent(sp.getUUID(), u -> new ArrayList<>());
        if (chain <= 0) {
            stars.clear();
            return false;
        }
        if (stars.size() >= chain) return false;
        int max = Math.max(1, j.trait() == null ? 5 : j.trait().max());
        Vec3 p = starPoint(sp, stars.size());
        stars.add(p);
        // 별이 뜸: 별 번쩍 → 반짝임 · 앞 별에서 금빛 선이 이어짐 · 맑은 소리 (차례마다 한 음씩 높게)
        JobFx.starNode(level, p, 0.5, 0, 2, 0.25, false, Opt.empty(), NIGHT, GOLD);
        JobFx.starNode(level, p, 0.42, 2, 4, 0.45, true, Opt.of("fade", 0.3), NIGHT, GOLD);
        if (stars.size() >= 2) RuneKit.streak(level, stars.get(stars.size() - 2), p, GOLD, 0.07, 0.6, 0.75, 0);
        SkillFx.sparks(level, p, GOLD, 6, 3);
        SkillFx.play(level, "block.amethyst_block.chime", p, 1.0 + 0.15 * stars.size(), 1.0);
        if (stars.size() < max) {
            JobTraits.sendGauge(sp, false);
            return false;
        }
        List<Vec3> done = new ArrayList<>(stars);
        stars.clear();
        constellation(sp, j, done);
        return true;
    }

    /**
     * 별자리 완성: 별을 잇는 금빛 선 (닫힌 고리 — 바깥 밤하늘 자주 빛 · 가운데 금빛) · 별마다 크게 번쩍 → starDelay 틱 뒤 별마다 폭발 (둘레 starRadius 칸 — 위력 × star) ·
     * 선에 닿은 적 (lineWidth) 위력 × line. 한 적에게 한 번에 (별 수 + 선 — 숫자 여러 줄)
     */
    static void constellation(ServerPlayer sp, Jobs.Job j, List<Vec3> stars) {
        ServerLevel level = sp.level();
        int delay = (int) trait(j, "starDelay", 10);
        double r = trait(j, "starRadius", 3.0);
        double width = trait(j, "lineWidth", 1.2);
        int n = stars.size();
        for (int i = 0; i < n; i++) {
            Vec3 a = stars.get(i);
            Vec3 b = stars.get((i + 1) % n);
            RuneKit.streak(level, a, b, NIGHT, 0.32, delay / 20.0 + 0.3, 0.55, 0);
            RuneKit.streak(level, a, b, GOLD, 0.12, delay / 20.0 + 0.3, 1.0, 0);
            JobFx.starNode(level, a, 0.62, 2, 4, delay / 20.0, true, Opt.empty(), NIGHT, GOLD);
        }
        Vec3 sum = V.ZERO;
        for (Vec3 p : stars) sum = V.add(sum, p, 1.0 / n);
        Vec3 mid = sum;
        SkillFx.play(level, "block.beacon.activate", mid, 1.6, 1.0);
        SkillFx.play(level, "block.amethyst_block.resonate", mid, 1.2, 1.2);
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            for (Vec3 p : stars) {
                JobFx.starNode(d, p, 0.9, 6, 2, 0.35, false, Opt.empty(), NIGHT, GOLD);
                JobFx.burst(d, p, r * 0.6, j, 0.35, Opt.empty());
                SkillFx.sparks(d, p, GOLD, 10, 5);
            }
            SkillFx.play(d, "firework.large_blast", mid, 1.2, 1.2);
            SkillFx.play(d, "random.explode", mid, 1.4, 0.8);
            double P = JobSystem.power(sp, j);
            double star = trait(j, "star", 2.0);
            double line = trait(j, "line", 1.0);
            Map<UUID, LivingEntity> foes = new java.util.LinkedHashMap<>();
            Map<UUID, int[]> cnt = new HashMap<>();
            for (Vec3 p : stars) {
                for (LivingEntity e : Kit.around(sp, p, r, 3)) {
                    foes.put(e.getUUID(), e);
                    cnt.computeIfAbsent(e.getUUID(), u -> new int[2])[0]++;
                }
            }
            for (int i = 0; i < n; i++) {
                Vec3 a = stars.get(i);
                Vec3 b = stars.get((i + 1) % n);
                for (LivingEntity e : Kit.around(sp, V.lerp(a, b, 0.5), V.len(V.sub(b, a)) / 2 + width, 4)) {
                    if (segDist(Kit.bodyPoint(e), a, b) > width + Kit.bodyPad(e)) continue;
                    foes.put(e.getUUID(), e);
                    cnt.computeIfAbsent(e.getUUID(), u -> new int[2])[1] = 1;
                }
            }
            for (LivingEntity e : foes.values()) {
                int[] c = cnt.get(e.getUUID());
                double mult = star * c[0] + line * c[1];
                int split = Math.max(1, c[0] + c[1]);
                JobKit.strikeSplit(sp, e, j, P, mult, split, 2, true, false, life, 0, x -> {
                    if (RuneKit.fxQuota("jobStarBurst", 6)) JobFx.hit(e, j, 1.2);
                });
            }
        });
    }

    /** 점 p 에서 선분 a-b 까지 거리 */
    static double segDist(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = V.sub(b, a);
        double l2 = V.dot(ab, ab);
        double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, V.dot(V.sub(p, a), ab) / l2));
        return V.len(V.sub(p, V.add(a, ab, t)));
    }

    // ------------------------------------------------------------ 네크로맨서: 망자의 군대 · 무덤 · 표식

    /** JobKit 이 직업 스킬 피해를 넣기 바로 전 (네크로맨서면 망자가 노릴 적) */
    static void beforeJobHit(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        if (j == null || !NC.equals(j.id()) || e == null) return;
        FOCUS.put(sp.getUUID(), e.getUUID());
    }

    /** 피해를 넣은 뒤 (들어갔든 아니든 — 지금은 할 일 없음: JobKit 의 짝) */
    static void afterJobHitDone() {}

    /**
     * 표식 (ticks 틱 — 그 안에 무엇에든 쓰러지면 무덤 없이 바로 망자로): 그레이브 콜 (mist false) · 소울 미스트 (mist true — 망자가 먼저 노리고 crewBonus 더 셈). 같은
     * 적에 두 표식이면 늦게 끝나는 쪽 · 소울 미스트가 남음. 머리 위 녹색 해골 (tick 이 8틱마다 다시 그림)
     */
    static void mark(ServerPlayer sp, LivingEntity e, int ticks, boolean mist) {
        if (e == null || e.isRemoved()) return;
        Mark old = MARKS.get(e.getUUID());
        long until = Tasks.now() + ticks;
        boolean m = mist;
        if (old != null && old.owner().equals(sp.getUUID()) && old.until() >= Tasks.now()) {
            until = Math.max(until, old.until());
            m = mist || old.mist();
        }
        MARKS.put(e.getUUID(), new Mark(sp.getUUID(), until, m));
        if (e.level() instanceof ServerLevel level && RuneKit.fxQuota("jobNecroMark", 6)) {
            Jobs.Job j = JobSystem.job(sp);
            if (j != null) JobFx.skull(level, RuneKit.overHead(e, 0.45), m ? 0.3 : 0.22, 0, 4, 0.5, false, Opt.of("vel", RuneKit.entVel(e)), j);
        }
    }

    /** 이 적에 이 사람의 표식이 있는지 (시험) */
    public static boolean marked(ServerPlayer sp, Entity e) {
        Mark m = e == null ? null : MARKS.get(e.getUUID());
        return m != null && m.owner().equals(sp.getUUID()) && m.until() >= Tasks.now();
    }

    /** 이 적에 이 사람의 소울 미스트 표식이 있는지 */
    public static boolean mistMarked(ServerPlayer sp, Entity e) {
        Mark m = e == null ? null : MARKS.get(e.getUUID());
        return m != null && m.mist() && m.owner().equals(sp.getUUID()) && m.until() >= Tasks.now();
    }

    /** 망자가 가장 먼저 노릴 적: 이 사람의 소울 미스트 표식이 남은 적 (at 둘레 range 칸 — 없으면 null) */
    static LivingEntity mistFoe(ServerPlayer sp, Vec3 at, double range) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : Kit.around(sp, at, range, 4)) {
            if (!mistMarked(sp, e)) continue;
            double d = V.hdist(e.position(), at);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** 망자가 노릴 적 (주인이 마지막으로 친 적 — 없거나 쓰러졌으면 null) */
    static LivingEntity focus(ServerPlayer sp) {
        UUID id = FOCUS.get(sp.getUUID());
        if (id == null) return null;
        Entity e = sp.level().getEntity(id);
        return e instanceof LivingEntity le && le.isAlive() && !le.isRemoved() ? le : null;
    }

    /** 망자가 노릴 적으로 정함 (선장이 친 적 — 망자가 몰려듦) */
    static void setFocus(ServerPlayer sp, LivingEntity e) {
        if (e != null) FOCUS.put(sp.getUUID(), e.getUUID());
    }

    /** 무기로 맞힘: 네크로맨서 — 망자가 그 적을 노림 */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (victim == null || jobIf(sp, NC) == null) return;
        FOCUS.put(sp.getUUID(), victim.getUUID());
    }

    /** 그레이브야드 고리를 둠 (until 틱까지 — 안의 적은 망자가 먼저 노림) */
    static void addYard(ServerPlayer sp, Vec3 c, double r, long until) {
        YARDS.removeIf(y -> y.until() < Tasks.now());
        YARDS.add(new Yard(sp.getUUID(), sp.level(), c, r, until));
    }

    /** 이 적이 이 사람의 그레이브야드 고리 안에 있는지 */
    static boolean inYard(ServerPlayer sp, LivingEntity e) {
        long now = Tasks.now();
        for (Yard y : YARDS) {
            if (y.until() < now || !y.owner().equals(sp.getUUID()) || y.level() != e.level()) continue;
            if (V.hdist(e.position(), y.c()) <= y.r() + Kit.bodyPad(e)) return true;
        }
        return false;
    }

    /** 블라이트를 켬 (ticks 틱 — 주인 둘레 r 칸) */
    static void startBlight(ServerPlayer sp, double r, int ticks) {
        BLIGHTS.put(sp.getUUID(), new Blight(r, Tasks.now() + ticks));
    }

    /** 블라이트가 켜져 있는지 (시험) */
    public static boolean blightOn(ServerPlayer sp) {
        Blight b = BLIGHTS.get(sp.getUUID());
        return b != null && b.until() >= Tasks.now();
    }

    /** 시험용: 지금 망자 공격 배율 (네크로맨서가 아니면 0 — 표식 없는 적) */
    public static double crewMultOf(ServerPlayer sp) {
        Jobs.Job j = jobIf(sp, NC);
        return j == null ? 0 : crewMult(sp, j, null);
    }

    /** 시험용: 이 적에게 망자가 주는 배율 (소울 미스트 표식이면 + crewBonus — 네크로맨서가 아니면 0) */
    public static double crewMultOn(ServerPlayer sp, Entity e) {
        Jobs.Job j = jobIf(sp, NC);
        return j == null ? 0 : crewMult(sp, j, e instanceof LivingEntity le ? le : null);
    }

    /** 망자 공격 배율 (위력에 곱함): crewHit × {@link #mistFactor} */
    static double crewMult(ServerPlayer sp, Jobs.Job j, LivingEntity foe) {
        return trait(j, "crewHit", 0.4) * mistFactor(sp, j, foe);
    }

    /** 망자 · 선장이 이 적에게 주는 피해의 덤: 그 적이 이 사람의 소울 미스트 표식이면 1 + crewBonus, 아니면 1 */
    static double mistFactor(ServerPlayer sp, Jobs.Job j, LivingEntity foe) {
        if (foe == null || !mistMarked(sp, foe)) return 1;
        Jobs.Skill k = j.skill(MIST);
        return 1 + (k == null ? 0.3 : k.num("crewBonus", 0.3));
    }

    /**
     * 죽음 (AFTER_DEATH — 적대 몹만): 네크로맨서의 표식이 남음 · 블라이트 안 → 그 자리에서 바로 망자 (주인 — 네크로맨서일 때 · 무덤 없이). 아니면 둘레 graveRange 칸에
     * 네크로맨서가 있으면 그 자리에 무덤 (graveFor 틱 — 묘비가 솟음)
     */
    private static void onDeath(LivingEntity e, DamageSource src) {
        if (e == null || e instanceof Player || e.entityTags().contains("rpg_ally") || !(e.level() instanceof ServerLevel level)) return;
        Mark m = MARKS.remove(e.getUUID());
        if (m != null && m.until() >= Tasks.now()) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(m.owner());
            Jobs.Job j = owner == null ? null : jobIf(owner, NC);
            if (j != null && owner.level() == level && owner.isAlive()) {
                NecroCrew.raise(owner, j, e.position(), e.getYRot(), (int) trait(j, "crewFor", 400), NecroCrew.TRAIT);
                return;
            }
        }
        if (!Kit.hasFamily(e, "monster") && !Kit.hasFamily(e, "rpg_monster") && !Kit.hasFamily(e, "rpg_boss")) return;
        long now = Tasks.now();
        Jobs.Job any = null;
        for (ServerPlayer p : level.players()) {
            Jobs.Job pj = jobIf(p, NC);
            if (pj == null || !p.isAlive()) continue;
            Blight b = BLIGHTS.get(p.getUUID());
            if (b != null && b.until() >= now && V.hdist(p.position(), e.position()) <= b.r() + Kit.bodyPad(e) && Math.abs(p.getY() - e.getY()) <= 3) {
                NecroCrew.raise(p, pj, e.position(), e.getYRot(), (int) trait(pj, "crewFor", 400), NecroCrew.TRAIT);
                return;
            }
            if (any == null && p.distanceToSqr(e) <= Math.pow(trait(pj, "graveRange", 24), 2)) any = pj;
        }
        if (any == null) return;
        addGrave(level, e.position(), e.getYRot(), (int) trait(any, "graveFor", 200), any);
    }

    /** 무덤 하나 (묘비가 솟음 — 녹색 혼불) */
    static void addGrave(ServerLevel level, Vec3 at, float yaw, int ticks, Jobs.Job j) {
        GRAVES.removeIf(g -> g.until() < Tasks.now());
        if (GRAVES.size() > 48) GRAVES.remove(0);
        Vec3 g = new Vec3(at.x, RuneKit.groundAt(level, V.up(at, 0.6), at.y), at.z);
        GRAVES.add(new Grave(level, g, yaw, Tasks.now(), Tasks.now() + ticks));
        JobFx.grave(level, g, 0.62, 0, 2, 0.12, false, Opt.empty(), j);
        JobFx.soul(level, V.up(g, 0.5), 0.3, j, 0.5, Opt.of("dir", V.UP, "speed", 0.8));
        SkillFx.sparks(level, V.up(g, 0.3), NecroCrew.FIRE, 5, 2);
        SkillFx.play(level, "block.rooted_dirt.place", g, 0.7, 1.0);
    }

    /** at 둘레 radius 칸 안의 무덤 (가까운 순 — 가져가지 않음) */
    static List<Grave> gravesNear(ServerLevel level, Vec3 at, double radius) {
        long now = Tasks.now();
        List<Grave> out = new ArrayList<>();
        for (Grave g : GRAVES) {
            if (g.level() != level || g.until() < now) continue;
            if (V.hdist(g.at(), at) <= radius && Math.abs(g.at().y - at.y) <= 6) out.add(g);
        }
        out.sort(java.util.Comparator.comparingDouble(g -> V.hdist(g.at(), at)));
        return out;
    }

    /** 무덤을 씀 (망자가 일어남 — 목록에서 뺌) */
    static void takeGrave(Grave g) {
        GRAVES.remove(g);
    }

    /** 시험용: 무덤 하나를 둠 */
    public static void testGrave(ServerLevel level, Vec3 at, int ticks) {
        GRAVES.add(new Grave(level, at, 0F, Tasks.now(), Tasks.now() + ticks));
    }

    /** 시험용: 남은 무덤 수 */
    public static int graves() {
        long now = Tasks.now();
        int n = 0;
        for (Grave g : GRAVES) if (g.until() >= now) n++;
        return n;
    }

    /** 시험용: 무덤을 모두 지움 */
    public static void clearGraves() {
        GRAVES.clear();
    }

    // ------------------------------------------------------------ 틱 · 태세 · 게이지

    /**
     * 4틱마다 (JobTraits.tick — 사람마다): 아크메이지 — 8틱마다 지금 별 (반짝임 · 별을 잇는 옅은 금빛 선), 네크로맨서 — 16틱마다 둘레의 무덤 (묘비 · 녹색 혼불) ·
     * 8틱마다 표식 남은 적의 머리 위 해골
     */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        if (MARKS.size() > 128) MARKS.values().removeIf(m -> m.until() < now);
        if (AM.equals(j.id())) {
            if (now % 8 != 0) return;
            List<Vec3> stars = STARS.get(sp.getUUID());
            if (stars == null || stars.isEmpty()) return;
            if (JobTraitsMore.chain(sp) <= 0) {
                stars.clear();
                return;
            }
            ServerLevel level = sp.level();
            for (int i = 0; i < stars.size(); i++) {
                JobFx.starNode(level, stars.get(i), 0.42, 2, 4, 0.5, true, Opt.of("fade", 0.3), NIGHT, GOLD);
                if (i > 0) RuneKit.streak(level, stars.get(i - 1), stars.get(i), GOLD, 0.06, 0.45, 0.55, 0);
            }
            return;
        }
        if (!NC.equals(j.id())) return;
        ServerLevel level = sp.level();
        if (now % 8 == 0) {
            int n = 0;
            for (var en : MARKS.entrySet()) {
                Mark m = en.getValue();
                if (!m.owner().equals(sp.getUUID()) || m.until() < now || n >= 8) continue;
                Entity e = level.getEntity(en.getKey());
                if (!(e instanceof LivingEntity le) || !le.isAlive()) continue;
                n++;
                JobFx.skull(level, RuneKit.overHead(le, 0.45), m.mist() ? 0.3 : 0.22, 2, 2, 0.45, true, Opt.of("vel", RuneKit.entVel(le), "fade", 0.3), j);
            }
        }
        if (now % 16 == 0) {
            GRAVES.removeIf(g -> g.until() < now);
            int n = 0;
            for (Grave g : GRAVES) {
                if (g.level() != level || g.at().distanceToSqr(sp.position()) > 32 * 32 || n++ >= 10) continue;
                JobFx.grave(level, g.at(), 0.62, 2, 4, 0.85, true, Opt.of("fade", 0.15, "phase", (double) (n % 4)), j);
                JobFx.soul(level, V.up(g.at(), 0.95), 0.2, j, 0.85, Opt.of("alpha", 0.85));
            }
        }
    }

    /** 태세 연출 (8틱마다 — JobTraits.stanceFx): 스펠 서지 = 몸 둘레를 도는 금빛 별 둘 · 완드 끝 별빛. 그렸으면 true */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        if (!SURGE.equals(stance)) return false;
        ServerLevel level = sp.level();
        double a = Tasks.now() * 0.35;
        for (int i = 0; i < 2; i++) {
            double b = a + i * Math.PI;
            Vec3 p = new Vec3(sp.getX() + Math.cos(b) * 0.85, sp.getY() + 1.1 + 0.25 * Math.sin(b * 2), sp.getZ() + Math.sin(b) * 0.85);
            JobFx.starNode(level, p, 0.22, 2, 4, 0.45, true, JobFx.hide1p(sp).with("fade", 0.3, "vel", RuneKit.bodyVel(sp)), NIGHT, GOLD);
        }
        JobFx.starNode(level, ArcmageSkills.wandTip(sp), 0.16, 2, 4, 0.4, true, Opt.of("fade", 0.3, "vel", RuneKit.bodyVel(sp)), NIGHT, GOLD);
        return true;
    }

    /** 게이지 값: 네크로맨서 = 망자 선원 수 (선장은 빼고 — 최대 trait.max) */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        return j != null && NC.equals(j.id()) ? NecroCrew.count(sp) : 0;
    }
}
