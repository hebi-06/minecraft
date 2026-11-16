package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Tasks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * 도발 (직업 스킬 — 수호기사 방패 치기 · 방패 투척 · 피에로 …): ticks 동안 그 적이 이 사람을 노림. 몬스터 (monsters.js) · 보스 (bosses.js) 는 스크립트
 * ({@link #addTauntHook}) 가, 바닐라 몹 (Mob) 은 대상을 이 사람으로 — 매 틱 다시 (다른 사람이 때려도 바뀌지 않게).
 * 2026-11-16 무기 스킬 (스발린 — Aegis) 을 지우며 옮김 (사용자 "무기 스킬도 빼고 대신 무기 디자인을 늘리건데")
 */
public final class Taunts {
    private Taunts() {}

    /** 도발 (몬스터 · 보스가 넣음): (플레이어, 적, 틱) → 걸었는지 */
    @FunctionalInterface
    public interface Hook {
        boolean taunt(ServerPlayer sp, LivingEntity e, int ticks);
    }

    /** 도발 (v2.20.0): 몬스터 · 보스가 넣음 — 비어 있으면 바닐라 몹만 */
    private static final List<Hook> HOOKS = new ArrayList<>();

    /** 바닐라 몹 도발: 몹, 도발한 사람, 끝나는 틱 */
    private record Taunted(Mob m, ServerPlayer sp, long until) {}

    /** 몹 → 바닐라 몹 도발 (나중에 건 도발이 이김 — 몬스터의 도발과 같게) */
    private static final Map<UUID, Taunted> TAUNTED = new HashMap<>();

    static void init() {
        // 바닐라 몹 도발: 매 틱 다시 노리게
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TAUNTED.clear());
    }

    /** 나감: 이 사람이 건 바닐라 몹 도발을 지움 */
    static void forget(UUID id) {
        TAUNTED.values().removeIf(t -> t.sp().getUUID().equals(id));
    }

    public static void addTauntHook(Hook fn) {
        if (fn != null) HOOKS.add(fn);
    }

    /** ticks 동안 그 적이 이 사람을 노리게 함. 스크립트가 걸었으면 true (바닐라 몹은 false — 머리 위 표시 규칙이 애드온과 같게) */
    static boolean taunt(ServerPlayer sp, LivingEntity e, int ticks) {
        for (Hook h : HOOKS) {
            try {
                if (h.taunt(sp, e, ticks)) {
                    TAUNTED.remove(e.getUUID());
                    return true;
                }
            } catch (RuntimeException err) {
                Mmorpg.LOG.warn("[mmorpg] taunt", err);
            }
        }
        if (e instanceof Mob m) tauntMob(m, sp, ticks);
        return false;
    }

    /** 몹이 그 사람을 노릴 수 있는지 (둘 다 살아 있고 같은 차원, 크리에이티브 · 관전이 아님) */
    private static boolean canTaunt(Mob m, ServerPlayer sp) {
        return !m.isRemoved() && m.isAlive() && !sp.isRemoved() && sp.isAlive() && m.level() == sp.level() && sp.canBeSeenAsEnemy();
    }

    private static void tauntMob(Mob m, ServerPlayer sp, int ticks) {
        if (!canTaunt(m, sp)) return;
        m.setTarget(sp);
        TAUNTED.put(m.getUUID(), new Taunted(m, sp, Tasks.now() + ticks));
    }

    /** 매 틱: 도발이 끝났거나 · 몹이나 사람이 쓰러졌거나 · 다른 차원이면 놓아 줌, 아니면 대상을 그 사람으로 되돌림 */
    private static void tick() {
        if (TAUNTED.isEmpty()) return;
        long now = Tasks.now();
        for (Iterator<Taunted> it = TAUNTED.values().iterator(); it.hasNext(); ) {
            Taunted t = it.next();
            if (now >= t.until() || !canTaunt(t.m(), t.sp())) {
                it.remove();
                continue;
            }
            if (t.m().getTarget() != t.sp()) t.m().setTarget(t.sp());
        }
    }

    /** 0.30.0 직업 스킬의 도발: ticks 동안 그 적이 이 사람을 노림 (mark — 머리 위 "도발" 자리, 지금은 표시 없음). 스크립트가 걸었으면 true */
    public static boolean tauntFor(ServerPlayer sp, LivingEntity e, int ticks, boolean mark) {
        if (e == null || e.isRemoved() || !(e.level() instanceof ServerLevel)) return false;
        return taunt(sp, e, ticks);
    }
}
