package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.mob.Monsters;
import mmorpg.fabric.mob.PierrotSummon;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-06 피에로 「쇼타임」 의 소환물 (진짜 몹 mob.PierrotSummon — 깜짝 상자 · 광대 인형 · 피날레의 거대한 상자): 주인마다 기록하고 틱마다 움직임.
 * <ul>
 * <li>끌어들임 (10틱마다): 소환물 둘레 trait.lure 칸의 몬스터 (Monsters.lure — 걸어가 공격 동작 → 소환물이 맞음) · 바닐라 적대 몹 (대상을 소환물로) 이 소환물을
 * 노림. 보스는 아님 (보스는 스킬이 나를 도발). 다른 소환물을 노리던 몬스터는 그대로</li>
 * <li>광대 인형 (할루시네이트): 떨어져 내려선 뒤 가까운 적 (인형 둘레 FOE 칸 — 인형을 때리는 적 먼저) 에게 걸어가 섬, 적이 없으면 주인 곁을 따름 (24칸 넘게 떨어지면
 * 곁으로 옮겨 옴). 뿅망치는 스킬이 정한 틱에 (PierrotSkills — 동작은 그 틱의 HAMMER_AT 틱 전)</li>
 * <li>부서짐 (체력이 다함): 색종이 폭죽 — 둘레 trait.burstRadius 칸 위력 × trait.burst · 보스 아닌 적 trait.stun 틱 기절 (특성 피해 — 직업 색 숫자).
 * 시간이 다 되거나 주인이 죽음 · 차원을 옮김 · 직업이 바뀜 · 나감 → 펑 연기로 사라짐 (폭죽 없음)</li>
 * <li>나눠 받음 (RpgHealth.lossShare — JobTraits10): 주인이 받을 피해의 trait.share 를 shareRange 칸 안의 가장 가까운 소환물이 받음</li>
 * </ul>
 */
public final class PierrotSummons {
    private PierrotSummons() {}

    static final int BOX = 0;
    static final int DOLL = 1;
    static final int FINALE = 2;
    /** 광대 인형이 노릴 적을 찾는 둘레 (인형에서) · 주인을 따르는 거리 · 곁으로 옮겨 오는 거리 */
    static final double FOE = 8;
    static final double FOLLOW = 3.5;
    static final double TELEPORT = 24;
    /** 광대 인형이 떨어져 내려서는 틱 (그동안은 움직이지 않음) */
    static final int DROP = 10;

    /** 소환물 하나 */
    static final class Member {
        final PierrotSummon x;
        final int kind;
        final long born;
        long until;
        /** 이 소환물의 피해 위력 (스킬이 쓸 때의 P — 칼 · 뿅망치) */
        double P;
        /** 이 틱부터 몬스터를 끌어들임 (상자가 열린 뒤 · 인형이 내려선 뒤) */
        long lureFrom;
        /** 광대 인형: 노리는 적 · 길을 다시 찾은 틱 · 이 틱까지 멈춰 섬 (뿅망치를 휘두르는 중) */
        LivingEntity foe;
        long pathAt = -99;
        long holdUntil;

        Member(PierrotSummon x, int kind, long born, long until) {
            this.x = x;
            this.kind = kind;
            this.born = born;
            this.until = until;
        }

        boolean alive() {
            return !x.isRemoved() && x.isAlive();
        }
    }

