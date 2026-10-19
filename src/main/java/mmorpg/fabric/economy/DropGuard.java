package mmorpg.fabric.economy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Economy;
import mmorpg.fabric.Notify;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * 0.21.0 버린 아이템은 가방으로 (사용자 요청): 살아 있는 플레이어가 떨어뜨리려는 아이템 (Q · 창 밖으로 끌어 놓기) 을 가방에 다시 넣음
 * (mixin ServerPlayerDropMixin — 들고 있던 칸이 비었으면 그 칸으로, 아니면 가방의 빈 칸 · 같은 묶음으로). 죽을 때 떨어지는 것 · 관전 · 창작 모드 ·
 * 가방에 자리가 없어 남은 몫은 바닐라대로 떨어짐. 설정 ECONOMY.dropReturn (끄면 바닐라).
 * 처음 (그 뒤 30초마다 한 번) 은 "쓰레기통으로 지움" 안내.
 */
public final class DropGuard {
    private DropGuard() {}

    /** 운영 · 시험용 스위치 (기본 켬) */
    public static boolean enabled = true;
    /** 안내를 띄운 때 (사람마다 — 잦으면 건너뜀) */
    private static final Map<UUID, Long> HINT = new HashMap<>();
    private static final long HINT_GAP_MS = 30_000;

    /** 시험용: 가방으로 돌려보낸 횟수 */
    public static int kept;

    /**
     * 떨어뜨리려는 stack 을 가방에 다시 넣음 — 모두 들어갔으면 true (떨어뜨리지 않음). 일부만 들어가면 stack 이 줄어든 채 false (남은 몫은 떨어짐),
     * 해당 없으면 (꺼짐 · 죽음 · 관전 · 창작) false
     */
    public static boolean keep(ServerPlayer sp, ItemStack stack) {
        if (!enabled || sp == null || stack == null || stack.isEmpty() || !Economy.cfg().truthy("dropReturn")) return false;
        if (sp.isRemoved() || !sp.isAlive() || sp.isSpectator() || sp.isCreative()) return false;
        int before = stack.getCount();
        Inventory inv = sp.getInventory();
        int sel = inv.getSelectedSlot();
        if (inv.getItem(sel).isEmpty()) {
            // Q 로 버린 것: 들고 있던 칸으로 그대로 (첫 빈 칸으로 옮겨지지 않게)
            inv.setItem(sel, stack.copy());
            stack.setCount(0);
        } else inv.add(stack); // 넣은 만큼 stack 이 줄어듦
        if (stack.getCount() == before) return false;
        kept++;
        hint(sp);
        return stack.isEmpty();
    }

    private static void hint(ServerPlayer sp) {
        long now = System.currentTimeMillis();
        Long last = HINT.get(sp.getUUID());
        Notify.sound(sp, SoundEvents.ITEM_PICKUP, 0.5F, 1.4F);
        if (last != null && now - last < HINT_GAP_MS) return;
        HINT.put(sp.getUUID(), now);
        Notify.notify(sp, "§7버린 아이템은 가방으로 돌아옵니다 §8— 지우려면 §f/쓰레기통", 50);
    }

    /** 나감: 안내 기록 지움 */
    public static void forget(UUID id) {
        HINT.remove(id);
    }
}
