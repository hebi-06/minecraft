package mmorpg.fabric;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Stats;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * 실제 스탯 (stats.js getStatsCached): (찍은 스탯 + 장비 보너스 — 4단계) × (1 + 룬 버프 비율), 반올림.
 * 룬 버프 (신체 강화 룬의 모든 스탯 +%) 는 저장하지 않음 (나가거나 죽으면 끝 — 룬이 지움).
 */
public final class RpgStats {
    private RpgStats() {}

    /** 룬 버프 비율 (플레이어 → 0.2 = +20%) */
    private static final Map<UUID, Double> BUFFS = new HashMap<>();

    static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BUFFS.clear());
    }

    static void forget(UUID id) {
        BUFFS.remove(id);
    }

    /** 실제 스탯 {str, dex, int, vit, point, lvmax} — 무기 피해 · 최대 체력 · 룬 위력이 모두 이것 */
    public static Map<String, Double> effective(ServerPlayer sp) {
        return Stats.effectiveStats(RpgData.of(sp).base(), Map.of(), buffOf(sp));
    }

    /** 룬 버프 비율 (없으면 0) */
    public static double buffOf(ServerPlayer sp) {
        return BUFFS.getOrDefault(sp.getUUID(), 0.0);
    }

    /**
     * 룬 버프를 바꿈 (stats.js setStatBuff — 0 이면 없앰). 바뀌었으면 최대 체력을 다시 계산 (늘면 그만큼 차고, 줄면 넘친 것만 깎임) 하고
     * 클라이언트 (스탯 창 · HUD) 에 보냄. 바뀌었는지
     */
    public static boolean setBuff(ServerPlayer sp, double pct) {
        double prev = buffOf(sp);
        double next = pct > 0 ? pct : 0;
        if (Math.abs(prev - next) < 1e-9) return false;
        if (next > 0) BUFFS.put(sp.getUUID(), next);
        else BUFFS.remove(sp.getUUID());
        if (!sp.isRemoved()) {
            RpgHealth.refreshMax(sp, false);
            RpgNet.sync(sp, false);
        }
        return true;
    }
}
