package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.mob.TyrfingWarrior;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 네크로맨서 「망자의 군대」 의 망자 선원 (진짜 몹 rpg:naglfar_crew — 티르빙의 전사와 같은 몸 · 독초록 혼불 · 뼈색): 스스로 생각하지 않고 이 틀이 틱마다
 * 움직임 (티르빙 망자의 각성과 같은 틀).
 * <ul>
 * <li>일어남: 쓰러진 적 자리 · 무덤 (그 땅) 아래에서 RISE 틱 동안 솟음 (녹색 무덤불 · 갈라진 땅) — 특성의 망자 (TRAIT) 는 주인마다 최대 trait.max (넘으면 가장 오래된 것이
 * 무너짐). 재설계안 2판 (2026-11-06) 팬텀 갤리온의 망자 선장 (CAPTAIN — 같은 몸 × captainScale) 은 따로 (스킬이 배에서 뛰어내리게 움직이다 놓아 줌 — captainFor 틱)</li>
 * <li>싸움: 노릴 적 = 주인의 소울 미스트 표식이 남은 적 → 그레이브야드 고리 안의 적 → 주인 (또는 선장) 이 마지막으로 친 적 → 둘레 crewRange 칸의 적대 몹 중 가까운 적
 * (다른 망자가 노리는 적은 1.5칸 멀게 — 둘러쌈). 달려가 crewEvery 틱마다 도끼로 내려침 (공격 동작 STRIKE_AT 틱 뒤 — 위력 × crewHit (소울 미스트 표식이면 + crewBonus) ·
 * 주인의 직업 스킬 피해 (JobKit — 숫자 · 처치 · 다시 망자는 주인의 것)). 선장은 captainEvery 틱마다 둘레 CLEAVE 칸을 휩쓸어 내려침 (위력 × captainHit · 친 적을 망자가
 * 노림). 적이 없으면 주인 뒤를 따름 (24칸 넘게 떨어지면 곁으로 옮겨 옴)</li>
 * <li>끝: crewFor 틱이 지나거나 주인이 죽음 · 차원을 옮김 · 직업이 바뀜 · 나감 → 녹색 불길 · 뼈 조각으로 무너짐 (몹은 저장하지 않음 — TyrfingWarrior)</li>
 * </ul>
 */
public final class NecroCrew {
    private NecroCrew() {}

    static final int TRAIT = 0;
    /** 팬텀 갤리온의 망자 선장 (크게 — 스킬이 내려놓은 뒤 이 틀이 움직임) */
    static final int CAPTAIN = 2;
    /** 선장이 휩쓰는 둘레 (도끼 끝 둘레 칸 — 크기 1 기준, 선장 크기만큼 늘어남) */
    static final double CLEAVE = 1.25;
    /** 솟는 틱 · 깊이 (키 2.4칸의 드라우그가 땅 밑에 다 묻혀 있다가 솟음) */
    static final int RISE = 14;
    static final double DEPTH = 2.6;
    /** 적 몸 겉에서 이만큼 떨어져 섬 (도끼가 닿는 거리) · 달려가는 빠르기 (티르빙의 전사와 같음) */
    static final double REACH = 0.9;
    static final double RUN = 1.25;
    /** 무덤불 (rpg:tyr_ghostfire) · 갈라진 땅 (rpg:ground_crack) 의 독초록 */
    static final double[] FIRE = {0.36, 1.0, 0.5};
    static final double[] DEEP = {0.08, 0.32, 0.14};

    /** 망자 하나 */
    static final class Member {
        final TyrfingWarrior x;
        final int kind;
        final long born;
        long until;
        final Vec3 ground;
        final int slot;
        /** 스킬이 움직이는 동안 (궁극기의 선원 — 뛰어내리는 중) */
        boolean scripted;
        LivingEntity foe;
        long nextSwing;
        long strikeAt = -1;
        LivingEntity strikeFoe;
        long pathAt = -99;
        /** 다 솟았는지 (물리를 켬 — 틱이 밀려도 한 번) */
        boolean risen;
        /** 크기 (선장 captainScale — 닿는 거리 · 휩쓰는 둘레가 함께 늚) · 내려치는 틱 사이 · 공격 배율 (0 = 특성 crewEvery · crewHit) */
        double scale = 1;
        int every;
        double hit;

