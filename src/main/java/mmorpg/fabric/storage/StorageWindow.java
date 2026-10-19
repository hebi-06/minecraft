package mmorpg.fabric.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import mmorpg.core.Economy;
import mmorpg.core.Storage;
import mmorpg.fabric.Notify;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.ui.EnderWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.22.0 엔더 창고 창 (사용자 요청 "엔더 상자를 플레이어 전용 창고로, 큰 상자 UI, 최대 5개, 1개는 기본, 2단계는 실버 그다음부터 골드"):
 * 바닐라 큰 상자 창 (9 × 6 — 끌어 놓기 · Shift + 누르기 · 숫자 키 그대로) 의 칸이 지금 상자의 54칸 (StorageData 를 그대로 보고 고침).
 * 상자 바꾸기는 창을 다시 열지 않고 칸 내용만 바꿈 (마우스가 가운데로 튀지 않고, 든 아이템을 그대로 다른 상자로) — 클라이언트의 상자 탭
 * (StorageNet.ActionPayload "page"), 다음 상자 사기 ("buy" — 2번 실버 · 3번부터 골드, 지갑 먼저 · 모자라면 가방의 동전).
 * 여는 곳: 놓인 엔더 상자 블록 (뚜껑 열림 · 닫힘 · 소리 — 블록 이벤트로) · 스탯 창 아랫줄 창고 단추 · /창고. 열 때마다 바닐라 엔더 상자 27칸에 남은
 * 아이템을 1번 상자 (같은 칸 · 차 있으면 빈 칸) 로 옮김. 모드 없는 클라이언트는 탭 대신 "/창고 번호" · "/창고 사기".
 */
public final class StorageWindow {
    private StorageWindow() {}

    /** 창 제목 (모드가 있는 클라이언트 — 상자 번호는 탭에) */
    static final String TITLE = "엔더 창고";
    /** 연출 색 (엔더 보랏빛 — EnderWindow 와 같음) */
    static final double[] COLOR = EnderWindow.COLOR;
    static final double[] GOLD = {1.0, 0.82, 0.3};

    // ------------------------------------------------------------ 창

    /** 창고 창 (큰 상자 9 × 6 — 칸은 view 의 지금 상자) */
    public static final class StorageMenu extends ChestMenu {
        final PageView view;

        StorageMenu(int id, Inventory inv, PageView view) {
            super(MenuType.GENERIC_9x6, id, inv, view, Storage.ROWS);
            this.view = view;
        }

        /** 지금 보는 상자 (0부터) */
        public int page() {
            return view.page;
        }

        /** 놓인 엔더 상자로 열었는지 */
        public boolean fromBlock() {
            return view.chest != null;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            if (player instanceof ServerPlayer sp && !sp.isRemoved() && view.chest == null) EnderWindow.closeFx(sp);
        }
    }

    /**
     * 창고 한 상자의 칸을 그대로 보여 주는 컨테이너 (복사본 없음 — 고치면 바로 StorageData 에). page 를 바꾸면 같은 창의 칸이 다른 상자가 됨.
     * 놓인 엔더 상자로 열었으면 그 블록의 뚜껑을 열고 닫음 (바닐라 엔더 상자처럼 — 블록 이벤트 · 소리).
     */
    static final class PageView implements Container {
        final ServerPlayer owner;
        final StorageData data;
        final ServerLevel chestLevel;
        final BlockPos chest;
        int page;
        /** 시험용: 칸이 바뀐 횟수 */
        int changes;

        PageView(ServerPlayer owner, StorageData data, int page, ServerLevel chestLevel, BlockPos chest) {
            this.owner = owner;
            this.data = data;
            this.page = page;
            this.chestLevel = chestLevel;
            this.chest = chest;
        }

        NonNullList<ItemStack> items() {
            return data.page(page);
        }

        @Override
        public int getContainerSize() {
            return Storage.SIZE;
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack s : items()) if (!s.isEmpty()) return false;
            return true;
        }

