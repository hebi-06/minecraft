package mmorpg.fabric.container;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mmorpg.core.Rules;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgWeaponItem;
import mmorpg.fabric.gear.RpgArmorItem;
import mmorpg.fabric.rune.RuneItem;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 0.23.0 상자 정렬 · 모두 넣기 · 같은 종류 넣기 (사용자 요청 — 상자 창 오른쪽 단추): 상자 · 큰 상자 · 엔더 창고 · 통 · 셜커 상자 (ChestMenu · ShulkerBoxMenu).
 * 쓰레기통처럼 넣으면 지워지는 창 ({@link NoTools}) 은 단추가 없음 (서버가 정함 — 클라이언트가 창을 열면 묻고 서버가 답함).
 * <ul>
 * <li>정렬 (sort): 같은 아이템을 최대 묶음까지 합치고, 무기 → 갑옷 → 룬 → 모드 아이템 (재료 · 동전 · 뽑기권) → 바닐라 순, 같은 종류 안에서는
 * 등급 높은 것 · 레벨 높은 것 먼저 (갑옷은 투구 · 갑옷 · 바지 · 신발), 그다음 아이템 번호 · 많은 묶음 먼저. 상자 칸만 (가방은 그대로).</li>
 * <li>모두 넣기 (deposit): 가방 27칸 (단축 줄 · 갑옷 · 왼손은 빼고) 의 아이템을 Shift + 누르기와 같은 규칙으로 상자에.</li>
 * <li>같은 종류 넣기 (stack): 그중 상자에 이미 있는 종류만.</li>
 * </ul>
 */
public final class ContainerTools {
    private ContainerTools() {}

    /** 정렬 · 넣기를 막는 창 (쓰레기통 — 넣고 닫으면 지워짐) */
    public interface NoTools {}

    public static final List<String> ACTIONS = List.of("sort", "deposit", "stack");

