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
 * 0.51.0 2차 직업 — 매지션 각성 오드레리르(아크메이지) · 반전 나글파르(네크로맨서) 특성 · 태세 (서버 — jobs.json advance):
 * <ul>
 * <li>아크메이지 「주문 연쇄」 (이어지는 것 · +12% 는 매지션과 같음 — JobTraitsMore.chainNext · chainCommit, 최대 trait.max = 5): 이은 스킬 (연쇄 1 ~ 5) 마다 그 자리 (겨눈 적 ·
 * 조준한 곳) 에 별 — 같은 적이면 오각형 꼭짓점을 하나 건너 (차례로 이으면 ★). 다섯째 별에 별자리 완성 — 별을 잇는 금빛 선 (닫힌 고리) → starDelay 틱 뒤 별마다 폭발
 * (위력 × star · 둘레 starRadius 칸) · 선에 닿은 적 (lineWidth) 위력 × line — 한 적에게 한 번에 (숫자 여러 줄) → 연쇄는 처음부터. 연쇄가 끊기면 별도 사라짐.
 * 꿀술의 노래 (태세): 그동안 직업 스킬을 쓸 때마다 그 자리에 별똥 (fall 틱 뒤 · 위력 × meteor · 둘레 meteorRadius 칸)</li>
 * <li>네크로맨서 「망자의 군대」 (1차와 거꾸로 — 이어 쓰는 대신 함께 싸움): 직업 스킬 (망자의 공격도 — 내 직업 스킬 피해) 로 쓰러뜨린 적 · 뼈 창이 꿴 뒤 markFor 틱 안에
 * (무엇에든) 쓰러진 적이 망자 선원으로 일어남 (NecroCrew — 최대 trait.max · crewFor 틱). 둘레에 네크로맨서가 있을 때 쓰러진 적대 몹의 자리는 corpseFor 틱 동안 시체
 * (녹색 혼불 — 시체 폭발이 터뜨림). 망자 일으키기 (태세): 망자 공격 + boost</li>
 * </ul>
 */
public final class JobTraits8 {
    private JobTraits8() {}

    static final String AM = "arcmage";
    static final String NC = "necromancer";
    static final String SONG = "arcmage_song";
    static final String RAISE = "necromancer_raise";
    /** 밤하늘 자주 (별자리 선의 바깥 빛 · 별 무리) */
    static final double[] NIGHT = {0.42, 0.26, 0.86};
    /** 금빛 별 (선 · 별 심지) */
    static final double[] GOLD = {1.0, 0.86, 0.42};

    // ------------------------------------------------------------ 상태

    /** 아크메이지: 지금 연쇄의 별 (스킬을 쓴 자리 — 차례대로) */
    private static final Map<UUID, List<Vec3>> STARS = new HashMap<>();
    /** 네크로맨서 뼈 창의 표식: 적 → {주인, 끝 틱} */
    private record Mark(UUID owner, long until) {}

    private static final Map<UUID, Mark> MARKS = new HashMap<>();
    /** 네크로맨서가 마지막으로 친 적 (망자가 먼저 노림) */
    private static final Map<UUID, UUID> FOCUS = new HashMap<>();
    /** 지금 들어가는 네크로맨서 직업 스킬 피해 (JobKit 이 피해를 넣는 동안만 — 그 피해로 쓰러지면 스킬로 쓰러뜨린 것: 죽음 이벤트가 피해 안에서 불림) */
    private static ServerPlayer pendingBy;
    private static LivingEntity pendingOn;

    /** 시체 (쓰러진 적대 몹의 자리 — 시체 폭발이 터뜨림) */
    record Corpse(ServerLevel level, Vec3 at, float yaw, long until) {}

    private static final List<Corpse> CORPSES = new ArrayList<>();