    private static final Map<UUID, List<Member>> SUMMONS = new HashMap<>();

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(PierrotSummons::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SUMMONS.clear());
        PierrotSummon.onBreak = PierrotSummons::broken;
        PierrotSummon.onHurt = PierrotSummons::hurt;
    }

    // ------------------------------------------------------------ 기록

    /** 이 사람의 소환물 (스냅샷) */
    static List<Member> members(ServerPlayer sp) {
        List<Member> l = SUMMONS.get(sp.getUUID());
        return l == null ? List.of() : new ArrayList<>(l);
    }

    /** 살아 있는 소환물 수 (게이지 — 종류 상관없이) */
    public static int count(ServerPlayer sp) {
        int n = 0;
        for (Member m : members(sp)) if (m.alive()) n++;
        return n;
    }

    /** 시험: 살아 있는 소환물 몸 (kind — -1 = 모두) */
    public static List<PierrotSummon> bodies(ServerPlayer sp, int kind) {
        List<PierrotSummon> out = new ArrayList<>();
        for (Member m : members(sp)) if (m.alive() && (kind < 0 || m.kind == kind)) out.add(m.x);
        return out;
    }

    /** 시험 (실패 글): 소환물마다 종류 · 자리 · 체력 · 노리는 적 */
    public static String testState(ServerPlayer sp) {
        StringBuilder out = new StringBuilder();
        for (Member m : members(sp)) {
            Vec3 p = m.x.position();
            out.append(String.format(java.util.Locale.ROOT, "[%s %.2f,%.2f,%.2f hp=%.1f/%.1f alive=%s", m.kind == BOX ? "box" : m.kind == DOLL ? "doll" : "finale", p.x, p.y,
                    p.z, m.x.getHealth(), m.x.getMaxHealth(), m.alive()));
            if (m.foe != null) out.append(" foe=").append(Kit.typeId(m.foe));
            out.append("]");
        }
        return out.toString();
    }

    /** 살아 있는 소환물 중 kind 인 것 (오래된 차례) */
    static List<Member> of(ServerPlayer sp, int kind) {
        List<Member> out = new ArrayList<>();
        for (Member m : members(sp)) if (m.alive() && m.kind == kind) out.add(m);
        return out;
    }

    /**
     * 소환물 하나를 세움: kind (BOX · DOLL · FINALE) · at (발) · yaw · life 틱 · 체력 = 주인 최대 체력 × hpFrac · 크기 scale, 피해 위력 P · lureFrom 틱 뒤부터
     * 끌어들임. 못 세우면 null
     */
    static Member spawn(ServerPlayer sp, int kind, Vec3 at, float yaw, int life, double hpFrac, double scale, double P, int lureAfter) {
        ServerLevel level = sp.level();
        double hp = Math.max(10, RpgHealth.maxOf(sp) * hpFrac);
        String id = kind == DOLL ? PierrotSummon.DOLL : PierrotSummon.BOX;
        PierrotSummon x = PierrotSummon.spawn(id, level, at, yaw, life + 40, hp, scale, sp.getUUID());
        if (x == null) return null;
        long now = Tasks.now();
        Member m = new Member(x, kind, now, now + life);
        m.P = P;
        m.lureFrom = now + Math.max(0, lureAfter);
        SUMMONS.computeIfAbsent(sp.getUUID(), k -> new ArrayList<>()).add(m);
        JobTraits.sendGauge(sp, false);
        return m;
    }

    /** 소환물이 사라짐 (fx = 펑 연기 · 소리 — 부서짐이 아님: 색종이 폭죽 없음) */
    static void dismiss(Member m, boolean fx) {
        Monsters.endLure(m.x);
        if (fx && m.alive() && m.x.level() instanceof ServerLevel level) poof(level, m);
        if (!m.x.isRemoved()) m.x.discard();
    }

    /** 이 사람의 소환물을 모두 치움 (직업을 바꿈 · 죽음 · 나감 · 시험) */
    static void dismissAll(UUID owner, boolean fx) {
        List<Member> l = SUMMONS.remove(owner);
        if (l == null) return;
        for (Member m : l) dismiss(m, fx);
    }

    /** 펑 — 연기 · 색종이 조금 · 장난감 소리 (몸 크기만큼) */
    private static void poof(ServerLevel level, Member m) {
        double s = Math.max(0.6, m.x.getBbWidth() + 0.2);
        Vec3 c = V.up(m.x.position(), m.x.getBbHeight() * 0.5);
        JobFx.smoke(level, c, s, PierrotSkills.WHITE, PierrotSkills.GOLD, 0.5, Opt.of("alpha", 0.7));
        JobFx.confetti(level, c, s * 0.7, 0.6, Opt.empty());
        SkillFx.play(level, "entity.chicken.egg", c, 0.8, 0.9);
        SkillFx.play(level, "block.wool.break", c, 1.4, 0.7);
    }

    // ------------------------------------------------------------ 틱

    private static void tick(MinecraftServer server) {
        if (SUMMONS.isEmpty()) return;
        long now = Tasks.now();
        for (Iterator<Map.Entry<UUID, List<Member>>> it = SUMMONS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, List<Member>> e = it.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            Jobs.Job j = sp == null ? null : JobTraits10.jobIf(sp, JobTraits10.PR);
            boolean ok = j != null && sp.isAlive() && !sp.isRemoved();
            boolean changed = false;
            for (Iterator<Member> mi = e.getValue().iterator(); mi.hasNext(); ) {
                Member m = mi.next();
                if (!m.alive()) {
                    Monsters.endLure(m.x);
                    mi.remove();
                    changed = true;
                    continue;
                }
                if (!ok || m.x.level() != sp.level() || now >= m.until) {
                    dismiss(m, true);
                    mi.remove();
                    changed = true;
                    continue;
                }
                if (now >= m.lureFrom && (now - m.born) % 10 == 0) lureAround(sp, j, m);
                if (m.kind == DOLL) dollStep(sp, m, now);
            }
            if (e.getValue().isEmpty()) it.remove();
            if (changed && sp != null) JobTraits.sendGauge(sp, false);
        }
    }

    /** 끌어들임: 소환물 둘레 trait.lure 칸 (피날레의 거대한 상자는 × 크기) 의 몬스터 · 바닐라 적대 몹이 이 소환물을 노림 (24틱 — 10틱마다 다시) */
    static void lureAround(ServerPlayer sp, Jobs.Job j, Member m) {
        ServerLevel level = sp.level();
        double R = JobTraits10.trait(j, "lure", 6) * (m.kind == FINALE ? 1.4 : 1.0);
        Vec3 c = m.x.position();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(R + 2, 4, R + 2), x -> x != sp && x.isAlive()
                && !(x instanceof PierrotSummon))) {
            if (Math.hypot(e.getX() - c.x, e.getZ() - c.z) > R + Kit.bodyPad(e)) continue;
            if (Monsters.kindOf(e) != null) {
                Monsters.lure(e, m.x, sp, 24);
                continue;
            }
            if (e instanceof Mob mob && e instanceof Enemy && !JobKit.isBoss(e) && Kit.canHit(sp, e) && !(mob.getTarget() instanceof PierrotSummon)) mob.setTarget(m.x);
        }
    }

    /** 광대 인형 한 틱: 노릴 적 고르기 · 그쪽으로 걸어가 섬 · 적이 없으면 주인을 따름 */
    private static void dollStep(ServerPlayer sp, Member m, long now) {
        PierrotSummon x = m.x;
        if (now - m.born < DROP) return;
        double reach = 1.6 * Math.max(1, x.getScale());
        if (m.foe == null || !m.foe.isAlive() || m.foe.isRemoved() || m.foe.level() != x.level() || m.foe.distanceTo(x) > FOE + 4 || !Kit.canHit(sp, m.foe))
            m.foe = pickFoe(sp, x);
        if (now < m.holdUntil) {
            x.getNavigation().stop();
            if (m.foe != null) face(x, m.foe.position());
            return;
        }
        if (x.distanceTo(sp) > TELEPORT) {
            Vec3 p = V.add(sp.position(), Kit.facingOf(sp), -2);
            x.teleportTo(p.x, sp.getY(), p.z);
            x.getNavigation().stop();
            return;
        }
        if (m.foe != null) {
            double gap = Math.max(0, Math.hypot(m.foe.getX() - x.getX(), m.foe.getZ() - x.getZ()) - Kit.bodyPad(m.foe) - x.getBbWidth() / 2);
            face(x, m.foe.position());
            if (gap > reach) {
                if (now - m.pathAt >= 10 || x.getNavigation().isDone()) {
                    x.getNavigation().moveTo(m.foe, 1.15);
                    m.pathAt = now;
                }
            } else x.getNavigation().stop();
            return;
        }
        if (x.distanceTo(sp) > FOLLOW + 1.5) {
            if (now - m.pathAt >= 10 || x.getNavigation().isDone()) {
                Vec3 p = V.add(sp.position(), V.rightOf(Kit.facingOf(sp)), -FOLLOW * 0.6);
                x.getNavigation().moveTo(p.x, sp.getY(), p.z, 1.15);
                m.pathAt = now;
            }
        } else x.getNavigation().stop();
    }

    /** 인형이 노릴 적: 인형을 때리는 몬스터 먼저 → 인형 둘레 FOE 칸의 맞을 수 있는 적대 몹 중 가까운 것 */
    private static LivingEntity pickFoe(ServerPlayer sp, PierrotSummon x) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : Kit.around(sp, x.position(), FOE, 3)) {
            if (!Kit.splashFoe(sp, e)) continue;
            Monsters.Fight f = Monsters.fightOf(e);
            double d = e.distanceToSqr(x) - (f != null && f.lure() == x ? 100 : 0);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** 몸 · 머리를 그쪽으로 */
    static void face(PierrotSummon x, Vec3 at) {
        double dx = at.x - x.getX();
        double dz = at.z - x.getZ();
        if (dx * dx + dz * dz < 1e-4) return;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        x.setYRot(yaw);
        x.setYHeadRot(yaw);
        x.setYBodyRot(yaw);
    }

    // ------------------------------------------------------------ 맞음 · 부서짐 · 나눠 받음

    /** 이 소환물의 기록 (없으면 null) */
    private static Member find(PierrotSummon s) {
        if (s.owner == null) return null;
        List<Member> l = SUMMONS.get(s.owner);
        if (l == null) return null;
        for (Member m : l) if (m.x == s) return m;
        return null;
    }

    /** 맞음: 소환물 머리 위 빨간 숫자 (플레이어가 맞은 것과 같은 꼴 — 대신 맞아 준 양) */
    private static void hurt(PierrotSummon s, Float amount) {
        if (amount == null || !(amount > 0.05F) || !RuneKit.fxQuota("pierrotHurt" + s.getId(), 2)) return;
        DamageNumbers.show(s, amount, DamageNumbers.Style.PLAYER, "", 0);
    }

    /**
     * 부서짐 (체력이 다함): 색종이 폭죽 — 둘레 burstRadius 칸 (피날레의 거대한 상자는 × 크기) 적에게 위력 × burst (특성 피해 · 직업 색 숫자) · 보스 아닌 적 stun 틱 기절,
     * 깨진 상자 조각 · 용수철 광대가 튀어 오름 · 큰 펑 소리
     */
    private static void broken(PierrotSummon s, DamageSource src) {
        Member m = find(s);
        if (m == null || !(s.level() instanceof ServerLevel level)) return;
        List<Member> l = SUMMONS.get(s.owner);
        if (l != null) l.remove(m);
        Monsters.endLure(s);
        ServerPlayer sp = RuneKit.player(level, s.owner);
        Jobs.Job j = sp == null ? null : JobTraits10.jobIf(sp, JobTraits10.PR);
        double scale = Math.max(1, s.getScale());
        Vec3 c = V.up(s.position(), s.getBbHeight() * 0.5);
        double R = (j == null ? 3 : JobTraits10.trait(j, "burstRadius", 3)) * (m.kind == FINALE ? 1.3 : 1.0);
        // 색종이 폭죽 (여러 빛 겹) · 둘레 고리 · 터지는 소리
        JobFx.confetti(level, c, 1.1 * R / 3 * Math.sqrt(scale), 0.9, Opt.empty());
        JobFx.confetti(level, V.up(c, 0.5), 0.8 * R / 3 * Math.sqrt(scale), 1.1, Opt.of("grow", 0.4));
        SkillFx.shock(level, V.up(s.position(), 0.06), PierrotSkills.GOLD, R / 2.1, 0.4);
        JobFx.burst(level, c, 0.9 * scale, PierrotSkills.RED, PierrotSkills.GOLD, 0.3, Opt.empty());
        SkillFx.play(level, "entity.firework_rocket.blast", c, 1.1, 1.0);
        SkillFx.play(level, "entity.firework_rocket.twinkle", c, 1.2, 0.8);
        SkillFx.play(level, "entity.slime.squish", c, 1.6, 0.8);
        if (sp == null || j == null) return;
        String life = RuneKit.lifeToken(sp);
        double power = JobSystem.power(sp, j);
        double mult = JobTraits10.trait(j, "burst", 0.8);
        int stun = (int) JobTraits10.trait(j, "stun", 10);
        for (LivingEntity e : Kit.around(sp, s.position(), R, 2.5)) {
            JobKit.strike(sp, e, j, power, mult, false, false, life, 0, d -> {
                if (!JobKit.isBoss(e) && e.isAlive()) RuneKit.stun(e, stun, PierrotSkills.GOLD, true);
            });
        }
        JobTraits.sendGauge(sp, false);
    }

    /**
     * 나눠 받음 (JobTraits10 — RpgHealth.lossShare): 주인이 받을 피해 loss 의 frac 을 range 칸 안의 가장 가까운 소환물이 받음 (그 소환물에 금빛 실 · 숫자) — 덜어 낸 양
     */
    static double share(ServerPlayer sp, double loss, double frac, double range) {
        if (!(loss > 0) || !(frac > 0)) return 0;
        Member best = null;
        double bd = range * range;
        for (Member m : members(sp)) {
            if (!m.alive() || m.x.level() != sp.level()) continue;
            double d = m.x.distanceToSqr(sp);
            if (d <= bd) {
                bd = d;
                best = m;
            }
        }
        if (best == null) return 0;
        double moved = Math.min(loss * frac, best.x.getHealth() + 1);
        ServerLevel level = sp.level();
        PierrotSummon x = best.x;
        if (RuneKit.fxQuota("pierrotShare" + sp.getId(), 2)) {
            Vec3 from = V.up(sp.position(), 1.1);
            Vec3 to = V.up(x.position(), x.getBbHeight() * 0.7);
            SkillFx.beam(level, from, to, PierrotSkills.GOLD, Opt.of("half", 0.08, "thick", 0.04, "life", 0.25, "alpha", 0.85));
            SkillFx.sparks(level, to, PierrotSkills.GOLD, 3, 2);
        }
        // 다음 틱에 (주인의 피해 처리 안에서 다른 몹의 체력을 바꾸지 않게)
        float amt = (float) moved;
        Tasks.later(1, () -> {
            if (!x.isRemoved() && x.isAlive() && x.level() instanceof ServerLevel lv) x.hurtServer(lv, x.damageSources().generic(), amt);
        });
        return moved;
    }
}
