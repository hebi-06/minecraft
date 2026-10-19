package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import mmorpg.core.Economy;
import mmorpg.core.Storage;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.client.StatScreen;
import mmorpg.fabric.client.StorageTabs;
import mmorpg.fabric.client.mixin.AbstractContainerScreenAccessor;
import mmorpg.fabric.economy.EconomyItems;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.storage.StorageData;
import mmorpg.fabric.storage.StorageWindow;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.22.0 엔더 창고 사진첩 (화면 시험 — 키 storage):
 * <ol>
 * <li>엔더 상자에 아이템을 넣어 두고 스탯 창 아랫줄 창고 단추 → 큰 상자 (6줄) · 1번 상자의 같은 칸으로 옮겨짐 · 왼쪽 상자 탭 (st_open · st_tab_*)</li>
 * <li>2번 상자 탭을 두 번 누름 (첫 누름 = 노란 테 · 두 번째 = 300 실버로 삼) → 같은 창이 2번 상자로 (st_armed · st_bought)</li>
 * <li>1번 상자에서 다이아몬드를 들고 2번 탭 → 2번 상자 칸에 내려놓음 (st_carry · st_carried)</li>
 * <li>3번 (골드 30 · 보유 25 — 모자람) · 5번 (앞 상자부터) 탭 풀이, 든 칸 막대 (노랑) (st_poor · st_locked · st_full)</li>
 * <li>놓인 엔더 상자를 우클릭 → 같은 창고 · 뚜껑이 열림 → 닫으면 닫힘 (st_block)</li>
 * </ol>
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgStorageGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-storage-gallery");
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("storage")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
            ctx.waitTicks(20);
            boolean ready = part(ctx, "open", () -> open(ctx, server, conn));
            if (ready) {
                part(ctx, "buy", () -> buy(ctx, server, conn));
                part(ctx, "carry", () -> carry(ctx, server, conn));
                part(ctx, "locked", () -> locked(ctx, server, conn));
            }
            closeAll(ctx, server);
            part(ctx, "block", () -> block(ctx, server, conn));
            ctx.setScreen(() -> null);
        }
        LOG.info("[storage-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 1. 열기 · 옮겨짐 · 탭

    private boolean opened;

    private void open(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getInventory().clearContent();
            sp.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
            sp.getInventory().setItem(1, new ItemStack(Items.TORCH, 32));
            sp.getInventory().setItem(9, new ItemStack(Items.OAK_LOG, 20));
            Wallet.set(sp, Economy.Kind.SILVER, 500);
            Wallet.set(sp, Economy.Kind.GOLD, 25);
            Container ec = sp.getEnderChestInventory();
            ec.setItem(0, new ItemStack(Items.DIAMOND, 24));
            ec.setItem(1, new ItemStack(Items.IRON_INGOT, 40));
            ec.setItem(2, RuneItems.stack("int", "hero"));
            ec.setItem(9, new ItemStack(RpgItems.byId(Weapons.weaponFrom("sword", 20, "rare").id())));
            ec.setItem(10, new ItemStack(GearItems.byId("rpg:enhance_stone"), 16));
            ec.setItem(18, new ItemStack(Items.GOLDEN_APPLE, 6));
            ec.setItem(26, new ItemStack(EconomyItems.SILVER_COIN, 30));
        });
        ctx.waitTicks(4);
        openStats(ctx);
        hover(ctx, bottom(1));
        shot(ctx, "st_stats_button");
        click(ctx, conn, bottom(1));
        if (!waitTabs(ctx, "storage from the stat window")) return;
        boolean grid = ctx.computeOnClient(mc -> mc.player.containerMenu instanceof ChestMenu cm && cm.getRowCount() == 6
                && cm.getSlot(0).getItem().is(Items.DIAMOND) && cm.getSlot(18).getItem().is(Items.GOLDEN_APPLE) && cm.getSlot(26).getItem().getCount() == 30);
        String title = ctx.computeOnClient(mc -> mc.gui.screen().getTitle().getString());
        boolean emptied = server.computeOnServer(s -> player(s).getEnderChestInventory().isEmpty());
        check(grid && emptied && title.equals("엔더 창고"), "large chest storage, ender chest items in the same slots of chest 1 (title '" + title + "', emptied " + emptied + ")");
        int[] geo = ctx.computeOnClient(mc -> {
            AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) mc.gui.screen();
            int[] t = tabs(mc.gui.screen()).tabCenter(4);
            return new int[] {a.mmorpg$leftPos(), a.mmorpg$topPos(), a.mmorpg$imageHeight(), t[0], t[1], mc.gui.screen().width};
        });
        check(geo[3] < geo[0] && geo[3] > 0 && geo[4] < geo[1] + geo[2], "tabs left of the window, inside the screen " + java.util.Arrays.toString(geo));
        away(ctx);
        shot(ctx, "st_open");
        hover(ctx, tab(0));
        shot(ctx, "st_tab_current");
        hover(ctx, tab(1));
        shot(ctx, "st_tab_buy");
        opened = true;
    }

    // ------------------------------------------------------------ 2. 두 번 눌러 사기

    private void buy(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        if (!opened) return;
        Screen before = ctx.computeOnClient(mc -> mc.gui.screen());
        click(ctx, conn, tab(1));
        int armed = ctx.computeOnClient(mc -> StorageTabs.armedTab());
        int still = server.computeOnServer(s -> StorageData.of(player(s)).unlocked());
        check(armed == 1 && still == 1, "first click arms chest 2 (armed " + armed + ", unlocked " + still + ")");
        hover(ctx, tab(1));
        shot(ctx, "st_armed");
        click(ctx, conn, tab(1));
        ctx.waitTicks(4);
        long[] r = server.computeOnServer(s -> {
            ServerPlayer sp = player(s);
            StorageWindow.StorageMenu m = StorageWindow.menu(sp);
            return new long[] {StorageData.of(sp).unlocked(), Wallet.get(sp, Economy.Kind.SILVER), m == null ? -1 : m.page()};
        });
        boolean same = ctx.computeOnClient(mc -> mc.gui.screen() == before);
        check(r[0] == 2 && r[1] == 200 && r[2] == 1 && same, "second click buys chest 2 for 300 silver, same window on chest 2 " + java.util.Arrays.toString(r) + " same " + same);
        away(ctx);
        shot(ctx, "st_bought");
    }

    // ------------------------------------------------------------ 3. 든 아이템을 다른 상자로

    private void carry(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        if (!opened) return;
        click(ctx, conn, tab(0));
        int page = server.computeOnServer(s -> StorageWindow.menu(player(s)) == null ? -1 : StorageWindow.menu(player(s)).page());
        check(page == 0, "tab 1 → chest 1 (" + page + ")");
        click(ctx, conn, slot(0));
        boolean carrying = ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().is(Items.DIAMOND));
        check(carrying, "picked up the diamonds");
        hover(ctx, tab(1));
        shot(ctx, "st_carry");
        click(ctx, conn, tab(1));
        boolean stillCarrying = ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().is(Items.DIAMOND));
        click(ctx, conn, slot(4));
        int[] r = server.computeOnServer(s -> {
            StorageData d = StorageData.of(player(s));
            return new int[] {d.page(1).get(4).is(Items.DIAMOND) ? d.page(1).get(4).getCount() : 0, d.page(0).get(0).isEmpty() ? 1 : 0,
                    player(s).containerMenu.getCarried().isEmpty() ? 1 : 0};
        });
        check(stillCarrying && r[0] == 24 && r[1] == 1 && r[2] == 1, "diamonds carried from chest 1 to chest 2 slot 4 " + java.util.Arrays.toString(r));
        away(ctx);
        shot(ctx, "st_carried");
    }

    // ------------------------------------------------------------ 4. 잠긴 상자 · 든 칸 막대

    private void locked(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        if (!opened) return;
        hover(ctx, tab(2));
        shot(ctx, "st_poor");
        hover(ctx, tab(4));
        shot(ctx, "st_locked");
        click(ctx, conn, tab(4));
        click(ctx, conn, tab(4));
        int u = server.computeOnServer(s -> StorageData.of(player(s)).unlocked());
        check(u == 2, "chest 5 can't be bought before 3 (" + u + ")");
        // 1번 상자를 45칸 채움 → 막대 노랑 (3/4 넘음)
        server.runOnServer(s -> {
            StorageData d = StorageData.of(player(s));
            for (int i = 0; i < 45; i++) if (d.page(0).get(i).isEmpty()) d.page(0).set(i, new ItemStack(i % 2 == 0 ? Items.COBBLESTONE : Items.DIRT, 64));
        });
        click(ctx, conn, tab(0));
        ctx.waitTicks(2);
        int n = server.computeOnServer(s -> StorageData.of(player(s)).stacks(0));
        check(n >= 45, "chest 1 holds " + n + " stacks");
        hover(ctx, tab(0));
        shot(ctx, "st_full");
    }

    // ------------------------------------------------------------ 5. 놓인 엔더 상자

    private void block(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        BlockPos pos = new BlockPos(0, -60, 2);
        server.runOnServer(s -> {
            player(s).level().setBlockAndUpdate(pos, Blocks.ENDER_CHEST.defaultBlockState());
            player(s).getInventory().setSelectedSlot(8); // 빈 손
        });
        server.runCommand("tp @a 0.5 -60 0.5 0 29");
        ctx.waitTicks(10);
        ctx.getInput().pressKey(o -> o.keyUse);
        conn.waitForServerboundPackets();
        ctx.waitTicks(3);
        conn.waitForClientboundPackets();
        if (!waitTabs(ctx, "storage from the ender chest block")) return;
        ctx.waitTicks(10);
        boolean fromBlock = server.computeOnServer(s -> StorageWindow.menu(player(s)) != null && StorageWindow.menu(player(s)).fromBlock());
        int openers = server.computeOnServer(s -> StorageWindow.lidOpeners(player(s).level(), pos));
        float lid = ctx.computeOnClient(mc -> mc.level.getBlockEntity(pos) instanceof EnderChestBlockEntity be ? be.getOpenNess(1F) : -1F);
        check(fromBlock && openers == 1 && lid > 0.5F, "ender chest block → storage, lid open (openers " + openers + ", openness " + lid + ")");
        away(ctx);
        shot(ctx, "st_block");
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(20);
        int after = server.computeOnServer(s -> StorageWindow.lidOpeners(player(s).level(), pos));
        float lid2 = ctx.computeOnClient(mc -> mc.level.getBlockEntity(pos) instanceof EnderChestBlockEntity be ? be.getOpenNess(1F) : -1F);
        check(after == 0 && lid2 < 0.5F, "closed → lid closes (openers " + after + ", openness " + lid2 + ")");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        ctx.waitTicks(3);
        shot(ctx, "st_block_closed");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    // ------------------------------------------------------------ 도움

    private void closeAll(ClientGameTestContext ctx, TestServerContext server) {
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
        server.runOnServer(s -> player(s).closeContainer());
        ctx.waitTicks(3);
    }

    private boolean part(ClientGameTestContext ctx, String name, Runnable run) {
        boolean ok = true;
        try {
            run.run();
            LOG.info("[storage-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            ok = false;
            check(false, "part " + name + " threw " + e);
            LOG.warn("[storage-gallery] part " + name, e);
        }
        return ok && opened;
    }

    private void check(boolean ok, String what) {
        LOG.info("[storage-gallery] {} {}", ok ? "OK  " : "FAIL", what);
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

    private void openStats(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(MmorpgClient.statsKey);
        if (!waitScreen(ctx, sc -> sc instanceof StatScreen, "stat window (K)")) ctx.setScreen(StatScreen::new);
        ctx.waitTicks(3);
    }

    private static Function<Screen, int[]> bottom(int i) {
        return sc -> ((StatScreen) sc).bottomCenter(i);
    }

    private static StorageTabs tabs(Screen sc) {
        if (sc == null) return null;
        for (AbstractWidget w : Screens.getWidgets(sc)) if (w instanceof StorageTabs t) return t;
        return null;
    }

    private static Function<Screen, int[]> tab(int i) {
        return sc -> tabs(sc) == null ? null : tabs(sc).tabCenter(i);
    }

    /** 창고 칸 i 의 가운데 */
    private static Function<Screen, int[]> slot(int i) {
        return sc -> {
            if (!(sc instanceof ContainerScreen cs)) return null;
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
            check(false, "screen " + what + " did not open (now " + ctx.computeOnClient(RpgStorageGallery::screenName) + ")");
            return false;
        }
    }

    /** 큰 상자 창 + 서버 상태가 와서 상자 탭이 보일 때까지 */
    private boolean waitTabs(ClientGameTestContext ctx, String what) {
        return waitScreen(ctx, sc -> sc instanceof ContainerScreen && tabs(sc) != null && tabs(sc).shown(), what);
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
