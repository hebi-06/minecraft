package mmorpg.fabric.ui;

import java.util.List;
import java.util.function.Predicate;
import mmorpg.core.Economy;
import mmorpg.core.Rules;
import mmorpg.fabric.Notify;
import mmorpg.fabric.economy.Wallet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 가방 도우미 (shop.js countItem · removeItem · giveItem · fitsAfter · bagFull): 핫바 + 가방 36칸 (갑옷 · 왼손 칸은 셈하지 않음 — 베드락
 * 인벤토리 컨테이너와 같음). NPC 화폐도 여기 — 0.21.0 부터 실버 (CONFIG.CURRENCY_NAME · 동전 CONFIG.CURRENCY_ITEM = 은화): 지갑의 실버 +
 * 가방의 은화 ({@link Wallet} — 낼 때는 지갑 먼저, 받으면 지갑으로).
 */
public final class Inv {
    private Inv() {}

    public static final int SIZE = 36;
    public static final String CURRENCY_NAME = Rules.get().obj("CONFIG").str("CURRENCY_NAME");
    public static final Item CURRENCY = Icon.itemById(Rules.get().obj("CONFIG").str("CURRENCY_ITEM"));

    /** 칸 i (0 ~ 35) 의 아이템 */
    public static ItemStack get(ServerPlayer sp, int i) {
        return sp.getInventory().getItem(i);
    }

    public static void set(ServerPlayer sp, int i, ItemStack stack) {
        sp.getInventory().setItem(i, stack == null ? ItemStack.EMPTY : stack);
    }

    public static int count(ServerPlayer sp, Item item) {
        return item == null ? 0 : count(sp, s -> s.is(item));
    }

    public static int count(ServerPlayer sp, Predicate<ItemStack> match) {
        Inventory inv = sp.getInventory();
        int n = 0;
        for (int i = 0; i < SIZE; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && match.test(s)) n += s.getCount();
        }
        return n;
    }

    /** 가진 화폐 (0.21.0 — 지갑의 실버 + 가방의 은화) */
    public static int money(ServerPlayer sp) {
        return (int) Math.min(Integer.MAX_VALUE, Wallet.total(sp, Economy.Kind.SILVER));
    }

    /** 화폐 내기 (지갑 먼저, 모자라면 가방의 은화 — 모두 합쳐도 모자라면 그대로 false) */
    public static boolean pay(ServerPlayer sp, int amount) {
        return Wallet.pay(sp, Economy.Kind.SILVER, amount);
    }

    /** 화폐 받기 (지갑으로 — 가방 자리가 필요 없음) */
    public static void earn(ServerPlayer sp, int amount) {
        Wallet.give(sp, Economy.Kind.SILVER, amount);
    }

    /** 화폐를 낼 때 가방에서 빠질 은화 (fitsAfter 의 뺄 것 — 지갑으로 모자란 몫만) */
    public static List<Take> payTakes(ServerPlayer sp, int amount) {
        return Wallet.takes(sp, Economy.Kind.SILVER, amount);
    }

    /** amount 개를 앞 칸부터 가져감 (모자라도 있는 만큼 가져감 — 다 가져갔는지) */
    public static boolean remove(ServerPlayer sp, Item item, int amount) {
        if (item == null) return amount <= 0;
        Inventory inv = sp.getInventory();
        int left = amount;
        for (int i = 0; i < SIZE && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !s.is(item)) continue;
            int take = Math.min(left, s.getCount());
            left -= take;
            s.shrink(take);
            if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
        }
        inv.setChanged();
        return left == 0;
    }

    /** 주기 (가득 차면 발밑) */
    public static void give(ServerPlayer sp, ItemStack stack) {
        Notify.give(sp, stack);
    }

    /** 빼고 넣을 것 하나 */
    public record Take(Item item, int n) {}

    /**
     * remove 를 먼저 빼고 (remove 와 같은 순서) add 를 게임처럼 넣어 보면 모두 들어가는지 (같은 아이템 묶음에 겹치고, 남으면 빈 칸) —
     * 사고팔 때 가방이 차서 땅에 떨어지지 않게 (shop.js fitsAfter)
     */
    public static boolean fitsAfter(ServerPlayer sp, List<Take> remove, List<ItemStack> add) {
        Inventory inv = sp.getInventory();
        ItemStack[] slots = new ItemStack[SIZE];
        int[] n = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            ItemStack s = inv.getItem(i);
            slots[i] = s.isEmpty() ? null : s;
            n[i] = s.isEmpty() ? 0 : s.getCount();
        }
        for (Take r : remove) {
            int left = r.n();
            for (int i = 0; i < SIZE && left > 0; i++) {
                if (slots[i] == null || !slots[i].is(r.item())) continue;
                int take = Math.min(left, n[i]);
                left -= take;
                n[i] -= take;
                if (n[i] <= 0) slots[i] = null;
            }
        }
        for (ItemStack st : add) {
            if (st.isEmpty()) continue;
            int left = st.getCount();
            int max = st.getMaxStackSize();
            for (int i = 0; i < SIZE && left > 0; i++) {
                if (slots[i] == null || n[i] >= Math.min(max, slots[i].getMaxStackSize())) continue;
                if (!ItemStack.isSameItemSameComponents(slots[i], st)) continue;
                int k = Math.min(left, max - n[i]);
                n[i] += k;
                left -= k;
            }
            if (left <= 0) continue;
            int free = -1;
            for (int i = 0; i < SIZE; i++) {
                if (slots[i] == null) {
                    free = i;
                    break;
                }
            }
            if (free < 0) return false;
            slots[free] = st;
            n[free] = left;
        }
        return true;
    }

    /** 가방이 가득 차서 사고팔 수 없음: 소리 + 알림, 창 본문 문구 (shop.js bagFull) */
    public static String bagFull(ServerPlayer sp) {
        Notify.sound(sp, SoundEvents.NOTE_BLOCK_BASS, 1F, 0.6F);
        Notify.notify(sp, "§c가방에 빈 칸이 없습니다 §7(한 칸 비우고 다시)", 40);
        return "§4가방에 빈 칸이 없습니다. 한 칸 비우고 다시 해 주세요.§r";
    }
}