        Member(TyrfingWarrior x, int kind, long born, long until, Vec3 ground, int slot) {
            this.x = x;
            this.kind = kind;
            this.born = born;
            this.until = until;
            this.ground = ground;
            this.slot = slot;
        }

        /** 아직 땅에서 솟는 중 (다 솟은 것으로 한 망자 — testRaise · 궁극기의 선원 — 는 아님) */
        boolean rising(long now) {
            return !risen && now - born < RISE;
        }
    }

    private static final Map<UUID, List<Member>> CREW = new HashMap<>();
    /** 시험: 이 주인들의 망자는 치지 않음 (시간 시험 · 스킬 시험이 스킬의 타격만 재게 — 몸은 그대로 움직임. 시험이 함께 돌아도 서로 섞이지 않게 주인마다) */
    public static final java.util.Set<UUID> PASSIVE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(NecroCrew::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CREW.clear());
    }

    /** 이 사람의 망자 (스냅샷) */
    static List<Member> members(ServerPlayer sp) {
        List<Member> l = CREW.get(sp.getUUID());
        return l == null ? List.of() : new ArrayList<>(l);
    }

    /** 특성의 망자 수 (선장은 빼고 — 게이지) */
    public static int count(ServerPlayer sp) {
        int n = 0;
        for (Member m : members(sp)) if (m.kind == TRAIT && !m.x.isRemoved()) n++;
        return n;
    }

    /** 시험: 모든 망자 수 (선장 포함) */
    public static int countAll(ServerPlayer sp) {
        int n = 0;
        for (Member m : members(sp)) if (!m.x.isRemoved()) n++;
        return n;
    }

    /** 시험: 망자 몸들 */
    public static List<TyrfingWarrior> bodies(ServerPlayer sp) {
        List<TyrfingWarrior> out = new ArrayList<>();
        for (Member m : members(sp)) if (!m.x.isRemoved()) out.add(m.x);
        return out;
    }

    /** 시험 (실패 글): 망자마다 종류 · 자리 · 다 솟았는지 · 노리는 적 (자리 · 거리) · 다음 내려치기 · 도끼가 닿을 틱 */
    public static String testState(ServerPlayer sp) {
        StringBuilder out = new StringBuilder();
        long now = Tasks.now();
        for (Member m : members(sp)) {
            Vec3 p = m.x.position();
            out.append(String.format(java.util.Locale.ROOT, "[%s %.2f,%.2f,%.2f risen=%s scripted=%s", m.kind == TRAIT ? "trait" : "captain", p.x, p.y, p.z, m.risen,
                    m.scripted));
            if (m.foe != null) {
                Vec3 f = m.foe.position();
                out.append(String.format(java.util.Locale.ROOT, " foe=%s@%.2f,%.2f,%.2f d=%.2f", Kit.typeId(m.foe),
                        f.x, f.y, f.z, V.hdist(p, f)));
            } else out.append(" foe=none");
            out.append(" swingIn=").append(m.nextSwing - now).append(" strikeAt=").append(m.strikeAt < 0 ? -1 : m.strikeAt - now)
                    .append(" nav=").append(m.x.getNavigation().isInProgress()).append(" passive=").append(PASSIVE.contains(sp.getUUID())).append("]");
        }
        return out.length() == 0 ? "(no crew)" : out.toString();
    }

    /** 시험: 망자 하나를 at 에 바로 세움 (솟는 동안도 지남 — 다 솟은 것으로) */
    public static TyrfingWarrior testRaise(ServerPlayer sp, Vec3 at) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null) return null;
        Member m = raise(sp, j, at, 0F, (int) JobTraits8.trait(j, "crewFor", 400), TRAIT);
        if (m == null) return null;
        settle(m, m.ground);
        m.risen = true;
        return m.x;
    }

    /** 시험: 이 사람의 망자 · 선장을 모두 조용히 치움 */
    public static void testClear(ServerPlayer sp) {
        disband(sp.getUUID(), false);
    }

    /** 다 솟은 것으로 (물리를 켜고 땅 위에 섬) */
    static void settle(Member m, Vec3 at) {
        m.x.noPhysics = false;
        m.x.setNoGravity(false);
        m.x.setPos(at.x, at.y, at.z);
        m.x.setOnGround(true);
        m.x.setDeltaMovement(0, -0.1, 0);
    }

    /** 모두 무너뜨림 (주인이 죽음 · 직업이 바뀜 · 나감) */
    static void disband(UUID owner, boolean fx) {
        List<Member> l = CREW.remove(owner);
        if (l == null) return;
        for (Member m : l) crumble(m, fx);
    }

    /**
     * 망자 하나가 at (그 땅) 아래에서 솟음: kind TRAIT 이면 최대 trait.max (넘으면 가장 오래된 것이 무너짐). 몸이 들어갈 자리가 없으면 둘레 1 ~ 1.5칸에서 찾음. 일어난 망자
     * (못 세우면 null)
     */
    static Member raise(ServerPlayer sp, Jobs.Job j, Vec3 at, float yaw, int life, int kind) {
        ServerLevel level = sp.level();
        long now = Tasks.now();
        Vec3 ground = standAt(level, at);
        List<Member> list = CREW.computeIfAbsent(sp.getUUID(), k -> new ArrayList<>());
        list.removeIf(m -> m.x.isRemoved());
        if (kind == TRAIT) {
            int max = Math.max(1, j.trait() == null ? 4 : j.trait().max());
            List<Member> army = new ArrayList<>();
            for (Member m : list) if (m.kind == TRAIT) army.add(m);
            army.sort(java.util.Comparator.comparingLong(m -> m.born));
            while (army.size() >= max) {
                Member old = army.remove(0);
                list.remove(old);
                crumble(old, true);
            }
        }
        TyrfingWarrior x = TyrfingWarrior.spawnAs(TyrfingWarrior.CREW_ID, level, V.up(ground, -DEPTH), yaw, life + RISE + 60);
        if (x == null) return null;
        x.noPhysics = true;
        x.setNoGravity(true);
        int slot = 0;
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Member m : list) used.add(m.slot);
        while (used.contains(slot)) slot++;
        Member m = new Member(x, kind, now, now + RISE + life, ground, slot);
        list.add(m);
        riseFx(level, ground, j);
        JobTraits.sendGauge(sp, false);
        return m;
    }

    /**
     * 팬텀 갤리온의 망자 선장 (스킬이 움직임 — 갑판에서 뛰어내리는 중, 물리 꺼짐): at 에 바로 섬 · 크기 scale (몸 · 도끼가 닿는 거리 · 휩쓰는 둘레) · every 틱마다 위력 × hit.
     * 한 사람에 하나 (전 선장은 무너짐). 못 세우면 null
     */
    static Member captainAt(ServerPlayer sp, Vec3 at, float yaw, int life, double scale, int every, double hit) {
        ServerLevel level = sp.level();
        List<Member> list = CREW.computeIfAbsent(sp.getUUID(), k -> new ArrayList<>());
        for (Member old : new ArrayList<>(list)) {
            if (old.kind != CAPTAIN) continue;
            list.remove(old);
            crumble(old, true);
        }
        TyrfingWarrior x = TyrfingWarrior.spawnAs(TyrfingWarrior.CREW_ID, level, at, yaw, life + 120);
        if (x == null) return null;
        var sc = x.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        if (sc != null) sc.setBaseValue(scale);
        x.refreshDimensions();
        x.noPhysics = true;
        x.setNoGravity(true);
        int slot = 0;
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Member m : list) used.add(m.slot);
        while (used.contains(slot)) slot++;
        Member m = new Member(x, CAPTAIN, Tasks.now() - RISE, Tasks.now() + life, at, slot);
        m.scripted = true;
        m.scale = scale;
        m.every = Math.max(10, every);
        m.hit = hit;
        list.add(m);
        return m;
    }

    /** 시험: 이 사람의 선장 (없으면 null) */
    public static TyrfingWarrior captain(ServerPlayer sp) {
        for (Member m : members(sp)) if (m.kind == CAPTAIN && !m.x.isRemoved()) return m.x;
        return null;
    }

    /** 스킬이 놓아 줌 (땅에 섬 — 이제 이 틀이 움직임): stay 틱 더 */
    static void release(Member m, Vec3 ground, int stay) {
        if (m.x.isRemoved()) return;
        settle(m, ground);
        m.risen = true;
        m.scripted = false;
        m.until = Tasks.now() + stay;
        m.nextSwing = Tasks.now() + (m.kind == CAPTAIN ? 12 : 8 + m.slot * 3);
    }

    /** 망자가 설 땅: at 아래 (6칸 안) 의 단단한 땅 — 몸 (폭 0.7 · 키 2.3) 이 들어가지 않으면 둘레 1 ~ 1.5칸에서 */
    static Vec3 standAt(ServerLevel level, Vec3 at) {
        for (double r : new double[] {0, 1.0, 1.5}) {
            for (int k = 0; k < (r == 0 ? 1 : 8); k++) {
                double a = k * Math.PI / 4;
                Vec3 p = new Vec3(at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r);
                Double gy = RuneKit.solidGround(level, V.up(p, 1.5), 6);
                if (gy == null) continue;
                if (level.noBlockCollision(null, new AABB(p.x - 0.35, gy + 0.01, p.z - 0.35, p.x + 0.35, gy + 2.3, p.z + 0.35))) return new Vec3(p.x, gy, p.z);
            }
        }
        return at;
    }

    /** 솟음: 녹색 무덤불 · 갈라진 땅 · 뼈 손 · 녹색 불티 · 소리 */
    static void riseFx(ServerLevel level, Vec3 ground, Jobs.Job j) {
        SkillFx.fx(level, "rpg:tyr_ghostfire", ground, Opt.of("size", 1.7, "life", 0.85, "color", FIRE, "alpha", 0.95));
        SkillFx.fx(level, "rpg:ground_crack", V.up(ground, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", 1.4, "life", 1.2, "color", DEEP,
                "alpha", 0.9));
        JobFx.hand(level, ground, 0.32, j, 0.6, Opt.empty());
        SkillFx.sparks(level, V.up(ground, 0.4), FIRE, 6, 3);
        SkillFx.play(level, "particle.soul_escape", ground, 0.8, 1.0);
        SkillFx.play(level, "entity.zombie.infect", ground, 0.7, 0.6);
    }

    /** 무너짐: 녹색 불길이 솟고 뼈 조각이 흩어짐 */
    static void crumble(Member m, boolean fx) {
        TyrfingWarrior x = m.x;
        if (x.isRemoved()) return;
        if (fx && x.level() instanceof ServerLevel level) {
            Vec3 at = x.position();
            double k = m.scale;
            SkillFx.fx(level, "rpg:tyr_ghostfire", at, Opt.of("size", 2.0 * k, "life", 0.6 + 0.15 * (k - 1), "color", FIRE, "alpha", 0.9));
            SkillFx.sparks(level, V.up(at, 1.0 * k), new double[] {0.92, 0.88, 0.74}, (int) (8 * k), 4 * k);
            SkillFx.play(level, "entity.skeleton.death", at, 0.7 / Math.sqrt(k), 0.6 + 0.2 * (k - 1));
        }
        x.discard();
    }

    // ------------------------------------------------------------ 틱

    private static void tick(MinecraftServer server) {
        if (CREW.isEmpty()) return;
        long now = Tasks.now();
        for (UUID id : new ArrayList<>(CREW.keySet())) {
            List<Member> list = CREW.get(id);
            if (list == null) continue;
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            Jobs.Job j = sp == null ? null : JobSystem.job(sp);
            if (sp == null || !sp.isAlive() || sp.isRemoved() || j == null || !JobTraits8.NC.equals(j.id())) {
                disband(id, sp != null);
                continue;
            }
            boolean changed = false;
            for (Member m : new ArrayList<>(list)) {
                if (m.x.isRemoved()) {
                    list.remove(m);
                    changed = true;
                } else if (m.x.level() != sp.level()) {
                    list.remove(m);
                    m.x.discard();
                    changed = true;
                } else if (now >= m.until && !m.scripted) {
                    list.remove(m);
                    crumble(m, true);
                    changed = true;
                }
            }
            if (changed) JobTraits.sendGauge(sp, false);
            if (list.isEmpty()) {
                CREW.remove(id);
                continue;
            }
            for (Member m : new ArrayList<>(list)) drive(sp, j, m, list, now);
        }
    }

    /** 망자 하나 한 틱: 솟음 → 노릴 적에게 달려가 도끼로 내려침 (적이 없으면 주인을 따름) */
    private static void drive(ServerPlayer sp, Jobs.Job j, Member m, List<Member> all, long now) {
        TyrfingWarrior x = m.x;
        if (x.isRemoved()) return;
        ServerLevel level = sp.level();
        long age = now - m.born;
        if (m.kind == TRAIT && !m.risen) {
            if (age < RISE) {
                double q = Math.max(0, age / (double) RISE);
                double up = 1 - (1 - q) * (1 - q);
                x.setPos(m.ground.x, m.ground.y - DEPTH * (1 - up), m.ground.z);
                return;
            }
            settle(m, m.ground);
            m.risen = true;
            m.nextSwing = now + 4 + m.slot * 3;
        }
        if (m.scripted) return;
        if (m.strikeAt >= 0 && now >= m.strikeAt) {
            m.strikeAt = -1;
            strike(sp, j, m);
        }
        // 머리의 녹색 혼불 (12틱마다 — 망자마다 엇갈려)
        if ((now + m.slot * 4) % 12 == 0 && RuneKit.fxQuota("necroCrew", 6)) {
            JobFx.soul(level, V.up(x.position(), 2.35 * m.scale), 0.15 * m.scale, j, 0.5, Opt.of("alpha", 0.85));
        }
        double range = JobTraits8.trait(j, "crewRange", 10);
        LivingEntity e = m.foe;
        boolean lost = e == null || e.isRemoved() || !e.isAlive() || e.level() != level || !Kit.canHit(sp, e) || V.hdist(e.position(), x.position()) > range + 6;
        if (lost || (now + m.slot) % 10 == 0) {
            e = pickFoe(sp, m, all, range);
            m.foe = e;
        }
        if (e == null) {
            follow(sp, m, now);
            return;
        }
        Vec3 xp = x.position();
        Vec3 ep = e.position();
        double pad = Kit.bodyPad(e);
        double reach = REACH * m.scale;
        double gap = V.hdist(xp, ep) - pad;
        x.getLookControl().setLookAt(e);
        Vec3 away = V.sub(xp, ep);
        Vec3 dir = Math.hypot(away.x, away.z) > 1e-3 ? V.flat(away) : V.rightOf(Kit.facingOf(sp));
        Vec3 spot = V.add(ep, dir, pad + reach);
        double toSpot = V.hdist(xp, spot);
        float yaw = (float) Math.toDegrees(Math.atan2(-(ep.x - xp.x), ep.z - xp.z));
        if (toSpot > 1.2) {
            // 길 찾기 (4틱마다 · 못 찾으면 2틱 뒤)
            if (now - m.pathAt >= 4) m.pathAt = x.getNavigation().moveTo(spot.x, spot.y, spot.z, RUN) ? now : now - 2;
            if ((now + m.slot) % 4 == 0 && x.onGround() && x.getDeltaMovement().horizontalDistance() > 0.1 && RuneKit.fxQuota("necroStep", 6)) {
                SkillFx.fx(level, "rpg:tyr_ghostfire", xp, Opt.of("size", 0.55, "life", 0.3, "color", FIRE, "alpha", 0.6));
            }
        } else if (toSpot > 0.3) {
            // 한두 칸: 길 찾기는 이미 닿은 것으로 보고 서 버림 (같은 칸 · 옆 칸 — CI 41372463899 에서 망자가 1.87칸에 멈춰 내려치지 않았음) → 곧장 걸어감
            x.getNavigation().stop();
            x.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, RUN);
        } else {
            x.getNavigation().stop();
            x.setYRot(yaw);
            x.setYBodyRot(yaw);
            x.setYHeadRot(yaw);
        }
        if (now >= m.nextSwing && m.strikeAt < 0 && gap <= reach + 1.3) {
            // 닿지 않았으면 그쪽으로 뛰어듦 (도끼가 닿는 틱에 곁에 있게 — 티르빙의 전사와 같음: 공중의 가로 속도는 틱마다 × 0.91, 6틱에 처음 속도 × 4.6)
            double far = gap - reach;
            if (far > 0.25 && x.onGround()) {
                Vec3 d = V.flat(V.sub(ep, xp));
                double v = Math.min(0.8, far / 4.6 + 0.04);
                x.getNavigation().stop();
                x.setDeltaMovement(d.x * v, 0.28, d.z * v);
            }
            x.setYRot(yaw);
            x.setYBodyRot(yaw);
            x.setYHeadRot(yaw);
            x.swingAxe();
            m.strikeAt = now + TyrfingWarrior.STRIKE_AT;
            m.strikeFoe = e;
            m.nextSwing = now + (m.kind == CAPTAIN ? m.every : Math.max(10, (long) JobTraits8.trait(j, "crewEvery", 30)));
        }
    }

    /**
     * 노릴 적: 주인의 소울 미스트 표식이 남은 적 (이 망자에게서 range + 4칸 안) → 그레이브야드 고리 안의 적 (range 칸 — 가까움 + 이미 노리는 망자 수 × 1.5칸) → 주인 (또는
     * 선장) 이 마지막으로 친 적 (range + 4칸) → 둘레 range 칸의 적대 몹 중 (가까움 + 이미 노리는 망자 수 × 1.5칸) 이 가장 작은 적
     */
    private static LivingEntity pickFoe(ServerPlayer sp, Member m, List<Member> all, double range) {
        Vec3 xp = m.x.position();
        LivingEntity mist = JobTraits8.mistFoe(sp, xp, range + 4);
        if (mist != null && mist.level() == m.x.level() && Kit.canHit(sp, mist)) return mist;
        LivingEntity yard = closest(sp, m, all, range, true);
        if (yard != null) return yard;
        LivingEntity focus = JobTraits8.focus(sp);
        if (focus != null && focus.level() == m.x.level() && Kit.canHit(sp, focus) && V.hdist(focus.position(), xp) <= range + 4) return focus;
        return closest(sp, m, all, range, false);
    }

    /** 둘레 range 칸의 적 중 (가까움 + 이미 노리는 망자 수 × 1.5칸) 이 가장 작은 적 — yard 면 그레이브야드 고리 안의 적만 */
    private static LivingEntity closest(ServerPlayer sp, Member m, List<Member> all, double range, boolean yard) {
        Vec3 xp = m.x.position();
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : Kit.around(sp, xp, range, 4)) {
            if (!Kit.splashFoe(sp, e) || (yard && !JobTraits8.inYard(sp, e))) continue;
            int crowd = 0;
            for (Member o : all) if (o != m && o.foe == e) crowd++;
            double d = V.hdist(e.position(), xp) + crowd * 1.5;
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** 적이 없음: 주인 뒤 (망자마다 다른 자리 — 2.6칸) 를 따름 · 24칸 넘게 떨어지면 곁으로 옮겨 옴 (녹색 무덤불) */
    private static void follow(ServerPlayer sp, Member m, long now) {
        TyrfingWarrior x = m.x;
        Vec3 f = Kit.facingOf(sp);
        double[] angs = {145, -145, 110, -110, 170, -170, 80, -80};
        Vec3 dir = V.turnY(f, angs[m.slot % angs.length]);
        Vec3 spot = V.add(sp.position(), dir, m.kind == CAPTAIN ? 4.2 : 2.6);
        double d = V.hdist(x.position(), sp.position());
        if (d > 24 || Math.abs(x.getY() - sp.getY()) > 10) {
            Vec3 g = standAt(sp.level(), spot);
            SkillFx.fx(sp.level(), "rpg:tyr_ghostfire", x.position(), Opt.of("size", 1.4, "life", 0.4, "color", FIRE, "alpha", 0.8));
            x.getNavigation().stop();
            x.teleportTo(g.x, g.y, g.z);
            SkillFx.fx(sp.level(), "rpg:tyr_ghostfire", g, Opt.of("size", 1.4, "life", 0.5, "color", FIRE, "alpha", 0.8));
            return;
        }
        if (V.hdist(x.position(), spot) > 1.4) {
            if (now - m.pathAt >= 6) m.pathAt = x.getNavigation().moveTo(spot.x, spot.y, spot.z, d > 6 ? RUN : 1.0) ? now : now - 3;
        } else {
            x.getNavigation().stop();
            Vec3 lp = V.add(sp.position(), f, 6);
            x.getLookControl().setLookAt(lp.x, sp.getEyeY(), lp.z);
        }
    }

    /**
     * 도끼가 닿음 (공격 동작 STRIKE_AT 틱 뒤): 그 적이 아직 닿는 곳이면 녹색 초승달 · 불티 · 직업 스킬 피해 (위력 × 망자 공격 배율 — 소울 미스트 표식이면 더). 선장은 도끼 끝 둘레
     * CLEAVE × 크기 칸을 휩쓸어 (위력 × hit — 같은 표식 덤) · 큰 초승달 · 갈라진 땅 · 녹색 충격 고리, 친 적을 망자가 노림
     */
    private static void strike(ServerPlayer sp, Jobs.Job j, Member m) {
        LivingEntity e = m.strikeFoe;
        m.strikeFoe = null;
        TyrfingWarrior x = m.x;
        if (e == null || e.isRemoved() || !e.isAlive() || x.isRemoved() || e.level() != x.level()) return;
        if (V.hdist(x.position(), e.position()) - Kit.bodyPad(e) > REACH * m.scale + 1.6) return;
        ServerLevel level = sp.level();
        Vec3 xp = x.position();
        Vec3 d = Math.hypot(e.getX() - xp.x, e.getZ() - xp.z) > 1e-3 ? V.flat(V.sub(e.position(), xp)) : Kit.facingOf(x);
        boolean captain = m.kind == CAPTAIN;
        double k = m.scale;
        if (captain || RuneKit.fxQuota("necroChop", 8)) {
            SkillFx.arc(level, V.up(V.add(xp, d, 1.0 * k), 1.2 * k), d, Opt.of("shape", "heavy", "flip", false, "size", 1.3 * k, "life", captain ? 0.3 : 0.24, "speed",
                    0.0, "spin", 80.0, "color", FIRE, "aura", DEEP));
            SkillFx.sparks(level, V.up(V.add(xp, d, 1.4 * k), 0.4), FIRE, captain ? 10 : 3, 3 * k);
        }
        Vec3 hitAt = V.add(xp, d, (REACH + 0.5) * k);
        if (captain) {
            Vec3 g = new Vec3(hitAt.x, RuneKit.groundAt(level, V.up(hitAt, 1.0), xp.y), hitAt.z);
            SkillFx.fx(level, "rpg:ground_crack", V.up(g, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", 1.45 * k, "life", 1.2, "color", DEEP,
                    "alpha", 0.9));
            SkillFx.shock(level, V.up(g, 0.1), FIRE, CLEAVE * k * 0.9, 0.35);
            SkillFx.fx(level, "rpg:tyr_ghostfire", g, Opt.of("size", 1.8, "life", 0.45, "color", FIRE, "alpha", 0.85));
            SkillFx.play(level, "mace.heavy_smash_ground", g, 0.6, 1.1);
        }
        SkillFx.play(level, "entity.zombie.attack_wooden_door", xp, captain ? 0.8 : 1.3, captain ? 0.7 : 0.35);
        if (PASSIVE.contains(sp.getUUID())) return;
        String life = RuneKit.lifeToken(sp);
        if (!captain) {
            JobKit.strike(sp, e, j, JobSystem.power(sp, j), JobTraits8.crewMult(sp, j, e), true, false, life, 0, got -> {
                if (RuneKit.fxQuota("necroHit", 6)) JobFx.hit(e, j, 0.8);
            });
            return;
        }
        JobTraits8.setFocus(sp, e);
        double P = JobSystem.power(sp, j);
        for (LivingEntity o : Kit.around(sp, hitAt, CLEAVE * k, 3)) {
            JobKit.strike(sp, o, j, P, m.hit * JobTraits8.mistFactor(sp, j, o), true, false, life, 0, got -> {
                if (JobKit.pushable(o)) RuneKit.knock(o, V.sub(o.position(), xp), 0.25, 0.15);
                if (RuneKit.fxQuota("necroCaptainHit", 6)) JobFx.hit(o, j, 1.2);
            });
        }
    }
}
