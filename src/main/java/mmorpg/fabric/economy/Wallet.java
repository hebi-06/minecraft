package mmorpg.fabric.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import mmorpg.core.Economy;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Props;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.ui.Inv;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * 0.21.0 지갑 (사용자 요청 — 화폐를 에메랄드에서 골드 · 실버로): 플레이어마다 골드 · 실버 숫자. 플레이어 데이터에 붙여 저장 (가방과 함께 저장되므로
 * 동전을 꺼내고 넣는 사이에 서버가 꺼져도 어긋나지 않음 · 죽어도 그대로). 화면 왼쪽 아래에 표시 (모드 클라이언트 — {@link EconomyNet.WalletPayload}).
 * <ul>
 * <li>내기: 지갑에서 먼저, 모자라면 가방의 같은 동전으로 (모자라면 아무것도 바꾸지 않음)</li>
 * <li>받기: 지갑으로 (가득이면 남은 몫은 동전으로)</li>
 * <li>동전 우클릭 = 손의 묶음을 지갑에 (웅크리면 가방의 같은 동전 모두) · "/골드 수" · "/실버 수" = 동전으로 꺼내기</li>
 * <li>처음 접속 (0.21.0 이후 한 번): 가방 · 엔더 상자의 에메랄드 (블록은 9개) 를 실버로 1:1 · "/환전" = 가방의 에메랄드를 실버로 (ECONOMY.emeraldExchange)</li>
 * </ul>
 */
public final class Wallet {
    private Wallet() {}

