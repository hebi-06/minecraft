package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import mmorpg.core.Armor;
import mmorpg.core.Economy;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.client.StatScreen;
import mmorpg.fabric.client.StorageTabs;
import mmorpg.fabric.client.form.ChestFormScreen;
import mmorpg.fabric.client.mixin.AbstractContainerScreenAccessor;
import mmorpg.fabric.client.rpgui.CharacterPanel;
import mmorpg.fabric.client.rpgui.ContainerButtons;
import mmorpg.fabric.container.ContainerTools;
import mmorpg.fabric.economy.EconomyItems;
import mmorpg.fabric.economy.TrashWindow;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.shop.Store;
import mmorpg.fabric.storage.StorageWindow;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.23.0 RPG 창 사진첩 (화면 시험 — 키 rpgui, 사용자 요청 "인벤토리 UI 랑 상자 UI 를 RPG 느낌으로" → 시안 A 어둠의 금테):
 * <ol>
 * <li>인벤토리: 금테 창 · 남빛 사람 칸 · 등급 색 칸 · 왼쪽 캐릭터 정보 패널 (레벨 40 · 스탯 · 피해 · 골드 · 실버), 무기 위 금빛 강조 · 풀이, 스탯 줄 풀이,
 * 패널 단추 → 스탯 창, 조합법 책을 열면 패널이 숨음 (ui_inventory · ui_inventory_hover · ui_panel_stat · ui_panel_button · ui_inventory_book)</li>
 * <li>상자 (놓인 상자를 우클릭): 금테 · 오른쪽 정렬 · 모두 넣기 · 같은 종류 단추 (서버가 허락), 누르면 상자 칸이 바뀜 (ui_chest · ui_chest_sort_tip ·
 * ui_chest_sorted · ui_chest_deposit)</li>
 * <li>큰 상자 · 셜커 상자 · 엔더 창고 (상자 탭) · 쓰레기통 (단추 없음) (ui_large_chest · ui_shulker · ui_storage · ui_trash)</li>
 * <li>모드 상자 모양 창: 무기 상점 · 유저 장터 (ui_shop · ui_market)</li>
 * </ol>
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgUiGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-rpgui-gallery");
    private static final BlockPos BOX = new BlockPos(0, -60, 2);
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("rpgui")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("tp @a 0.5 -60 0.5 0 29");
            ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
            ctx.waitTicks(20);
            setup(server);
            ctx.waitTicks(20);
            part(ctx, "inventory", () -> inventory(ctx, server, conn));
            closeAll(ctx, server);
            part(ctx, "chest", () -> chest(ctx, server, conn));
            closeAll(ctx, server);
            part(ctx, "large", () -> largeChest(ctx, server, conn));
            closeAll(ctx, server);
            part(ctx, "shulker", () -> shulker(ctx, server, conn));
            closeAll(ctx, server);
            part(ctx, "storage", () -> storage(ctx, server, conn));
            closeAll(ctx, server);
            part(ctx, "trash", () -> trash(ctx, server, conn));
            closeAll(ctx, server);
            part(ctx, "forms", () -> forms(ctx, server, conn));
            ctx.setScreen(() -> null);
        }
        LOG.info("[rpgui-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
    }

    private static ItemStack weapon(String type, int level, String grade) {
        return new ItemStack(RpgItems.byId(Weapons.weaponFrom(type, level, grade).id()));
    }

    private static ItemStack armor(String type, int level, String grade, String piece) {
        return new ItemStack(GearItems.byId(Armor.armorId(type, level, grade, piece)));
    }

    /** 40렙 · 힘 110 · 체력 15 · 초월 힘 갑옷 · 등급마다 무기 · 룬 · 재료 · 지갑 */
    private void setup(TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.setExperienceLevels(40);
            RpgPlayers.checkLevelUp(sp);
            RpgPlayers.spend(sp, "str", 110);
            RpgPlayers.spend(sp, "vit", 15);
            sp.getInventory().clearContent();
            sp.setItemSlot(EquipmentSlot.HEAD, armor("str", 40, "transcend", "helmet"));
            sp.setItemSlot(EquipmentSlot.CHEST, armor("str", 40, "transcend", "chest"));
            sp.setItemSlot(EquipmentSlot.LEGS, armor("str", 40, "transcend", "legs"));
            sp.setItemSlot(EquipmentSlot.FEET, armor("str", 40, "transcend", "boots"));
            sp.setItemSlot(EquipmentSlot.OFFHAND, weapon("shield", 30, "hero"));
            var inv = sp.getInventory();
            inv.setItem(0, weapon("sword", 40, "transcend"));
            inv.setItem(1, RuneItems.stack("blade", "transcend"));
            inv.setItem(2, RuneItems.stack("body", "hero"));
            inv.setItem(3, RuneItems.stack("axe", "noble"));
            inv.setItem(4, new ItemStack(EconomyItems.SILVER_COIN, 20));
            inv.setItem(9, weapon("axe", 40, "hero"));
            inv.setItem(10, weapon("bow", 40, "noble"));
            inv.setItem(11, weapon("wand", 30, "rare"));
            inv.setItem(12, weapon("dagger", 20, "common"));
            inv.setItem(13, new ItemStack(GearItems.byId("rpg:enhance_stone"), 12));
            inv.setItem(14, new ItemStack(GearItems.byId("rpg:star_ore"), 5));
            inv.setItem(18, armor("str", 30, "common", "helmet"));
            inv.setItem(19, new ItemStack(EconomyItems.GOLD_COIN, 30));
            inv.setItem(20, new ItemStack(Items.DIAMOND, 7));
            inv.setItem(21, new ItemStack(Items.COBBLESTONE, 64));
            inv.setItem(22, new ItemStack(Items.COBBLESTONE, 40));
            inv.setItem(27, new ItemStack(GearItems.byId("rpg:rune_shard"), 18));
            inv.setSelectedSlot(0);
            Wallet.set(sp, Economy.Kind.GOLD, 1271);
            Wallet.set(sp, Economy.Kind.SILVER, 56849);
            RpgNet.sync(sp, false);
        });
    }

    // ------------------------------------------------------------ 1. 인벤토리

    private void inventory(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        ctx.getInput().pressKey(o -> o.keyInventory);
        if (!waitScreen(ctx, sc -> sc instanceof InventoryScreen, "inventory (E)")) ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        ctx.waitTicks(5);
        int[] geo = ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            CharacterPanel p = panel(sc);
            AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) sc;
            return p == null ? new int[] {-1} : new int[] {p.shown() ? 1 : 0, p.getX(), p.getY(), a.mmorpg$leftPos(), a.mmorpg$topPos()};
        });
        check(geo.length == 5 && geo[0] == 1 && geo[1] + CharacterPanel.W < geo[3] && geo[1] >= 0 && geo[2] == geo[4],
                "character panel left of the inventory " + java.util.Arrays.toString(geo));
        boolean armored = ctx.computeOnClient(mc -> !mc.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !mc.player.getItemBySlot(EquipmentSlot.FEET).isEmpty());
        check(armored, "transcend armor stays on (level 40 · str 110)");
        away(ctx);
        shot(ctx, "ui_inventory");
        hover(ctx, invSlot(36)); // 단축 줄 첫 칸 = 초월 칼
        shot(ctx, "ui_inventory_hover");
        hover(ctx, sc -> panel(sc).statCenter(0));
        shot(ctx, "ui_panel_stat");
        hover(ctx, sc -> panel(sc).buttonCenter());
        shot(ctx, "ui_panel_button");
        click(ctx, conn, sc -> panel(sc).buttonCenter());
        boolean stats = waitScreen(ctx, sc -> sc instanceof StatScreen, "stat window from the panel button");
        check(stats, "panel button opens the stat window");
        // 다시 인벤토리 → 조합법 책을 열면 패널이 숨음
        ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        ctx.waitTicks(5);
        click(ctx, conn, sc -> {
            AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) sc;
            return new int[] {a.mmorpg$leftPos() + 104 + 10, sc.height / 2 - 22 + 9};
        });
        boolean hidden = ctx.computeOnClient(mc -> panel(mc.gui.screen()) != null && !panel(mc.gui.screen()).shown());
        check(hidden, "recipe book open → panel hidden");
        away(ctx);
        shot(ctx, "ui_inventory_book");
        // 책 닫기 (다음에 여는 인벤토리에 남지 않게)
        click(ctx, conn, sc -> {
            AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) sc;
            return new int[] {a.mmorpg$leftPos() + 104 + 10, sc.height / 2 - 22 + 9};
        });
        boolean back = ctx.computeOnClient(mc -> panel(mc.gui.screen()) != null && panel(mc.gui.screen()).shown());
        check(back, "recipe book closed → panel back");
    }

    // ------------------------------------------------------------ 2. 상자 · 단추

    private void placeAndOpen(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn, String block, BlockPos other, String otherBlock) {
        server.runCommand("setblock " + BOX.getX() + " " + BOX.getY() + " " + BOX.getZ() + " " + block);
        if (other != null) server.runCommand("setblock " + other.getX() + " " + other.getY() + " " + other.getZ() + " " + otherBlock);
        server.runOnServer(s -> player(s).getInventory().setSelectedSlot(8));
        server.runCommand("tp @a 0.5 -60 0.5 0 29");
        ctx.waitTicks(10);
        ctx.getInput().pressKey(o -> o.keyUse);
        conn.waitForServerboundPackets();
        ctx.waitTicks(3);
        conn.waitForClientboundPackets();
        ctx.waitTicks(3);
    }

    private void fill(TestServerContext server, BlockPos pos, ItemStack... items) {
        server.runOnServer(s -> {
            if (!(player(s).level().getBlockEntity(pos) instanceof BaseContainerBlockEntity be)) return;
            for (int i = 0; i < items.length && i < be.getContainerSize(); i++) if (items[i] != null) be.setItem(i * 2 % be.getContainerSize(), items[i]);
        });
    }

    private void chest(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        placeAndOpen(ctx, server, conn, "minecraft:chest[facing=north]", null, null);
        fill(server, BOX, new ItemStack(Items.DIRT, 20), weapon("sword", 20, "rare"), new ItemStack(Items.DIRT, 30), RuneItems.stack("int", "transcend"),
                new ItemStack(Items.IRON_INGOT, 9), armor("str", 40, "transcend", "chest"), new ItemStack(Items.DIAMOND, 3), weapon("bow", 40, "transcend"));
        ctx.waitTicks(4);
        if (!waitButtons(ctx, sc -> sc instanceof ContainerScreen cs && cs.getMenu().getRowCount() == 3, "chest with buttons")) return;
        int[] geo = ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) sc;
            int[] c = buttons(sc).center(0);
            return new int[] {c[0], c[1], a.mmorpg$leftPos() + a.mmorpg$imageWidth(), a.mmorpg$topPos()};
        });
        check(geo[0] > geo[2] && geo[1] > geo[3], "buttons right of the chest window " + java.util.Arrays.toString(geo));
        away(ctx);
        shot(ctx, "ui_chest");
        hover(ctx, sc -> buttons(sc).center(0));
        shot(ctx, "ui_chest_sort_tip");
        click(ctx, conn, sc -> buttons(sc).center(0));
        ctx.waitTicks(3);
        String order = server.computeOnServer(s -> {
            Container c = (Container) player(s).level().getBlockEntity(BOX);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 9; i++) b.append(ContainerTools.category(c.getItem(i))).append(c.getItem(i).isEmpty() ? "_" : "").append(' ');
            return b.toString().trim();
        });
        boolean sorted = server.computeOnServer(s -> {
            Container c = (Container) player(s).level().getBlockEntity(BOX);
            return ContainerTools.grade(c.getItem(0)) == 4 && ContainerTools.category(c.getItem(0)) == 0 && ContainerTools.category(c.getItem(1)) == 0
                    && ContainerTools.category(c.getItem(2)) == 1 && ContainerTools.category(c.getItem(3)) == 2 && c.getItem(4).getCount() == 50 && c.getItem(4).is(Items.DIRT);
        });
        check(sorted, "sort button: weapons → armor → rune → vanilla, dirt 20 + 30 merged (" + order + ")");
        away(ctx);
        shot(ctx, "ui_chest_sorted");
        click(ctx, conn, sc -> buttons(sc).center(2)); // 같은 종류 (다이아몬드 · 흙 없음 → 다이아몬드만)
        ctx.waitTicks(3);
        int[] st = server.computeOnServer(s -> new int[] {RpgEconomyTests.count(player(s), Items.DIAMOND), RpgEconomyTests.count(player(s), Items.COBBLESTONE)});
        check(st[0] == 0 && st[1] == 104, "same-kind button: diamonds in, cobblestone stays " + java.util.Arrays.toString(st));
        click(ctx, conn, sc -> buttons(sc).center(1)); // 모두 넣기
        ctx.waitTicks(3);
        int[] dep = server.computeOnServer(s -> {
            ServerPlayer sp = player(s);
            int bag = 0;
            for (int i = 9; i < 36; i++) if (!sp.getInventory().getItem(i).isEmpty()) bag++;
            return new int[] {bag, sp.getInventory().getItem(0).isEmpty() ? 0 : 1};
        });
        check(dep[0] == 0 && dep[1] == 1, "deposit-all button: bag empty, hotbar kept " + java.util.Arrays.toString(dep));
        away(ctx);
        shot(ctx, "ui_chest_deposit");
    }

    private void largeChest(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        placeAndOpen(ctx, server, conn, "minecraft:chest[facing=north,type=left]", BOX.east(), "minecraft:chest[facing=north,type=right]");
        if (!waitButtons(ctx, sc -> sc instanceof ContainerScreen cs && cs.getMenu().getRowCount() == 6, "large chest")) return;
        away(ctx);
        shot(ctx, "ui_large_chest");
        hover(ctx, chestSlot(0));
        shot(ctx, "ui_large_chest_hover");
        server.runCommand("setblock " + BOX.east().getX() + " " + BOX.east().getY() + " " + BOX.east().getZ() + " minecraft:air");
    }

    private void shulker(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        placeAndOpen(ctx, server, conn, "minecraft:purple_shulker_box[facing=up]", null, null);
        fill(server, BOX, weapon("scythe", 40, "hero"), new ItemStack(Items.ENDER_PEARL, 16), RuneItems.stack("reaper", "transcend"));
        ctx.waitTicks(4);
        if (!waitButtons(ctx, sc -> sc instanceof ShulkerBoxScreen, "shulker box")) return;
        away(ctx);
        shot(ctx, "ui_shulker");
    }

    private void storage(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runCommand("setblock " + BOX.getX() + " " + BOX.getY() + " " + BOX.getZ() + " minecraft:air");
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            mmorpg.fabric.storage.StorageData d = mmorpg.fabric.storage.StorageData.of(sp);
            d.setUnlocked(2);
            d.page(0).set(0, weapon("lance", 40, "transcend"));
            d.page(0).set(1, RuneItems.stack("holy", "hero"));
            d.page(0).set(9, new ItemStack(Items.GOLDEN_APPLE, 6));
            StorageWindow.open(sp, 0, null, null);
        });
        if (!waitScreen(ctx, sc -> sc instanceof ContainerScreen && tabs(sc) != null && tabs(sc).shown() && buttons(sc) != null && buttons(sc).shown(), "ender storage")) return;
        away(ctx);
        shot(ctx, "ui_storage");
        hover(ctx, sc -> tabs(sc).tabCenter(1));
        shot(ctx, "ui_storage_tab");
    }

    private void trash(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> TrashWindow.open(player(s)));
        if (!waitScreen(ctx, sc -> sc instanceof ContainerScreen && sc.getTitle().getString().startsWith("쓰레기통"), "trash")) return;
        ctx.waitTicks(10);
        conn.waitForClientboundPackets();
        boolean none = ctx.computeOnClient(mc -> buttons(mc.gui.screen()) != null && !buttons(mc.gui.screen()).shown());
        check(none, "trash: no sort / deposit buttons");
        away(ctx);
        shot(ctx, "ui_trash");
    }

    private void forms(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> Store.openStore(player(s), 40, "weapons"));
        if (waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && c.form().title().contains("무기 상점"), "weapon shop")) {
            away(ctx);
            shot(ctx, "ui_shop");
            hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(10));
            shot(ctx, "ui_shop_hover");
        }
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
        server.runOnServer(s -> RpgNet.statAction(player(s), "market"));
        if (waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && c.form().title().contains("장터"), "market")) {
            away(ctx);
            shot(ctx, "ui_market");
        }
    }

    // ------------------------------------------------------------ 도움

    private void closeAll(ClientGameTestContext ctx, TestServerContext server) {
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
        server.runOnServer(s -> player(s).closeContainer());
        ctx.waitTicks(3);
    }

    private void part(ClientGameTestContext ctx, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[rpgui-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[rpgui-gallery] part " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[rpgui-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.waitTick();
        ctx.takeScreenshot(name);
    }

    private static void away(ClientGameTestContext ctx) {
        ctx.getInput().setCursorPos(6, 6);
        ctx.waitTicks(2);
    }

    private static CharacterPanel panel(Screen sc) {
        if (sc == null) return null;
        for (AbstractWidget w : Screens.getWidgets(sc)) if (w instanceof CharacterPanel p) return p;
        return null;
    }

    private static ContainerButtons buttons(Screen sc) {
        if (sc == null) return null;
        for (AbstractWidget w : Screens.getWidgets(sc)) if (w instanceof ContainerButtons b) return b;
        return null;
    }

    private static StorageTabs tabs(Screen sc) {
        if (sc == null) return null;
        for (AbstractWidget w : Screens.getWidgets(sc)) if (w instanceof StorageTabs t) return t;
        return null;
    }

    /** 인벤토리 창 칸 (InventoryMenu 번호 — 36 = 단축 줄 첫 칸) 의 가운데 */
    private static Function<Screen, int[]> invSlot(int i) {
        return sc -> {
            if (!(sc instanceof InventoryScreen inv)) return null;
            AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) sc;
            Slot s = inv.getMenu().getSlot(i);
            return new int[] {a.mmorpg$leftPos() + s.x + 8, a.mmorpg$topPos() + s.y + 8};
        };
    }

    /** 상자 창 칸 i 의 가운데 */
    private static Function<Screen, int[]> chestSlot(int i) {
        return sc -> {
            if (!(sc instanceof AbstractContainerScreen<?> cs)) return null;
            AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) sc;
            Slot s = cs.getMenu().getSlot(i);
            return new int[] {a.mmorpg$leftPos() + s.x + 8, a.mmorpg$topPos() + s.y + 8};
        };
    }

    private static String screenName(Minecraft mc) {
        Screen sc = mc.gui.screen();
        return sc == null ? "none" : sc.getClass().getSimpleName() + " '" + sc.getTitle().getString() + "'";
    }

    private boolean waitScreen(ClientGameTestContext ctx, Predicate<Screen> test, String what) {
        try {
            ctx.waitFor(mc -> mc.gui.screen() != null && test.test(mc.gui.screen()), 100);
            ctx.waitTicks(2);
            return true;
        } catch (AssertionError | RuntimeException e) {
            check(false, "screen " + what + " did not open (now " + ctx.computeOnClient(RpgUiGallery::screenName) + ")");
            return false;
        }
    }

    /** 상자 창이 열리고 서버가 단추를 허락할 때까지 */
    private boolean waitButtons(ClientGameTestContext ctx, Predicate<Screen> test, String what) {
        return waitScreen(ctx, sc -> test.test(sc) && buttons(sc) != null && buttons(sc).shown(), what);
    }

    private static double[] at(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        return ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            int[] p = sc == null ? null : where.apply(sc);
            if (p == null) throw new AssertionError("no point on screen " + screenName(mc));
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(p[0] + 0.5) * k, (p[1] + 0.5) * k};
        });
    }

    private static void hover(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        double[] p = at(ctx, where);
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(3);
    }

    private static void click(ClientGameTestContext ctx, TestServerConnection conn, Function<Screen, int[]> where) {
        double[] p = at(ctx, where);
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
    }
}
