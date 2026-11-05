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
 * <li>일어남: 쓰러진 적 자리 (그 땅) 아래에서 RISE 틱 동안 솟음 (녹색 무덤불 · 갈라진 땅) — 특성의 망자 (TRAIT) 는 주인마다 최대 trait.max (넘으면 가장 오래된 것이 무너짐),
 * 궁극기의 선원 (ULT) 은 따로 (스킬이 움직이다 놓아 줌 — stay 틱)</li>
 * <li>싸움: 주인이 마지막으로 친 적 (가까우면) → 아니면 둘레 crewRange 칸의 적대 몹 중 가까운 적 (다른 망자가 노리는 적은 1.5칸 멀게 — 둘러쌈) 에게 달려가 crewEvery 틱마다
 * 도끼로 내려침 (공격 동작 STRIKE_AT 틱 뒤 — 위력 × crewHit (망자 일으키기 동안 + boost) · 주인의 직업 스킬 피해 (JobKit — 숫자 · 처치 · 다시 망자는 주인의 것)).
 * 적이 없으면 주인 뒤를 따름 (24칸 넘게 떨어지면 곁으로 옮겨 옴)</li>
 * <li>끝: crewFor 틱이 지나거나 주인이 죽음 · 차원을 옮김 · 직업이 바뀜 · 나감 → 녹색 불길 · 뼈 조각으로 무너짐 (몹은 저장하지 않음 — TyrfingWarrior)</li>
 * </ul>
 */
public final class NecroCrew {
    private NecroCrew() {}

    static final int TRAIT = 0;
    static final int ULT = 1;
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

    /** 특성의 망자 수 (궁극기의 선원은 빼고 — 게이지) */
    public static int count(ServerPlayer sp) {
        int n = 0;
        for (Member m : members(sp)) if (m.kind == TRAIT && !m.x.isRemoved()) n++;
        return n;
    }

    /** 시험: 모든 망자 수 (궁극기의 선원 포함) */
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
            out.append(String.format(java.util.Locale.ROOT, "[%s %.2f,%.2f,%.2f risen=%s scripted=%s", m.kind == TRAIT ? "trait" : "ult", p.x, p.y, p.z, m.risen, m.scripted));
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

    /** 망자 하나를 목록에서 뺌 (시체 폭발이 터뜨림 · 몸은 스킬이 치움) */
    static void take(ServerPlayer sp, Member m) {
        List<Member> l = CREW.get(sp.getUUID());
        if (l != null) l.remove(m);
        JobTraits.sendGauge(sp, false);
    }