    /** 저장 값: 골드 · 실버 */
    public record Purse(long gold, long silver) {
        public static final Purse EMPTY = new Purse(0, 0);
        public static final Codec<Purse> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.LONG.optionalFieldOf("gold", 0L).forGetter(Purse::gold),
                        Codec.LONG.optionalFieldOf("silver", 0L).forGetter(Purse::silver))
                .apply(i, Purse::new));

        public long of(Economy.Kind k) {
            return k == Economy.Kind.GOLD ? gold : silver;
        }

        Purse with(Economy.Kind k, long v) {
            long c = Math.max(0, Math.min(Economy.walletMax(), v));
            return k == Economy.Kind.GOLD ? new Purse(c, silver) : new Purse(gold, c);
        }
    }

    public static AttachmentType<Purse> TYPE;
    /** 에메랄드를 실버로 바꿨는지 (플레이어 속성 — 한 번만) */
    public static final String MIGRATED = "rpg:econ_migrated";
    static final double[] GOLD_FX = {1.0, 0.82, 0.3};
    static final double[] SILVER_FX = {0.86, 0.9, 1.0};

    static void init() {
        TYPE = AttachmentRegistry.create(Mmorpg.id("wallet"), b -> b.persistent(Purse.CODEC).copyOnDeath().initializer(() -> Purse.EMPTY));
    }

    // ------------------------------------------------------------ 숫자

    public static Purse purse(ServerPlayer sp) {
        Purse p = sp == null ? null : sp.getAttached(TYPE);
        return p == null ? Purse.EMPTY : p;
    }

    /** 지갑의 수 */
    public static long get(ServerPlayer sp, Economy.Kind k) {
        return purse(sp).of(k);
    }

    /** 가방 (36칸) 의 같은 동전 수 */
    public static int coins(ServerPlayer sp, Economy.Kind k) {
        return Inv.count(sp, EconomyItems.coin(k));
    }

    /** 낼 수 있는 수 = 지갑 + 가방의 동전 */
    public static long total(ServerPlayer sp, Economy.Kind k) {
        return get(sp, k) + coins(sp, k);
    }

    static void put(ServerPlayer sp, Purse p) {
        if (p.equals(purse(sp))) return;
        sp.setAttached(TYPE, p);
        sync(sp);
    }

    /** 바로 정함 (운영자 · 시험) */
    public static void set(ServerPlayer sp, Economy.Kind k, long n) {
        put(sp, purse(sp).with(k, n));
    }

    /** 지갑에 더함 (0 이하는 무시 · 최대에서 멈춤) — 더한 양 */
    public static long add(ServerPlayer sp, Economy.Kind k, long n) {
        if (sp == null || n <= 0) return 0;
        Purse p = purse(sp);
        long cur = p.of(k);
        long max = Economy.walletMax();
        long next = cur > max - n ? max : cur + n;
        put(sp, p.with(k, next));
        return Math.max(0, next - cur);
    }

    /** 받기 (보상 · 판매 대금 · 환전): 지갑으로, 가득이면 남은 몫은 동전으로 가방에 (가방도 가득이면 발밑) */
    public static void give(ServerPlayer sp, Economy.Kind k, long n) {
        long rest = n - add(sp, k, n);
        Item coin = EconomyItems.coin(k);
        for (int i = 0; rest > 0 && i < 64; i++) {
            int c = (int) Math.min(64, rest);
            Inv.give(sp, new ItemStack(coin, c));
            rest -= c;
        }
    }

    /** 내기: 지갑에서 먼저, 모자라면 가방의 같은 동전 — 모두 합쳐도 모자라면 아무것도 바꾸지 않고 false */
    public static boolean pay(ServerPlayer sp, Economy.Kind k, long n) {
        if (n <= 0) return true;
        if (sp == null) return false;
        long w = get(sp, k);
        if (w >= n) {
            put(sp, purse(sp).with(k, w - n));
            return true;
        }
        long need = n - w;
        if (coins(sp, k) < need) return false;
        if (!Inv.remove(sp, EconomyItems.coin(k), (int) need)) return false;
        put(sp, purse(sp).with(k, 0));
        return true;
    }

    /** 낼 때 가방에서 빠질 동전 (Inv.fitsAfter 의 뺄 것 — 지갑으로 모자란 몫만) */
    public static List<Inv.Take> takes(ServerPlayer sp, Economy.Kind k, long n) {
        long need = n - get(sp, k);
        return need <= 0 ? List.of() : List.of(new Inv.Take(EconomyItems.coin(k), (int) Math.min(Integer.MAX_VALUE, need)));
    }

    /** 클라이언트에 보냄 (모드가 있으면 — 화면 왼쪽 아래 지갑) */
    public static void sync(ServerPlayer sp) {
        if (sp == null || sp.isRemoved()) return;
        Purse p = purse(sp);
        EconomyNet.send(sp, p.gold(), p.silver());
    }

    // ------------------------------------------------------------ 동전 넣기 · 꺼내기

    /** 동전 우클릭: 손의 묶음을 지갑에 (all 이면 가방 · 왼손의 같은 동전 모두). 넣은 수 */
    public static long deposit(ServerPlayer sp, InteractionHand hand, Economy.Kind k, boolean all) {
        Item coin = EconomyItems.coin(k);
        long got = 0;
        ItemStack held = sp.getItemInHand(hand);
        if (held.is(coin)) got += depositStack(sp, held, k);
        if (all) {
            Inventory inv = sp.getInventory();
            for (int i = 0; i < Inv.SIZE; i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(coin)) got += depositStack(sp, s, k);
            }
            ItemStack off = sp.getOffhandItem();
            if (off.is(coin)) got += depositStack(sp, off, k);
        }
        if (got <= 0) {
            if (get(sp, k) >= Economy.walletMax()) Notify.warn(sp, "§c지갑이 가득 찼습니다 §7(" + k.label() + " " + Economy.fmt(Economy.walletMax()) + ")");
            return 0;
        }
        sp.getInventory().setChanged();
        coinFx(sp, k, got, true);
        Notify.notify(sp, k.color() + "+" + Economy.fmt(got) + " " + k.label() + " §7→ 지갑 " + k.color() + Economy.fmt(get(sp, k)), 40);
        return got;
    }

    private static long depositStack(ServerPlayer sp, ItemStack s, Economy.Kind k) {
        long added = add(sp, k, s.getCount());
        s.shrink((int) added);
        return added;
    }

    /** "/골드 수" · "/실버 수": 지갑에서 동전으로 꺼냄 (가방에 다 들어갈 때만). 결과 글 (성공은 §a 로 시작) */
    public static String withdraw(ServerPlayer sp, Economy.Kind k, long n) {
        if (n <= 0) return "§c1 이상을 적어 주세요";
        long have = get(sp, k);
        if (have < n) return "§c지갑의 " + k.label() + "가 모자랍니다 §7(지갑 " + Economy.fmt(have) + ")";
        Item coin = EconomyItems.coin(k);
        long room = room(sp, coin);
        if (room < n) return "§c가방에 자리가 모자랍니다 §7(" + Economy.fmt(room) + "개까지 꺼낼 수 있음)";
        put(sp, purse(sp).with(k, have - n));
        for (long left = n; left > 0; left -= 64) Inv.give(sp, new ItemStack(coin, (int) Math.min(64, left)));
        coinFx(sp, k, n, false);
        return "§a" + coinName(k) + " " + Economy.fmt(n) + "개를 꺼냈습니다 §7(지갑 " + k.color() + Economy.fmt(have - n) + "§7)";
    }

    /** 가방 (36칸) 에 이 동전이 더 들어갈 수 */
    static long room(ServerPlayer sp, Item coin) {
        ItemStack plain = new ItemStack(coin);
        int max = plain.getMaxStackSize();
        long r = 0;
        for (int i = 0; i < Inv.SIZE; i++) {
            ItemStack s = Inv.get(sp, i);
            if (s.isEmpty()) r += max;
            else if (ItemStack.isSameItemSameComponents(s, plain)) r += Math.max(0, max - s.getCount());
        }
        return r;
    }

    static String coinName(Economy.Kind k) {
        return k == Economy.Kind.GOLD ? "§6금화§a" : "§f은화§a";
    }

    /** 동전 소리 · 손 앞 반짝임 (넣기 = 높은 소리 · 빛이 손으로, 꺼내기 = 낮은 소리) */
    static void coinFx(ServerPlayer sp, Economy.Kind k, long n, boolean in) {
        boolean gold = k == Economy.Kind.GOLD;
        SkillFx.playFor(sp, gold ? "armor.equip_gold" : "use.chain", in ? 1.5 : 1.1, 0.8);
        SkillFx.playFor(sp, "random.orb", gold ? 1.7 : 1.3, in ? 0.5 : 0.3);
        Vec3 hand = sp.getEyePosition().add(sp.getLookAngle().scale(0.7)).add(0, -0.35, 0);
        SkillFx.sparks(sp.level(), hand, gold ? GOLD_FX : SILVER_FX, (int) Math.min(14, 4 + n / 8), in ? 1.4 : 0.8);
    }

    // ------------------------------------------------------------ 에메랄드 → 실버

    /** 처음 접속 (0.21.0 이후 한 번 — ECONOMY.migrateEmeralds): 가방 · 엔더 상자의 에메랄드를 실버로 1:1. 바꾼 수 */
    public static long migrate(ServerPlayer sp) {
        if (sp == null || Props.get(sp, MIGRATED) != null) return 0;
        Props.set(sp, MIGRATED, "1");
        if (!Economy.cfg().truthy("migrateEmeralds")) return 0;
        int bag = takeEmeralds(sp.getInventory());
        int ender = takeEmeralds(sp.getEnderChestInventory());
        long n = (long) bag + ender;
        if (n <= 0) return 0;
        give(sp, Economy.Kind.SILVER, n);
        Notify.say(sp, "§a화폐가 바뀌었습니다: §f에메랄드 " + Economy.fmt(n) + "개 §7→ §f실버 " + Economy.fmt(n) + " §8(가방 " + bag + " · 엔더 상자 " + ender + ")");
        Notify.say(sp, "§7지갑은 화면 왼쪽 아래 · 상점 = 실버, 유저 장터 · 장식 = 골드 · 상자에 둔 에메랄드는 가방에 넣고 §f/환전");
        coinFx(sp, Economy.Kind.SILVER, n, true);
        return n;
    }

    /** "/환전": 가방의 에메랄드 (블록은 9개) 를 실버로 1:1 (ECONOMY.emeraldExchange). 결과 글 (성공은 §a 로 시작) */
    public static String exchange(ServerPlayer sp) {
        if (!Economy.cfg().truthy("emeraldExchange")) return "§c이 서버에서는 환전을 쓰지 않습니다";
        int n = takeEmeralds(sp.getInventory());
        if (n <= 0) return "§7가방에 에메랄드가 없습니다 §8(에메랄드 · 에메랄드 블록 → 실버 1:1 · 9)";
        give(sp, Economy.Kind.SILVER, n);
        coinFx(sp, Economy.Kind.SILVER, n, true);
        return "§a환전: §f에메랄드 " + Economy.fmt(n) + "개 §7→ §f실버 " + Economy.fmt(n) + " §7(지갑 " + Economy.fmt(get(sp, Economy.Kind.SILVER)) + ")";
    }

    /** 컨테이너의 에메랄드 (에메랄드 블록은 9개로) 를 모두 빼고 그 수 */
    static int takeEmeralds(Container c) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.is(Items.EMERALD)) n += s.getCount();
            else if (s.is(Items.EMERALD_BLOCK)) n += s.getCount() * 9;
            else continue;
            c.setItem(i, ItemStack.EMPTY);
        }
        if (n > 0) c.setChanged();
        return n;
    }
}
