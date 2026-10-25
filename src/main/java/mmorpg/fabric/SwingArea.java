package mmorpg.fabric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Motions;
import mmorpg.core.Weapon;
import mmorpg.fabric.motion.MotionSystem;
import mmorpg.fabric.skill.Kit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.39.0 참격 범위 피해 (rules_more MOTION.area — 사용자 "허공을 때려도 이펙트가 나오는데 해당 이펙트 범위만큼 피해가 들어가게", 고른 것: 범위 안 모두 100%).
 * 근접 무기 평타를 휘두를 때마다 (허공 포함) 그 동작의 범위 안 적에게 평타 한 번 그대로:
 * 가로 베기 · X자 · 밀치기 = 앞 부채꼴, 세로 베기 · 내려찍기 · 내려치기 = 앞 띠, 찌르기 = 앞 줄, 회전 베기 = 둘레 (낫은 1.2배 — 예전 넓게 베기와 같은 120°).
 * <ul>
 *   <li>조준한 적 (공격 패킷 → Player#attack) 은 바닐라 공격으로 한 번, 범위의 다른 적은 1틱 뒤 (같은 틱에 온 공격 · 휘두르기 패킷을 다 받은 뒤) — 같은 적이 두 번 맞지 않음</li>
 *   <li>맞는 적: 적대 무리 · 결투 상대 (Kit.splashFoe), 눈에서 보이는, 가까운 순 MOTION.area.max 명. 범위는 휘두를 때의 눈 · 보는 방향 기준 (회전은 발 기준 둘레)</li>
 *   <li>한 번 휘두를 때 한 번만: 전투 망치 지진 (처음 맞은 적 둘레 — 범위로 맞은 적은 빼고), 홀 흡혈 상한 (최대 체력 × cap)</li>
 *   <li>사신 현신 (낫 — WeaponTraits.sweepMod) 은 낫 범위의 거리를 늘림</li>
 * </ul>
 * MOTION.area.enabled 를 끄면 예전대로 (회전 베기 · 낫 넓게 베기만 다른 적에게 40% — WeaponTraits).
 */
public final class SwingArea {
    private SwingArea() {}

    /** 한 번 휘두르기 */
    static final class Swing {
        final long tick;
        final Weapon w;
        final String motion;
        UUID primary;
        final List<LivingEntity> victims = new ArrayList<>();
        final Set<UUID> inArea = new HashSet<>();
        boolean quake;
        double healed;
        boolean applied;

        Swing(long tick, Weapon w, String motion) {
            this.tick = tick;
            this.w = w;
            this.motion = motion;
        }
    }

    private static final Map<UUID, Swing> NOW = new HashMap<>();
    /** 시험: 마지막으로 범위 피해를 준 적 수 (사람 → 수) */
    private static final Map<UUID, Integer> LAST_COUNT = new HashMap<>();

    public static void forget(UUID id) {
        NOW.remove(id);
        LAST_COUNT.remove(id);
    }

    /** 이번 휘두르기 (휘두른 틱 · 범위 피해가 나가는 다음 틱까지 — 맞힘 훅이 씀). 없으면 null */
    static Swing current(ServerPlayer sp) {
        Swing s = sp == null ? null : NOW.get(sp.getUUID());
        return s != null && Tasks.now() - s.tick <= 1 && Tasks.now() >= s.tick ? s : null;
    }

    // ------------------------------------------------------------ 휘두르기

    /** Slash.once (새 휘두르기): 범위 안 적을 지금 모아 두고 1틱 뒤 피해. primary = 조준한 적 (공격 패킷 — 허공이면 null) */
    static void begin(ServerPlayer sp, Weapon w, MotionSystem.Swing m, Entity primary) {
        if (w == null || m == null) {
            NOW.remove(sp.getUUID());
            return;
        }
        Swing s = new Swing(Tasks.now(), w, m.motion());
        if (primary != null) s.primary = primary.getUUID();
        NOW.put(sp.getUUID(), s);
        if (!Motions.areaEnabled()) return; // 범위 피해를 끔: 휘두르기만 기억 (같은 누름의 공격 패킷 하나 · 지진 한 번)
        try {
            s.victims.addAll(targets(sp, w, m.motion(), primary));
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] swing area", e);
        }
        for (LivingEntity e : s.victims) s.inArea.add(e.getUUID());
        Tasks.later(1, () -> apply(sp, s));
    }

    /** 같은 틱에 조준 공격이 이어 들어옴 (휘두르기 패킷이 먼저 왔을 때): 그 적은 바닐라 공격으로 맞으므로 범위 피해에서 뺌 */
    static void primary(ServerPlayer sp, Entity target) {
        Swing s = current(sp);
        if (s != null && s.tick == Tasks.now() && s.primary == null && target != null) s.primary = target.getUUID();
    }

    /** 이번 휘두르기에 조준 공격이 이미 들어왔는지 (한 번 누름에 공격 패킷은 하나만) */
    static boolean hasPrimary(ServerPlayer sp) {
        Swing s = current(sp);
        return s != null && s.primary != null;
    }

    private static void apply(ServerPlayer sp, Swing s) {
        if (s.applied || sp.isRemoved() || !sp.isAlive()) return;
        s.applied = true;
        Weapon held = RpgCombat.weaponOf(sp.getMainHandItem());
        if (held == null || !held.id().equals(s.w.id()) || !RpgCombat.lackOf(sp, held).isEmpty()) return; // 무기를 바꿨거나 들 수 없게 됨
        int n = 0;
        for (LivingEntity e : s.victims) {
            if (e.getUUID().equals(s.primary) || !Kit.splashFoe(sp, e)) continue;
            try {
                if (RpgCombat.areaHit(sp, e, s.w)) n++;
            } catch (RuntimeException err) {
                Mmorpg.LOG.warn("[mmorpg] swing area hit", err);
            }
        }
        LAST_COUNT.put(sp.getUUID(), n);
    }

    // ------------------------------------------------------------ 범위

    /** 범위 안 적 (조준한 적 빼고 — 눈에서 보이는, 가까운 순 MOTION.area.max 명) */
    static List<LivingEntity> targets(ServerPlayer sp, Weapon w, String motion, Entity primary) {
        Motions.Area a = Motions.area(motion, w.type(), w.gradeIndex());
        if (a == null) return List.of();
        a = widen(sp, w, a);
        Vec3 eye = sp.getEyePosition();
        Vec3 feet = sp.position();
        // 보는 방향 = 몸의 yaw · pitch (플레이어는 머리와 같음 — 머리 회전 yHeadRot 이 따로 노는 가짜 플레이어 · 시험에서도 같게)
        Vec3 f = Vec3.directionFromRotation(sp.getXRot(), sp.getYRot()).normalize();
        Vec3 r = f.cross(new Vec3(0, 1, 0));
        if (r.lengthSqr() < 1e-4) r = Kit.facingOf(sp).cross(new Vec3(0, 1, 0));
        r = r.normalize();
        Vec3 u = r.cross(f).normalize();
        boolean ring = a.shape().equals("ring");
        List<LivingEntity> cand = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, ring ? feet : eye, a.range() + 1.5, 5)) {
            if (e == primary || !Kit.splashFoe(sp, e)) continue;
            if (inside(a, e, eye, feet, f, r, u)) cand.add(e);
        }
        List<LivingEntity> out = Kit.visibleFrom(sp, eye, cand);
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
        int max = Math.max(0, Motions.areaMax());
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** 사신 현신 (낫 — 넓게 베기 범위를 늘리던 것): 낫 범위의 거리를 그만큼 */
    private static Motions.Area widen(ServerPlayer sp, Weapon w, Motions.Area a) {
        if (!"scythe".equals(w.type())) return a;
        try {
            double[] m = WeaponTraits.sweepMod.mod(sp, w);
            if (m != null && m.length >= 1 && m[0] > a.range()) return new Motions.Area(a.shape(), m[0], a.angle(), a.width(), a.height());
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] sweep mod", e);
        }
        return a;
    }

    /** 몸 (발 위 · 가운데 · 머리 아래 세 점, 몸 반폭만큼 넓혀) 중 하나가 범위 안이면 */
    static boolean inside(Motions.Area a, LivingEntity e, Vec3 eye, Vec3 feet, Vec3 f, Vec3 r, Vec3 u) {
        double pad = e.getBbWidth() / 2;
        double h = e.getBbHeight();
        double[] ys = {e.getY() + Math.min(0.2, h / 2), e.getY() + h / 2, e.getY() + Math.max(h / 2, h - 0.2)};
        boolean ring = a.shape().equals("ring");
        for (double y : ys) {
            if (ring) {
                if (Motions.inArea(a, e.getX() - feet.x, e.getZ() - feet.z, y - feet.y, pad)) return true;
            } else {
                Vec3 d = new Vec3(e.getX() - eye.x, y - eye.y, e.getZ() - eye.z);
                if (Motions.inArea(a, d.dot(f), d.dot(r), d.dot(u), pad)) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ 한 번 휘두를 때 한 번 (WeaponTraits)

    /** 전투 망치 지진: 이번 휘두르기의 처음 맞은 적에서만 (휘두르기 밖 — 범위를 끔 · 다른 길로 들어온 평타 — 은 늘) */
    static boolean claimQuake(ServerPlayer sp) {
        Swing s = current(sp);
        if (s == null) return true;
        if (s.quake) return false;
        s.quake = true;
        return true;
    }

    /** 이번 휘두르기의 범위로 맞는 적인지 (지진이 다시 때리지 않게) */
    static boolean inSwing(ServerPlayer sp, Entity e) {
        Swing s = current(sp);
        return s != null && e != null && (s.inArea.contains(e.getUUID()) || e.getUUID().equals(s.primary));
    }

    /** 이번 휘두르기에서 홀 흡혈로 이미 회복한 양 */
    static double healed(ServerPlayer sp) {
        Swing s = current(sp);
        return s == null ? 0 : s.healed;
    }

    static void addHealed(ServerPlayer sp, double amount) {
        Swing s = current(sp);
        if (s != null) s.healed += amount;
    }

    // ------------------------------------------------------------ 시험

    /** 시험: 마지막 휘두르기에서 범위 피해가 들어간 적 수 (없으면 -1) */
    public static int lastCount(ServerPlayer sp) {
        Integer n = sp == null ? null : LAST_COUNT.get(sp.getUUID());
        return n == null ? -1 : n;
    }

    /** 시험: 지금 이 사람이 휘두르면 범위로 맞을 적 (조준 없음) */
    public static List<LivingEntity> testTargets(ServerPlayer sp, String motion) {
        Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
        return w == null ? List.of() : targets(sp, w, motion, null);
    }
}
