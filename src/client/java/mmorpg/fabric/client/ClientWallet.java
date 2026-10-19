package mmorpg.fabric.client;

import mmorpg.core.Economy;
import mmorpg.fabric.economy.EconomyItems;
import mmorpg.fabric.economy.EconomyNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 0.21.0 서버가 보낸 지갑 (골드 · 실버) — 화면 왼쪽 아래 지갑 ({@link WalletHud}) · 장식 창 (골드) · 스탯 트리 창 (실버). 바뀌면 보이는 숫자가
 * 굴러가듯 따라가고 (틱마다 남은 차이의 30%), 바뀐 양 (+12 · −40) 이 숫자 옆에 잠깐 떠오름 (이어서 바뀌면 합쳐서).
 */
public final class ClientWallet {
    private ClientWallet() {}

    /** 바뀐 양 표시: 양 · 시각 (밀리초) */
    record Pop(long delta, long at) {}

    /** 떠오른 글자가 머무는 시간 (밀리초) */
    static final long POP_MS = 1600;
    private static boolean received;
    private static final long[] VALUE = new long[2];
    private static final double[] SHOWN = {-1, -1};
    private static final Pop[] POP = new Pop[2];

    static void update(EconomyNet.WalletPayload p) {
        long[] next = {p.gold(), p.silver()};
        long now = Util.getMillis();
        for (int i = 0; i < 2; i++) {
            long d = next[i] - VALUE[i];
            if (received && d != 0) {
                Pop old = POP[i];
                POP[i] = old != null && now - old.at() < POP_MS / 2 && Long.signum(old.delta()) == Long.signum(d) ? new Pop(old.delta() + d, now) : new Pop(d, now);
            }
            VALUE[i] = next[i];
            if (!received || SHOWN[i] < 0) SHOWN[i] = next[i];
        }
        received = true;
    }

    static void clear() {
        received = false;
        VALUE[0] = VALUE[1] = 0;
        SHOWN[0] = SHOWN[1] = -1;
        POP[0] = POP[1] = null;
    }

    /** 보이는 숫자를 실제 값으로 (틱마다) */
    static void tick() {
        for (int i = 0; i < 2; i++) {
            if (SHOWN[i] < 0) continue;
            double diff = VALUE[i] - SHOWN[i];
            SHOWN[i] = Math.abs(diff) < 0.5 ? VALUE[i] : SHOWN[i] + diff * 0.3;
        }
    }

    /** 이 서버에서 지갑을 받았는지 */
    public static boolean received() {
        return received;
    }

    /** 지갑의 수 */
    public static long get(Economy.Kind k) {
        return VALUE[k.ordinal()];
    }

    /** 굴러가는 중의 보이는 수 */
    static long shown(Economy.Kind k) {
        double v = SHOWN[k.ordinal()];
        return v < 0 ? VALUE[k.ordinal()] : Math.round(v);
    }

    /** 떠오른 바뀐 양 (없거나 끝났으면 null) */
    static Pop pop(Economy.Kind k) {
        Pop p = POP[k.ordinal()];
        return p != null && Util.getMillis() - p.at() < POP_MS ? p : null;
    }

    /** 가방 (핫바 + 36칸) 의 같은 동전 수 — 서버 Wallet.coins 와 같게 */
    public static int coins(Economy.Kind k) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return 0;
        Item coin = EconomyItems.coin(k);
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(coin)) n += s.getCount();
        }
        return n;
    }

    /** 낼 수 있는 수 = 지갑 + 가방의 동전 (서버 Wallet.total 과 같게) */
    public static long total(Economy.Kind k) {
        return get(k) + coins(k);
    }
}