    /** 클라이언트 → 서버: 이 창에 단추를 보여도 되는지 */
    public record QueryPayload(int containerId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<QueryPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("container_query"));
        public static final StreamCodec<FriendlyByteBuf, QueryPayload> CODEC = CustomPacketPayload.codec((p, b) -> b.writeVarInt(p.containerId), b -> new QueryPayload(b.readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 서버 → 클라이언트: 답 (allowed = 정렬 · 넣기 단추를 보임) */
    public record InfoPayload(int containerId, boolean allowed) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<InfoPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("container_info"));
        public static final StreamCodec<FriendlyByteBuf, InfoPayload> CODEC = CustomPacketPayload.codec((p, b) -> {
            b.writeVarInt(p.containerId);
            b.writeBoolean(p.allowed);
        }, b -> new InfoPayload(b.readVarInt(), b.readBoolean()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 클라이언트 → 서버: 단추 누르기 (action = sort · deposit · stack) */
    public record ActionPayload(int containerId, String action) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ActionPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("container_action"));
        public static final StreamCodec<FriendlyByteBuf, ActionPayload> CODEC = CustomPacketPayload.codec((p, b) -> {
            b.writeVarInt(p.containerId);
            b.writeUtf(p.action, 16);
        }, b -> new ActionPayload(b.readVarInt(), b.readUtf(16)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(QueryPayload.TYPE, QueryPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(InfoPayload.TYPE, InfoPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(QueryPayload.TYPE, (p, ctx) -> onQuery(ctx.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (p, ctx) -> onAction(ctx.player(), p));
    }

    /** 단추를 쓸 수 있는 창인지: 상자 (ChestMenu — 상자 · 큰 상자 · 엔더 창고 · 통 · 수레 상자) · 셜커 상자, 쓰레기통 빼고 */
    public static boolean allowed(AbstractContainerMenu m) {
        return (m instanceof ChestMenu || m instanceof ShulkerBoxMenu) && !(m instanceof NoTools);
    }

    static void onQuery(ServerPlayer sp, QueryPayload p) {
        AbstractContainerMenu m = sp.containerMenu;
        boolean ok = m != null && m.containerId == p.containerId() && allowed(m);
        if (ServerPlayNetworking.canSend(sp, InfoPayload.TYPE)) ServerPlayNetworking.send(sp, new InfoPayload(p.containerId(), ok));
    }

    static void onAction(ServerPlayer sp, ActionPayload p) {
        AbstractContainerMenu m = sp.containerMenu;
        if (m == null || m.containerId != p.containerId() || !allowed(m) || sp.isSpectator() || !m.stillValid(sp)) return;
        String msg = run(sp, m, p.action());
        if (msg == null) return;
        m.broadcastChanges();
        Notify.notify(sp, msg, 40);
        boolean ok = msg.startsWith("§a");
        sp.level().playSound(null, sp.getX(), sp.getY() + 1, sp.getZ(), ok ? (p.action().equals("sort") ? SoundEvents.BOOK_PAGE_TURN : SoundEvents.BUNDLE_INSERT)
                : SoundEvents.BUNDLE_INSERT_FAIL, SoundSource.PLAYERS, 0.6F, ok ? 1.0F : 0.8F);
    }

    /** 할 일 (sort · deposit · stack) — 결과 글 (§a 로 시작하면 무엇인가 바뀜), 모르는 할 일은 null */
    public static String run(ServerPlayer sp, AbstractContainerMenu m, String action) {
        return switch (action) {
            case "sort" -> sort(m);
            case "deposit" -> deposit(sp, m, false);
            case "stack" -> deposit(sp, m, true);
            default -> null;
        };
    }

    /** 상자 칸 (플레이어 가방이 아닌 칸 — 창의 앞쪽) */
    public static List<Slot> containerSlots(AbstractContainerMenu m) {
        List<Slot> out = new ArrayList<>();
        for (Slot s : m.slots) if (!(s.container instanceof net.minecraft.world.entity.player.Inventory)) out.add(s);
        return out;
    }

    // ------------------------------------------------------------ 정렬

    /** 정렬: 합치고 · 줄 세워 상자 칸 앞에서부터 */
    public static String sort(AbstractContainerMenu m) {
        List<Slot> slots = containerSlots(m);
        List<ItemStack> items = new ArrayList<>();
        int before = 0;
        for (Slot s : slots) {
            ItemStack st = s.getItem();
            if (st.isEmpty()) continue;
            before++;
            merge(items, st.copy());
        }
        if (before == 0) return "§7정렬할 아이템이 없습니다";
        items.sort(ORDER);
        boolean changed = false;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack want = i < items.size() ? items.get(i) : ItemStack.EMPTY;
            ItemStack now = slots.get(i).getItem();
            if (!ItemStack.matches(now, want)) changed = true;
            slots.get(i).set(want);
        }
        if (!changed) return "§7이미 정렬되어 있습니다";
        return "§a정렬했습니다 §7(" + before + "묶음" + (items.size() < before ? " → 합쳐서 " + items.size() + "묶음" : "") + ")";
    }

    /** 같은 아이템 (구성 요소까지 같음) 의 빈 자리에 먼저 채우고 남으면 새 묶음 */
    static void merge(List<ItemStack> items, ItemStack st) {
        for (ItemStack e : items) {
            if (st.isEmpty()) return;
            if (!ItemStack.isSameItemSameComponents(e, st)) continue;
            int space = e.getMaxStackSize() - e.getCount();
            if (space <= 0) continue;
            int n = Math.min(space, st.getCount());
            e.grow(n);
            st.shrink(n);
        }
        if (!st.isEmpty()) items.add(st);
    }

    /** 종류 (작을수록 앞): 0 무기 · 1 갑옷 · 2 룬 · 3 그 밖의 모드 아이템 · 4 바닐라 */
    public static int category(ItemStack s) {
        Item it = s.getItem();
        if (it instanceof RpgWeaponItem) return 0;
        if (it instanceof RpgArmorItem) return 1;
        if (it instanceof RuneItem) return 2;
        return BuiltInRegistries.ITEM.getKey(it).getNamespace().equals("minecraft") ? 4 : 3;
    }

    /** 등급 (0 일반 ~ 4 초월 · 없으면 -1) — 무기 · 갑옷 · 룬 */
    public static int grade(ItemStack s) {
        Item it = s.getItem();
        if (it instanceof RpgWeaponItem w) return w.weapon().gradeIndex();
        if (it instanceof RpgArmorItem a) return a.piece().gradeIndex();
        if (it instanceof RuneItem r) {
            mmorpg.core.Grade g = Rules.get().grade(r.rune().grade());
            return g == null ? -1 : g.index();
        }
        return -1;
    }

    static int level(ItemStack s) {
        Item it = s.getItem();
        if (it instanceof RpgWeaponItem w) return w.weapon().level();
        if (it instanceof RpgArmorItem a) return a.piece().level();
        return 0;
    }

    static final List<String> PIECES = List.of("helmet", "chest", "legs", "boots");

    static int piece(ItemStack s) {
        return s.getItem() instanceof RpgArmorItem a ? Math.max(0, PIECES.indexOf(a.piece().piece())) : 0;
    }

    static final Comparator<ItemStack> ORDER = Comparator.<ItemStack>comparingInt(ContainerTools::category)
            .thenComparing(Comparator.comparingInt(ContainerTools::grade).reversed())
            .thenComparing(Comparator.comparingInt(ContainerTools::level).reversed())
            .thenComparingInt(s -> s.getItem() instanceof RpgArmorItem a ? a.piece().type().hashCode() : 0)
            .thenComparingInt(ContainerTools::piece)
            .thenComparingInt(s -> BuiltInRegistries.ITEM.getId(s.getItem()))
            .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed())
            .thenComparing(s -> s.getHoverName().getString());

    // ------------------------------------------------------------ 넣기

    /** 가방 27칸 (단축 줄 0 ~ 8 · 갑옷 · 왼손 빼고) → 상자. onlySame 이면 상자에 이미 있는 종류만 */
    public static String deposit(ServerPlayer sp, AbstractContainerMenu m, boolean onlySame) {
        List<Slot> box = containerSlots(m);
        int stacks = 0;
        int count = 0;
        boolean full = false;
        for (Slot s : m.slots) {
            if (s.container != sp.getInventory()) continue;
            int idx = s.getContainerSlot();
            if (idx < 9 || idx >= 36) continue;
            ItemStack st = s.getItem();
            if (st.isEmpty()) continue;
            if (onlySame && !has(box, st)) continue;
            int before = st.getCount();
            Item item = st.getItem();
            ItemStack r = m.quickMoveStack(sp, s.index);
            while (!r.isEmpty() && ItemStack.isSameItem(s.getItem(), r)) r = m.quickMoveStack(sp, s.index);
            ItemStack after = s.getItem();
            int left = after.isEmpty() || after.getItem() != item ? 0 : after.getCount();
            if (left > 0) full = true;
            if (left < before) {
                stacks++;
                count += before - left;
            }
        }
        if (stacks == 0) return full ? "§c상자에 자리가 없습니다" : onlySame ? "§7상자에 있는 종류가 가방에 없습니다" : "§7넣을 아이템이 없습니다 §8(단축 줄은 그대로)";
        return "§a" + (onlySame ? "같은 종류 넣기" : "모두 넣기") + ": §f" + stacks + "묶음 · " + count + "개" + (full ? " §c(자리가 모자라 일부 남음)" : "");
    }

    /** 상자에 같은 종류 (아이템) 가 있는지 */
    static boolean has(List<Slot> box, ItemStack st) {
        for (Slot b : box) if (!b.getItem().isEmpty() && ItemStack.isSameItem(b.getItem(), st)) return true;
        return false;
    }
}