    static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register(JobTraits8::onDeath);
        RpgCombat.addWeaponHitHook(JobTraits8::onWeaponHit);
        NecroCrew.init();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STARS.clear();
            MARKS.clear();
            FOCUS.clear();
            CORPSES.clear();
            pendingBy = null;
            pendingOn = null;
        });
    }

    static Jobs.Job jobIf(ServerPlayer sp, String id) {
        Jobs.Job j = JobSystem.job(sp);
        return j != null && id.equals(j.id()) ? j : null;
    }

    static double trait(Jobs.Job j, String key, double fallback) {
        return j == null || j.trait() == null ? fallback : j.trait().def().numOr(key, fallback);
    }

    static void reset(ServerPlayer sp) {
        STARS.remove(sp.getUUID());
        FOCUS.remove(sp.getUUID());
        NecroCrew.disband(sp.getUUID(), true);
        UUID id = sp.getUUID();
        MARKS.values().removeIf(m -> m.owner().equals(id));
    }

    static void forget(UUID id) {
        STARS.remove(id);
        FOCUS.remove(id);
        NecroCrew.disband(id, false);
        MARKS.values().removeIf(m -> m.owner().equals(id));
    }

    static void lifeEnded(UUID id) {
        STARS.remove(id);
        FOCUS.remove(id);
        NecroCrew.disband(id, true);
        MARKS.values().removeIf(m -> m.owner().equals(id));
    }

    // ------------------------------------------------------------ 아크메이지: 별 · 별자리

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
     * 시전이 나감 (JobTraitsMore.chainCommit — 아크메이지): 꿀술의 노래면 그 자리에 별똥, 연쇄 1 ~ 5 면 별 하나 (연쇄가 늘었을 때만 — 같은 스킬을 이어 쓰면 그대로).
     * 다섯째 별이면 별자리 완성 → true (연쇄를 처음부터)
     */
    static boolean commit(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, int chain) {
        if (j == null || !AM.equals(j.id())) return false;
        ServerLevel level = sp.level();
        // 꿀술의 노래: 그 자리에 별똥 (노래를 시작한 스킬은 빼고)
        if (JobTraits.stanceOn(sp, SONG) && !SONG.equals(k.id())) meteor(sp, j, starPoint(sp, 0));
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
            Kit.shakeNear(d, mid, 12, 0.25, 0.3);
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

    /**
     * 꿀술의 노래의 별똥: 하늘 (뒤 위 9칸) 에서 금빛 별똥이 at 아래 땅으로 fall 틱 동안 떨어짐 → 둘레 meteorRadius 칸 (위력 × meteor — 강화) · 작은 별 터짐 · 금빛 고리
     */
    static void meteor(ServerPlayer sp, Jobs.Job j, Vec3 at) {
        Jobs.Skill k = j.skill(SONG);
        if (k == null) return;
        ServerLevel level = sp.level();
        int fall = Math.max(4, (int) k.num("fall", 12));
        double r = k.num("meteorRadius", 2.5);
        double mult = JobUpgrades.num(sp, k, "meteor", 2.4);
        Vec3 ground = new Vec3(at.x, RuneKit.groundAt(level, at, at.y - 1.6), at.z);
        Vec3 f = Kit.facingOf(sp);
        Vec3 from = V.up(V.add(ground, f, -4.5), 9);
        Vec3 to = V.up(ground, 0.3);
        double speed = V.len(V.sub(to, from)) / (fall / 20.0);
        JobFx.fallStar(level, from, to, speed, 1.8, Opt.empty(), NIGHT, GOLD); // 영상 1 뒤 0.9 → 1.8 (가는 줄로만 보였음)
        SkillFx.play(level, "entity.firework_rocket.launch", from, 0.7, 0.8);
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(fall, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            JobFx.starNode(d, V.up(ground, 0.8), 1.4, 6, 2, 0.32, false, Opt.empty(), NIGHT, GOLD);
            JobFx.burst(d, V.up(ground, 0.6), r * 0.55, j, 0.32, Opt.empty());
            JobFx.ring(d, V.up(ground, 0.06), r, j, false, 0.35, Opt.empty());
            SkillFx.shock(d, V.up(ground, 0.1), GOLD, r / 1.8, 0.35);
            SkillFx.sparks(d, V.up(ground, 0.5), GOLD, 14, 6);
            SkillFx.play(d, "firework.blast", ground, 1.0, 1.0);
            SkillFx.play(d, "random.explode", ground, 1.6, 0.5);
            double P = JobSystem.power(sp, j);
            for (LivingEntity e : Kit.around(sp, ground, r, 3)) {
                JobKit.strike(sp, e, j, P, mult, true, false, life, 0, x -> {
                    if (RuneKit.fxQuota("jobMeteor", 6)) JobFx.hit(e, j, 1.0);
                });
            }
        });
    }

    // ------------------------------------------------------------ 네크로맨서: 망자의 군대

    /** JobKit 이 직업 스킬 피해를 넣기 바로 전 (네크로맨서면 그 피해로 쓰러지면 스킬로 쓰러뜨린 것 · 망자가 노릴 적) */
    static void beforeJobHit(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        if (j == null || !NC.equals(j.id()) || e == null) return;
        pendingBy = sp;
        pendingOn = e;
        FOCUS.put(sp.getUUID(), e.getUUID());
    }

    /** 피해를 넣은 뒤 (들어갔든 아니든) */
    static void afterJobHitDone() {
        pendingBy = null;
        pendingOn = null;
    }

    /** 뼈 창의 표식 (ticks 틱 — 그 안에 무엇에든 쓰러지면 망자로) · 머리 위 작은 녹색 해골 */
    static void markSpear(ServerPlayer sp, LivingEntity e, Jobs.Job j, int ticks) {
        if (e == null || e.isRemoved()) return;
        MARKS.put(e.getUUID(), new Mark(sp.getUUID(), Tasks.now() + ticks));
        if (e.level() instanceof ServerLevel level && RuneKit.fxQuota("jobNecroMark", 6)) {
            JobFx.skull(level, RuneKit.overHead(e, 0.45), 0.2, 0, 4, 0.9, false, Opt.of("vel", RuneKit.entVel(e)), j);
        }
    }

    /** 이 적에 뼈 창 표식이 있는지 (시험) */
    public static boolean marked(ServerPlayer sp, Entity e) {
        Mark m = e == null ? null : MARKS.get(e.getUUID());
        return m != null && m.owner().equals(sp.getUUID()) && m.until() >= Tasks.now();
    }

    /** 망자가 먼저 노릴 적 (주인이 마지막으로 친 적 — 없거나 쓰러졌으면 null) */
    static LivingEntity focus(ServerPlayer sp) {
        UUID id = FOCUS.get(sp.getUUID());
        if (id == null) return null;
        Entity e = sp.level().getEntity(id);
        return e instanceof LivingEntity le && le.isAlive() && !le.isRemoved() ? le : null;
    }

    /** 무기로 맞힘: 네크로맨서 — 망자가 그 적을 먼저 노림 */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (victim == null || jobIf(sp, NC) == null) return;
        FOCUS.put(sp.getUUID(), victim.getUUID());
    }

    /** 시험용: 지금 망자 공격 배율 (네크로맨서가 아니면 0) */
    public static double crewMultOf(ServerPlayer sp) {
        Jobs.Job j = jobIf(sp, NC);
        return j == null ? 0 : crewMult(sp, j);
    }

    /** 망자 공격 배율 (위력에 곱함): crewHit × (망자 일으키기 동안 1 + boost — 강화) */
    static double crewMult(ServerPlayer sp, Jobs.Job j) {
        double m = trait(j, "crewHit", 0.4);
        if (JobTraits.stanceOn(sp, RAISE)) {
            Jobs.Skill k = j.skill(RAISE);
            m *= 1 + (k == null ? 0.3 : JobUpgrades.num(sp, k, "boost", 0.3));
        }
        return m;
    }

    /**
     * 죽음 (AFTER_DEATH): 네크로맨서의 직업 스킬 피해 (망자의 공격 포함) 로 쓰러짐 · 뼈 창 표식 안에 쓰러짐 → 그 자리에서 망자가 일어남 (주인 — 네크로맨서일 때). 아니면
     * 둘레 24칸에 네크로맨서가 있으면 그 자리가 시체 (적대 몹만)
     */
    private static void onDeath(LivingEntity e, DamageSource src) {
        if (e == null || e instanceof Player || e.entityTags().contains("rpg_ally") || !(e.level() instanceof ServerLevel level)) return;
        ServerPlayer owner = null;
        if (pendingOn == e && pendingBy != null) owner = pendingBy;
        Mark m = MARKS.remove(e.getUUID());
        if (owner == null && m != null && m.until() >= Tasks.now()) owner = level.getServer().getPlayerList().getPlayer(m.owner());
        Jobs.Job j = owner == null ? null : jobIf(owner, NC);
        if (j != null && owner.level() == level && owner.isAlive()) {
            NecroCrew.raise(owner, j, e.position(), e.getYRot(), (int) trait(j, "crewFor", 400), NecroCrew.TRAIT);
            return;
        }
        if (!Kit.hasFamily(e, "monster") && !Kit.hasFamily(e, "rpg_monster") && !Kit.hasFamily(e, "rpg_boss")) return;
        Jobs.Job any = null;
        for (ServerPlayer p : level.players()) {
            Jobs.Job pj = jobIf(p, NC);
            if (pj != null && p.distanceToSqr(e) <= 24 * 24) {
                any = pj;
                break;
            }
        }
        if (any == null) return;
        if (CORPSES.size() > 48) CORPSES.remove(0);
        CORPSES.add(new Corpse(level, e.position(), e.getYRot(), Tasks.now() + (long) trait(any, "corpseFor", 200)));
    }

    /** at 둘레 radius 칸 안의 가장 가까운 시체를 가져감 (없으면 null) */
    static Corpse takeCorpse(ServerLevel level, Vec3 at, double radius) {
        long now = Tasks.now();
        Corpse best = null;
        double bd = Double.MAX_VALUE;
        for (Corpse c : CORPSES) {
            if (c.level() != level || c.until() < now) continue;
            double d = V.hdist(c.at(), at);
            if (d <= radius && Math.abs(c.at().y - at.y) <= 4 && d < bd) {
                bd = d;
                best = c;
            }
        }
        if (best != null) CORPSES.remove(best);
        return best;
    }

    /** 시험용: 시체 하나를 둠 */
    public static void testCorpse(ServerLevel level, Vec3 at, int ticks) {
        CORPSES.add(new Corpse(level, at, 0F, Tasks.now() + ticks));
    }

    /** 시험용: 남은 시체 수 */
    public static int corpses() {
        long now = Tasks.now();
        int n = 0;
        for (Corpse c : CORPSES) if (c.until() >= now) n++;
        return n;
    }

    // ------------------------------------------------------------ 틱 · 태세 · 게이지

    /**
     * 4틱마다 (JobTraits.tick — 사람마다): 아크메이지 — 8틱마다 지금 별 (반짝임 · 별을 잇는 옅은 금빛 선), 네크로맨서 — 16틱마다 둘레의 시체에 녹색 혼불
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
        if (NC.equals(j.id()) && now % 16 == 0) {
            CORPSES.removeIf(c -> c.until() < now);
            ServerLevel level = sp.level();
            int n = 0;
            for (Corpse c : CORPSES) {
                if (c.level() != level || c.at().distanceToSqr(sp.position()) > 32 * 32 || n++ >= 8) continue;
                JobFx.soul(level, V.up(c.at(), 0.45), 0.24, j, 0.85, Opt.of("alpha", 0.85));
            }
        }
    }

    /** 태세 연출 (8틱마다 — JobTraits.stanceFx): 꿀술의 노래 = 머리 위로 떠오르는 금빛 음표 · 몸 둘레 금빛 반짝임 / 망자 일으키기 = 발밑 녹색 혼불 · 해골 연기. 그렸으면 true */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        ServerLevel level = sp.level();
        if (SONG.equals(stance)) {
            double a = Tasks.now() * 0.3;
            Vec3 p = V.up(sp.position(), 1.9);
            JobFx.note(level, new Vec3(p.x + Math.cos(a) * 0.7, p.y, p.z + Math.sin(a) * 0.7), (int) (Tasks.now() / 8 % 4), 0.22, j, 0.7,
                    JobFx.hide1p(sp).with("dir", V.UP, "speed", 0.9, "vel", RuneKit.bodyVel(sp)));
            RuneKit.rfx(level, "prop", V.up(sp.position(), 1.0 + Kit.rand() * 0.8), JobFx.hide1p(sp).with("size", 0.13, "life", 0.45, "cell",
                    (double) RuneKit.RFX.get("glint")[0], "spin", 0.0, "spinRate", 180.0, "color", GOLD, "fade", 0.4, "vel", RuneKit.bodyVel(sp)));
            return true;
        }
        if (RAISE.equals(stance)) {
            double a = Tasks.now() * 0.25;
            for (int i = 0; i < 2; i++) {
                double b = a + i * Math.PI;
                JobFx.soul(level, new Vec3(sp.getX() + Math.cos(b) * 0.9, sp.getY() + 0.2, sp.getZ() + Math.sin(b) * 0.9), 0.2, j, 0.45,
                        JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
            }
            return true;
        }
        return false;
    }

    /** 게이지 값: 네크로맨서 = 망자 선원 수 (궁극기의 선원은 빼고 — 최대 trait.max) */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        return j != null && NC.equals(j.id()) ? NecroCrew.count(sp) : 0;
    }
}
