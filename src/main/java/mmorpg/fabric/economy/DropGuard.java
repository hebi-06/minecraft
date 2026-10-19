package mmorpg.fabric.economy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Economy;
import mmorpg.fabric.Notify;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * 0.21.0 버린 아이템은 가방으로 (사용자 요청): 살아 있는 플레이어가 아이템을 버리지 못하게 (mixin ServerPlayerDropMixin) — 핫바의 Q 는
 * 처음에 막아 그 칸 그대로, 창 안의 Q · 창 밖으로 끌어 놓기는 창이 뺀 아이템을 가방에 다시 넣음. 죽을 때 떨어지는 것 · 관전 · 창작 모드 ·
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

    /** 막을 사람인지 (켬 · 설정 · 살아 있음 · 생존 · 모험 — 게임 모드는 서버의 실제 모드로: 시험용 가짜 플레이어는 isCreative 가 늘 참) */
    static boolean applies(ServerPlayer sp) {
        if (!enabled || sp == null || !Economy.cfg().truthy("dropReturn")) return false;
        if (sp.isRemoved() || !sp.isAlive()) return false;
        GameType mode = sp.gameMode.getGameModeForPlayer();
        return mode == GameType.SURVIVAL || mode == GameType.ADVENTURE;
    }

    /**
     * 핫바에서 Q · Ctrl+Q: 막으면 true (아무것도 빼지 않음 — 든 칸 그대로). 클라이언트는 이미 그 칸을 뺀 것으로 그리므로 그 칸을 다시 보냄
     * (다음 틱 동기화에 실제 아이템이 감)
     */
    public static boolean blockHotbarDrop(ServerPlayer sp) {
        if (!applies(sp)) return false;
        Inventory inv = sp.getInventory();
        if (inv.getSelectedItem().isEmpty()) return false;
        sp.containerMenu.findSlot(inv, inv.getSelectedSlot()).ifPresent(i -> sp.containerMenu.setRemoteSlot(i, ItemStack.EMPTY));
        kept++;
        hint(sp);
        return true;
    }

    /**
     * 창 안에서 Q · 창 밖으로 끌어 놓기 (창이 이미 뺀 stack): 가방에 다시 넣음 — 모두 들어갔으면 true (떨어뜨리지 않음). 일부만 들어가면 stack 이
     * 줄어든 채 false (남은 몫은 떨어짐), 해당 없으면 (꺼짐 · 죽음 · 관전 · 창작) false
     */
    public static boolean keep(ServerPlayer sp, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !applies(sp)) return false;
        int before = stack.getCount();
        sp.getInventory().add(stack); // 넣은 만큼 stack 이 줄어듦
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