        @Override
        public ItemStack getItem(int i) {
            return i >= 0 && i < Storage.SIZE ? items().get(i) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int i, int n) {
            if (i < 0 || i >= Storage.SIZE) return ItemStack.EMPTY;
            ItemStack out = ContainerHelper.removeItem(items(), i, n);
            if (!out.isEmpty()) setChanged();
            return out;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            return i >= 0 && i < Storage.SIZE ? ContainerHelper.takeItem(items(), i) : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int i, ItemStack stack) {
            if (i < 0 || i >= Storage.SIZE) return;
            items().set(i, stack);
            stack.limitSize(getMaxStackSize(stack));
            setChanged();
        }

        @Override
        public void setChanged() {
            changes++;
        }

        @Override
        public boolean stillValid(Player player) {
            if (player != owner || owner.isRemoved()) return false;
            if (chest == null) return true;
            return chestLevel.getBlockEntity(chest) instanceof EnderChestBlockEntity be && Container.stillValidBlockEntity(be, player);
        }

        @Override
        public void clearContent() {
            items().clear();
        }

        @Override
        public void startOpen(ContainerUser user) {
            if (chest != null) Lids.open(chestLevel, chest);
        }

        @Override
        public void stopOpen(ContainerUser user) {
            if (chest != null) Lids.close(chestLevel, chest);
        }
    }

    /** 놓인 엔더 상자의 뚜껑 (여는 사람 수 — 바닐라 ContainerOpenersCounter 대신 블록 이벤트 1 로 직접) */
    static final class Lids {
        private Lids() {}

        private record Key(ResourceKey<Level> dim, BlockPos pos) {}

        private static final Map<Key, Integer> OPEN = new HashMap<>();

        static void open(ServerLevel level, BlockPos pos) {
            Key k = new Key(level.dimension(), pos.immutable());
            int n = OPEN.merge(k, 1, Integer::sum);
            if (!level.getBlockState(pos).is(Blocks.ENDER_CHEST)) return;
            level.blockEvent(pos, Blocks.ENDER_CHEST, 1, n);
            if (n == 1) sound(level, pos, true);
        }

        static void close(ServerLevel level, BlockPos pos) {
            Key k = new Key(level.dimension(), pos.immutable());
            Integer had = OPEN.get(k);
            int n = had == null ? 0 : Math.max(0, had - 1);
            if (n == 0) OPEN.remove(k);
            else OPEN.put(k, n);
            if (!level.getBlockState(pos).is(Blocks.ENDER_CHEST)) return;
            level.blockEvent(pos, Blocks.ENDER_CHEST, 1, n);
            if (n == 0) sound(level, pos, false);
        }

        private static void sound(ServerLevel level, BlockPos pos, boolean open) {
            level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, open ? SoundEvents.ENDER_CHEST_OPEN : SoundEvents.ENDER_CHEST_CLOSE,
                    SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
        }

        /** 시험용: 그 자리를 여는 사람 수 */
        static int openers(ServerLevel level, BlockPos pos) {
            return OPEN.getOrDefault(new Key(level.dimension(), pos.immutable()), 0);
        }

        static void clear() {
            OPEN.clear();
        }
    }

    // ------------------------------------------------------------ 열기

    /** 창고를 엶 — page 는 0부터 (-1 = 마지막으로 본 상자), chest = 놓인 엔더 상자 (없으면 어디서나). 열었으면 true */
    public static boolean open(ServerPlayer sp, int page, ServerLevel chestLevel, BlockPos chest) {
        if (sp == null || sp.isRemoved()) return false;
        if (sp.isSpectator()) {
            Notify.notify(sp, "§c관전 모드에서는 엔더 창고를 열 수 없습니다", 60);
            return false;
        }
        migrate(sp);
        StorageData d = StorageData.of(sp);
        int p = page < 0 ? d.last() : page;
        if (p >= d.unlocked()) {
            Notify.notify(sp, "§c" + Storage.label(p + 1) + "는 아직 잠겨 있습니다 §7(/창고 사기)", 60);
            p = d.last();
        }
        d.last = p;
        PageView view = new PageView(sp, d, p, chestLevel, chest == null ? null : chest.immutable());
        boolean mod = StorageNet.canShow(sp);
        Component title = Component.literal(mod ? TITLE : TITLE + " · " + Storage.label(p + 1) + " (" + d.unlocked() + "/" + Storage.chests() + ")");
        sp.openMenu(new SimpleMenuProvider((id, inv, pl) -> new StorageMenu(id, inv, view), title));
        if (!(sp.containerMenu instanceof StorageMenu m) || m.view != view) return false;
        sendState(sp, m);
        sp.awardStat(net.minecraft.stats.Stats.OPEN_ENDERCHEST);
        if (chest == null) EnderWindow.openFx(sp);
        else blockFx(chestLevel, chest);
        if (!mod) Notify.say(sp, "§d엔더 창고 §f" + Storage.label(p + 1) + " §7— 다른 상자: /창고 번호 · 다음 상자 사기: /창고 사기");
        return true;
    }

    /** 놓인 엔더 상자를 열 때: 뚜껑 위로 거꾸로 포털 알갱이 (소리는 뚜껑이 냄) */
    static void blockFx(ServerLevel level, BlockPos pos) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3 top = new Vec3(pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5);
        for (int i = 0; i < 10; i++) {
            SkillFx.vanilla(level, "minecraft:portal_reverse_particle", top.add((r.nextDouble() - 0.5) * 0.8, r.nextDouble() * 0.6, (r.nextDouble() - 0.5) * 0.8));
        }
    }

    /** 시험용: 놓인 엔더 상자를 지금 여는 사람 수 (뚜껑) */
    public static int lidOpeners(ServerLevel level, BlockPos pos) {
        return Lids.openers(level, pos);
    }

    /** 열린 창고 창 (없으면 null) */
    public static StorageMenu menu(ServerPlayer sp) {
        return sp.containerMenu instanceof StorageMenu m ? m : null;
    }

    // ------------------------------------------------------------ 상자 바꾸기 · 사기

    static void onAction(ServerPlayer sp, StorageNet.ActionPayload p) {
        StorageMenu m = menu(sp);
        if (m == null || m.containerId != p.containerId()) return; // 늦게 온 · 다른 창
        switch (p.action()) {
            case "page" -> switchPage(sp, p.page());
            case "buy" -> {
                String r = buy(sp); // 성공은 buy 가 화면에 알림 · 실패는 까닭을 화면에
                if (!r.startsWith("§a")) {
                    Notify.notify(sp, r, 70);
                    sendState(sp, m);
                }
            }
            default -> { }
        }
    }

    /** 열린 창의 상자를 바꿈 (창은 그대로 — 칸 내용만). 바꿨으면 true */
    public static boolean switchPage(ServerPlayer sp, int page) {
        StorageMenu m = menu(sp);
        if (m == null) return false;
        StorageData d = m.view.data;
        if (page < 0 || page >= d.unlocked()) {
            Notify.notify(sp, "§c" + Storage.label(page + 1) + "는 아직 잠겨 있습니다", 40);
            sendState(sp, m);
            return false;
        }
        if (m.view.page == page) return true;
        m.view.page = page;
        d.last = page;
        m.sendAllDataToRemote();
        sendState(sp, m);
        SkillFx.playFor(sp, "item.book.page_turn", 1.1, 0.8);
        return true;
    }

    /**
     * 다음 상자 사기 (앞에서부터 차례로 — 2번 실버 · 3번부터 골드): 지갑 먼저, 모자라면 가방의 동전. 결과 글 (성공은 §a 로 시작).
     * 창이 열려 있으면 산 상자로 바꿈.
     */
    public static String buy(ServerPlayer sp) {
        StorageData d = StorageData.of(sp);
        int next = Storage.next(d.unlocked());
        if (next < 0) return "§7엔더 창고의 상자 " + Storage.chests() + "개가 모두 열려 있습니다";
        Storage.Price price = Storage.price(next);
        if (price == null) return "§c" + Storage.label(next) + "의 값이 설정에 없습니다 (rules_more.json STORAGE.prices)";
        long have = Wallet.total(sp, price.kind());
        if (have < price.amount() || !Wallet.pay(sp, price.kind(), price.amount())) {
            mmorpg.fabric.shop.Shop.angry(sp);
            return "§c" + price.kind().label() + "가 모자랍니다 §7(" + Storage.label(next) + ": " + price.text() + " §7· 보유 " + Economy.amount(price.kind(), have) + "§7)";
        }
        d.unlocked = next;
        buyFx(sp, price.kind());
        Notify.notify(sp, "§d엔더 창고 §f" + Storage.label(next) + " §d열림! §7(" + price.text() + "§7)", 70);
        StorageMenu m = menu(sp);
        if (m != null) {
            m.view.page = next - 1;
            d.last = next - 1;
            m.sendAllDataToRemote();
            sendState(sp, m);
        }
        return "§a엔더 창고 " + Storage.label(next) + "를 열었습니다 §7(" + price.text() + " §7· 열린 상자 " + next + "/" + Storage.chests() + ")";
    }

    /** 살 때: 발밑 보랏빛 룬 둘 · 금빛 (골드) / 은빛 (실버) 반짝임 · 소리 */
    static void buyFx(ServerPlayer sp, Economy.Kind kind) {
        ServerLevel level = sp.level();
        Vec3 feet = sp.position();
        SkillFx.rune(level, feet, Opt.of("size", 1.6, "life", 0.9, "color", COLOR, "alpha", 0.95, "progress", 1, "rate", 220, "grow", 0.4));
        SkillFx.shock(level, feet, COLOR, 2.4, 0.5);
        SkillFx.sparks(level, V.up(feet, 1.1), kind == Economy.Kind.GOLD ? GOLD : new double[] {0.85, 0.88, 0.95}, 18, 1.8);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 14; i++) {
            SkillFx.vanilla(level, "minecraft:portal_reverse_particle", feet.add((r.nextDouble() - 0.5) * 1.6, 0.2 + r.nextDouble() * 1.8, (r.nextDouble() - 0.5) * 1.6));
        }
        SkillFx.playFor(sp, "random.levelup", 1.3, 0.7);
        SkillFx.playFor(sp, "random.enderchestopen", 0.8, 0.9);
    }

    /** 창고 상태를 클라이언트에 (열린 창이 있을 때 — 탭) */
    static void sendState(ServerPlayer sp, StorageMenu m) {
        StorageData d = m.view.data;
        List<String> prices = new ArrayList<>();
        List<Integer> stacks = new ArrayList<>();
        for (int c = 1; c <= Storage.chests(); c++) {
            Storage.Price p = Storage.price(c);
            prices.add(p == null ? "" : p.kind().id + ":" + p.amount());
            stacks.add(c <= d.unlocked() ? d.stacks(c - 1) : 0);
        }
        StorageNet.send(sp, new StorageNet.StatePayload(m.containerId, m.view.page, d.unlocked(), Storage.chests(), prices, stacks));
    }

    // ------------------------------------------------------------ 바닐라 엔더 상자 → 1번 상자

    /** 바닐라 엔더 상자 27칸의 아이템을 창고로 (1번 상자의 같은 칸 — 차 있으면 앞 상자부터 빈 칸). 옮긴 묶음 수 */
    public static int migrate(ServerPlayer sp) {
        if (!Storage.migrateEnderChest()) return 0;
        PlayerEnderChestContainer ec = sp.getEnderChestInventory();
        if (ec.isEmpty()) return 0;
        StorageData d = StorageData.of(sp);
        int moved = 0;
        for (int i = 0; i < ec.getContainerSize(); i++) {
            ItemStack s = ec.getItem(i);
            if (s.isEmpty()) continue;
            int[] at = i < Storage.SIZE && d.page(0).get(i).isEmpty() ? new int[] {0, i} : d.firstEmpty();
            if (at == null) break;
            d.page(at[0]).set(at[1], s.copy());
            ec.setItem(i, ItemStack.EMPTY);
            moved++;
        }
        if (moved > 0) {
            ec.setChanged();
            Notify.say(sp, "§d엔더 창고: §f엔더 상자에 있던 " + moved + "묶음을 1번 상자로 옮겼습니다 §7(엔더 상자 · 스탯 창 · /창고 로 엶)");
        }
        if (!ec.isEmpty()) Notify.say(sp, "§c엔더 창고에 빈 칸이 없어 엔더 상자에 아이템이 남았습니다 §7— 창고를 비우고 다시 열면 옮겨집니다");
        return moved;
    }
}
