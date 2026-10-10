package mmorpg.fabric.gear;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Weapon;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 무기 강화 단계 (+1 ~ +10, enhance.js · weapons.js ENHANCE_PROP): 아이템 구성 요소 mmorpg:enhance (저장 · 클라이언트에도 — 이름 "+N" · 설명).
 * 피해 배율 (combat.js heldEnhance): 손에 든 그 무기의 단계, 아니면 6초 안에 들었던 같은 무기의 단계 (쏜 화살 · 스킬 파도가 날아가는 동안 바꿔 들어도).
 */
public final class WeaponEnhance {
    private WeaponEnhance() {}

    public static DataComponentType<Integer> ENHANCE;
    /** 쏜 뒤 무기를 바꿔도 날아가는 화살 · 스킬 · 반사에는 그 단계 (combat.js ENHANCE_MEMORY_TICKS) */
    static final int MEMORY_TICKS = 120;

    private record Seen(int n, long tick) {}

    private static final Map<UUID, Map<String, Seen>> RECENT = new HashMap<>();

    /** Mmorpg.onInitialize (아이템보다 먼저) */
    public static void init() {
        ENHANCE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Mmorpg.id("enhance"),
                DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 10)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
        // 손에 든 무기의 단계를 4틱마다 적어 둠 (main.js heldTick)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (Tasks.now() % 4 != 0) return;
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
                if (w != null) remember(sp, w.id(), of(sp.getMainHandItem()));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RECENT.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RECENT.clear());
    }

    /** 아이템의 강화 단계 (없으면 0 — weapons.js enhanceOf) */
    public static int of(ItemStack stack) {
        if (stack == null || stack.isEmpty() || ENHANCE == null) return 0;
        Integer v = stack.get(ENHANCE);
        return v == null || v <= 0 ? 0 : Math.min(10, v);
    }

    /** 강화 단계를 적음 (0 이면 지움 — weapons.js writeEnhance: 이름 · 설명은 아이템이 단계로 그림) */
    public static void write(ItemStack stack, int n) {
        if (n > 0) stack.set(ENHANCE, Math.min(10, n));
        else stack.remove(ENHANCE);
    }

    private static void remember(ServerPlayer sp, String weaponId, int n) {
        RECENT.computeIfAbsent(sp.getUUID(), k -> new HashMap<>()).put(weaponId, new Seen(n, Tasks.now()));
    }

    /** 피해에 쓰는 강화 단계 (combat.js heldEnhance) */
    public static int held(ServerPlayer sp, Weapon w) {
        if (w == null) return 0;
        ItemStack main = sp.getMainHandItem();
        Weapon m = RpgCombat.weaponOf(main);
        if (m != null && m.id().equals(w.id())) {
            int n = of(main);
            remember(sp, w.id(), n);
            return n;
        }
        Map<String, Seen> map = RECENT.get(sp.getUUID());
        Seen s = map == null ? null : map.get(w.id());
        return s != null && Tasks.now() - s.tick() <= MEMORY_TICKS ? s.n() : 0;
    }

    /** 스탯 창 기준 무기의 단계 (weapons.js referenceEnhance): 손에 든 것 → 가진 것 중 가장 높은 것 */
    public static int reference(ItemStack main, Iterable<ItemStack> inventory, Weapon w) {
        if (w == null) return 0;
        Weapon m = RpgCombat.weaponOf(main);
        if (m != null && m.id().equals(w.id())) return of(main);
        int best = 0;
        for (ItemStack s : inventory) {
            Weapon x = RpgCombat.weaponOf(s);
            if (x != null && x.id().equals(w.id())) best = Math.max(best, of(s));
        }
        return best;
    }
}