    /** at 둘레 radius 칸 안의 가장 가까운 망자 (다 솟았고 스킬이 움직이지 않는 것 — 없으면 null) */
    static Member nearest(ServerPlayer sp, Vec3 at, double radius) {
        long now = Tasks.now();
        Member best = null;
        double bd = Double.MAX_VALUE;
        for (Member m : members(sp)) {
            if (m.x.isRemoved() || m.scripted || m.rising(now)) continue;
            double d = V.hdist(m.x.position(), at);
            if (d <= radius && Math.abs(m.x.getY() - at.y) <= 4 && d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    /** 자리를 바꿀 망자: 보는 쪽 cone 도 안 · range 칸 안에서 시선에 가장 가까운 것, 없으면 가장 먼 것 (다 솟았고 스킬이 움직이지 않는 것) */
    static Member swapPick(ServerPlayer sp, double range, double cone) {
        long now = Tasks.now();
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1F);
        Member aimed = null;
        double best = Double.MAX_VALUE;
        Member far = null;
        double fd = -1;
        for (Member m : members(sp)) {
            if (m.x.isRemoved() || m.scripted || m.rising(now) || m.x.level() != sp.level()) continue;
            Vec3 to = V.sub(V.up(m.x.position(), 1.2), eye);
            double d = V.len(to);
            if (d > range) continue;
            double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, V.dot(V.scale(to, 1 / Math.max(1e-6, d)), look)))));
            if (ang <= cone && ang < best) {
                best = ang;
                aimed = m;
            }
            if (d > fd) {
                fd = d;
                far = m;
            }
        }
        return aimed != null ? aimed : far;
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

    /** 궁극기의 선원 하나 (스킬이 움직임 — 뛰어내리는 중, 물리 꺼짐): at 에 바로 섬 */
    static Member crewAt(ServerPlayer sp, Vec3 at, float yaw, int life) {
        ServerLevel level = sp.level();
        TyrfingWarrior x = TyrfingWarrior.spawnAs(TyrfingWarrior.CREW_ID, level, at, yaw, life + 80);
        if (x == null) return null;
        x.noPhysics = true;
        x.setNoGravity(true);
        List<Member> list = CREW.computeIfAbsent(sp.getUUID(), k -> new ArrayList<>());
        int slot = 0;
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Member m : list) used.add(m.slot);
        while (used.contains(slot)) slot++;
        Member m = new Member(x, ULT, Tasks.now() - RISE, Tasks.now() + life, at, slot);
        m.scripted = true;
        list.add(m);
        return m;
    }

    /** 스킬이 놓아 줌 (땅에 섬 — 이제 이 틀이 움직임): stay 틱 더 */
    static void release(Member m, Vec3 ground, int stay) {
        if (m.x.isRemoved()) return;
        settle(m, ground);
        m.risen = true;
        m.scripted = false;
        m.until = Tasks.now() + stay;
        m.nextSwing = Tasks.now() + 8 + m.slot * 3;
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
            SkillFx.fx(level, "rpg:tyr_ghostfire", at, Opt.of("size", 2.0, "life", 0.6, "color", FIRE, "alpha", 0.9));
            SkillFx.sparks(level, V.up(at, 1.0), new double[] {0.92, 0.88, 0.74}, 8, 4);
            SkillFx.play(level, "entity.skeleton.death", at, 0.7, 0.6);
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
        if ((now + m.slot * 4) % 12 == 0 && RuneKit.fxQuota("necroCrew", 6)) JobFx.soul(level, V.up(x.position(), 2.35), 0.15, j, 0.5, Opt.of("alpha", 0.85));
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
        double gap = V.hdist(xp, ep) - pad;
        x.getLookControl().setLookAt(e);
        Vec3 away = V.sub(xp, ep);
        Vec3 dir = Math.hypot(away.x, away.z) > 1e-3 ? V.flat(away) : V.rightOf(Kit.facingOf(sp));
        Vec3 spot = V.add(ep, dir, pad + REACH);
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
        if (now >= m.nextSwing && m.strikeAt < 0 && gap <= REACH + 1.3) {
            // 닿지 않았으면 그쪽으로 뛰어듦 (도끼가 닿는 틱에 곁에 있게 — 티르빙의 전사와 같음: 공중의 가로 속도는 틱마다 × 0.91, 6틱에 처음 속도 × 4.6)
            double far = gap - REACH;
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
            m.nextSwing = now + Math.max(10, (long) JobTraits8.trait(j, "crewEvery", 30));
        }
    }

    /** 노릴 적: 주인이 마지막으로 친 적 (이 망자에게서 range + 4칸 안) → 둘레 range 칸의 적대 몹 중 (가까움 + 이미 노리는 망자 수 × 1.5칸) 가 가장 작은 적 */
    private static LivingEntity pickFoe(ServerPlayer sp, Member m, List<Member> all, double range) {
        Vec3 xp = m.x.position();
        LivingEntity focus = JobTraits8.focus(sp);
        if (focus != null && focus.level() == m.x.level() && Kit.canHit(sp, focus) && V.hdist(focus.position(), xp) <= range + 4) return focus;
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : Kit.around(sp, xp, range, 4)) {
            if (!Kit.splashFoe(sp, e)) continue;
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
        Vec3 spot = V.add(sp.position(), dir, 2.6);
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

    /** 도끼가 닿음 (공격 동작 STRIKE_AT 틱 뒤): 그 적이 아직 닿는 곳이면 녹색 초승달 · 불티 · 직업 스킬 피해 (위력 × 망자 공격 배율) */
    private static void strike(ServerPlayer sp, Jobs.Job j, Member m) {
        LivingEntity e = m.strikeFoe;
        m.strikeFoe = null;
        TyrfingWarrior x = m.x;
        if (e == null || e.isRemoved() || !e.isAlive() || x.isRemoved() || e.level() != x.level()) return;
        if (V.hdist(x.position(), e.position()) - Kit.bodyPad(e) > REACH + 1.6) return;
        ServerLevel level = sp.level();
        Vec3 xp = x.position();
        Vec3 d = Math.hypot(e.getX() - xp.x, e.getZ() - xp.z) > 1e-3 ? V.flat(V.sub(e.position(), xp)) : Kit.facingOf(x);
        if (RuneKit.fxQuota("necroChop", 8)) {
            SkillFx.arc(level, V.up(V.add(xp, d, 1.0), 1.2), d, Opt.of("shape", "heavy", "flip", false, "size", 1.3, "life", 0.24, "speed", 0.0, "spin", 80.0, "color",
                    FIRE, "aura", DEEP));
            SkillFx.sparks(level, V.up(V.add(xp, d, 1.4), 0.4), FIRE, 3, 3);
        }
        SkillFx.play(level, "entity.zombie.attack_wooden_door", xp, 1.3, 0.35);
        if (PASSIVE.contains(sp.getUUID())) return;
        JobKit.strike(sp, e, j, JobSystem.power(sp, j), JobTraits8.crewMult(sp, j), true, false, RuneKit.lifeToken(sp), 0, got -> {
            if (RuneKit.fxQuota("necroHit", 6)) JobFx.hit(e, j, 0.8);
        });
    }
}
